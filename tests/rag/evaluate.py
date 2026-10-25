#!/usr/bin/env python3
"""Real local-model smoke evaluation; stdlib only. Never resets a schema or deletes fixtures."""
import argparse
import hashlib
import http.cookiejar
import json
import math
import os
from pathlib import Path
import shutil
import statistics
import subprocess
import sys
import time
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, unquote, urlparse
from urllib.request import HTTPCookieProcessor, Request, build_opener
from uuid import uuid4


class EvaluationError(RuntimeError):
    pass


def timestamp():
    return datetime.now(timezone.utc).isoformat()


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    temporary.replace(path)


def local_url(value):
    parsed = urlparse(value)
    if parsed.scheme != 'http' or parsed.hostname not in {'127.0.0.1', 'localhost', '::1'} or parsed.username or parsed.password:
        raise EvaluationError('Evaluation requires explicit HTTP loopback app and Ollama URLs')
    if parsed.path not in {'', '/'} or parsed.query or parsed.fragment:
        raise EvaluationError('Use a base URL without a path, query or fragment')
    return value.rstrip('/')


class JsonClient:
    def __init__(self, base, timeout):
        self.base = local_url(base)
        self.timeout = timeout
        self.cookies = http.cookiejar.CookieJar()
        self.opener = build_opener(HTTPCookieProcessor(self.cookies))

    def call(self, path, body=None, csrf=False):
        headers = {'Accept': 'application/json'}
        payload = None
        if body is not None:
            payload = json.dumps(body, ensure_ascii=False).encode('utf-8')
            headers['Content-Type'] = 'application/json'
        if csrf:
            token = next((cookie.value for cookie in self.cookies if cookie.name == 'XSRF-TOKEN'), None)
            if not token:
                raise EvaluationError('Server did not issue CSRF cookie')
            headers['X-XSRF-TOKEN'] = unquote(token)
        request = Request(self.base + path, data=payload, headers=headers)
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                raw = response.read()
                if 'application/json' in response.headers.get('Content-Type', ''):
                    return response.status, json.loads(raw)
                return response.status, None
        except HTTPError as error:
            # Do not include arbitrary response messages or credentials in exception logs.
            try:
                data = json.loads(error.read())
            except (ValueError, UnicodeError):
                data = {}
            return error.code, data
        except (URLError, TimeoutError) as error:
            raise EvaluationError('Local HTTP call failed: ' + type(error).__name__) from None

    def require(self, path, body=None, csrf=False):
        status, data = self.call(path, body, csrf)
        if status >= 400:
            code = data.get('code', '') if isinstance(data, dict) else ''
            raise EvaluationError(f'{path} returned HTTP {status} {code}')
        return data


class ReadOnlyPostgres:
    def __init__(self):
        self.executable = os.environ.get('RAG_EVAL_PSQL') or shutil.which('psql')
        if not self.executable:
            raise EvaluationError('Set RAG_EVAL_PSQL to the psql executable')
        self.env = os.environ.copy()
        jdbc = os.environ.get('RAG_TEST_JDBC_URL')
        if jdbc:
            parsed = urlparse(jdbc.removeprefix('jdbc:'))
            if parsed.scheme not in {'postgresql', 'postgres'} or not parsed.hostname or not parsed.path.strip('/'):
                raise EvaluationError('Invalid RAG_TEST_JDBC_URL')
            self.env.update(PGHOST=parsed.hostname, PGPORT=str(parsed.port or 5432), PGDATABASE=unquote(parsed.path.lstrip('/')))
            query = parse_qs(parsed.query)
            user = os.environ.get('RAG_TEST_DB_USER') or os.environ.get('RAG_TEST_DB_USERNAME') or (query.get('user') or [parsed.username])[0]
            password = os.environ.get('RAG_TEST_DB_PASSWORD') or (query.get('password') or [parsed.password])[0]
            if user:
                self.env['PGUSER'] = unquote(user)
            if password:
                self.env['PGPASSWORD'] = unquote(password)
        if not self.env.get('PGDATABASE'):
            raise EvaluationError('Set RAG_TEST_JDBC_URL or explicit PGHOST/PGPORT/PGDATABASE/PGUSER')
        # Enforce read-only at the PostgreSQL session level for every statement.
        self.env['PGOPTIONS'] = '-c default_transaction_read_only=on -c statement_timeout=30000'

    def rows(self, sql):
        wrapped = "SELECT COALESCE(json_agg(evaluation_rows), '[]'::json) FROM (" + sql + ') evaluation_rows;'
        result = subprocess.run([self.executable, '-X', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1'],
                                input=wrapped, text=True, capture_output=True, env=self.env, timeout=40)
        if result.returncode:
            raise EvaluationError('Read-only PostgreSQL query failed; check database connection and RAG migration')
        try:
            return json.loads(result.stdout.strip())
        except ValueError:
            raise EvaluationError('Unexpected psql JSON output') from None


def quote(value):
    return "'" + str(value).replace("'", "''") + "'"


def ids_sql(documents):
    return ','.join(str(int(document['id'])) for document in documents.values())


def eligible_sql(document_ids, model, dimensions):
    return f"""
        SELECT c.document_id, c.version_sha, c.chunk_index, c.embedding
        FROM rag_chunks c
        JOIN documents d ON d.id = c.document_id
        JOIN spaces s ON s.id = d.space_id
        JOIN LATERAL (SELECT v.git_hash FROM versions v WHERE v.document_id = d.id
                      ORDER BY v.created_at DESC, v.id DESC LIMIT 1) latest ON latest.git_hash = c.version_sha
        WHERE d.status = 'Published' AND s.is_deleted = false
          AND d.updated_at = c.source_updated_at
          AND c.document_id IN ({document_ids})
          AND c.embedding_model = {quote(model)} AND c.embedding_dimensions = {int(dimensions)}
    """


def validate_dataset(dataset):
    documents = dataset['documents']
    questions = dataset['questions']
    keys = {document['key'] for document in documents}
    if len(keys) != len(documents) or len(questions) < 30:
        raise EvaluationError('Dataset needs unique documents and at least 30 questions')
    if len({question['id'] for question in questions}) != len(questions):
        raise EvaluationError('Question IDs must be unique')
    spaces = {space['key'] for space in dataset['spaces']}
    for document in documents:
        if document['space'] not in spaces or not document['content'].strip():
            raise EvaluationError('Invalid dataset document')
    for question in questions:
        if question['answerable'] and question['expectedDocument'] not in keys:
            raise EvaluationError('Answerable question needs a known expected document')
        if not question['question'].strip() or len(question['question']) > 2000:
            raise EvaluationError('Question has invalid length')


def seed(app, dataset, dataset_hash, state_path, resume):
    if state_path.exists():
        if not resume:
            raise EvaluationError('State file exists: use --resume to reuse its own fixtures, or choose another --state')
        state = json.loads(state_path.read_text(encoding='utf-8'))
        if state['datasetSha256'] != dataset_hash or state['baseUrl'] != app.base:
            raise EvaluationError('State file belongs to another dataset or app URL')
    else:
        if resume:
            raise EvaluationError('--resume requires an existing state file')
        state = {'datasetSha256': dataset_hash, 'baseUrl': app.base,
                 'prefix': 'RAG-EVAL-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid4().hex[:8],
                 'createdAt': timestamp(), 'spaces': {}, 'documents': {}}
        write_json(state_path, state)
    for space in dataset['spaces']:
        if space['key'] not in state['spaces']:
            created = app.require('/api/admin/spaces', {'name': state['prefix'] + ' ' + space['name'],
                                                        'description': 'Synthetic RAG evaluation fixture'}, csrf=True)
            state['spaces'][space['key']] = created['id']
            write_json(state_path, state)
    for document in dataset['documents']:
        key = document['key']
        if key not in state['documents']:
            created = app.require('/api/documents', {'title': document['title'], 'content': document['content'],
                                  'spaceId': state['spaces'][document['space']]}, csrf=True)
            # Persist the ID before publication so interruption can resume without duplicate documents.
            state['documents'][key] = {'id': created['id'], 'title': document['title'],
                                       'spaceId': state['spaces'][document['space']]}
            write_json(state_path, state)
        actual = app.require('/api/documents/' + str(state['documents'][key]['id']))
        if actual['title'] != document['title'] or actual['content'] != document['content']:
            raise EvaluationError('An evaluation fixture was modified; use a fresh state file')
        if actual['status'].lower() == 'draft':
            app.require('/api/documents/' + str(actual['id']) + '/publish', {}, csrf=True)
        elif actual['status'].lower() != 'published':
            raise EvaluationError('An evaluation fixture is no longer published; use a fresh state file')
    return state


def wait_for_index(database, state, model, dimensions, timeout):
    deadline = time.monotonic() + timeout
    document_ids = ids_sql(state['documents'])
    while True:
        rows = database.rows('WITH eligible AS MATERIALIZED (' + eligible_sql(document_ids, model, dimensions) +
                             ') SELECT document_id, version_sha, chunk_index FROM eligible ORDER BY document_id, chunk_index')
        indexed = {row['document_id'] for row in rows}
        if len(indexed) == len(state['documents']):
            return rows
        failures = database.rows(f"SELECT document_id, state, attempts, last_error FROM rag_index_jobs "
                                 f"WHERE document_id IN ({document_ids}) AND embedding_model = {quote(model)} "
                                 f"AND embedding_dimensions = {int(dimensions)} AND state = 'FAILED'")
        if failures:
            raise EvaluationError('Indexing failed for evaluation document IDs ' + ','.join(str(row['document_id']) for row in failures))
        if time.monotonic() >= deadline:
            raise EvaluationError(f'Index readiness timeout: {len(indexed)}/{len(state["documents"])} documents')
        time.sleep(2)


def percentile(values, fraction):
    if not values:
        return None
    return sorted(values)[max(0, math.ceil(len(values) * fraction) - 1)]


def evaluation_gates(dataset_gates):
    # Useful answers are a separate gate: perfect retrieval with universal abstention must fail.
    return {**dataset_gates, 'answerableGroundedResponseMinimum': .9,
            'answerableExpectedSourceCitedMinimum': .9}


def summarize(cases, gates):
    gates = evaluation_gates(gates)
    answerable = [case for case in cases if case['answerable']]
    unanswerable = [case for case in cases if not case['answerable']]
    recall = sum(case.get('expectedInTop5', False) for case in answerable) / len(answerable) if answerable else 0
    grounded_count = sum(case.get('insufficientEvidence') is False and case.get('sourceCount', 0) > 0 for case in answerable)
    expected_cited_count = sum(case.get('expectedSourceCited', False) for case in answerable)
    grounded_rate = grounded_count / len(answerable) if answerable else 0
    expected_cited_rate = expected_cited_count / len(answerable) if answerable else 0
    unsupported = sum(case.get('insufficientEvidence') is not True for case in unanswerable)
    latencies = [case['answerLatencyMs'] for case in cases if 'answerLatencyMs' in case]
    checks = {
        'answerableRecallAt5': recall >= gates['answerableRecallAt5Minimum'],
        'answerableGroundedResponse': grounded_rate >= gates['answerableGroundedResponseMinimum'],
        'answerableExpectedSourceCited': expected_cited_rate >= gates['answerableExpectedSourceCitedMinimum'],
        'allHttpRequestsSuccessful': all(case.get('httpStatus') == 200 for case in cases),
        'citationProvenanceValid': all(not case.get('citationProblems') for case in cases),
        'noUnexpectedDocumentIds': all(not case.get('unexpectedDocumentIds') for case in cases),
        'unanswerableAbstention': unsupported <= gates['unsupportedUnanswerableMaximum'],
        'noInjectionMarker': all(not case.get('injectionMarkerFound') for case in cases),
    }
    return {'questionCount': len(cases), 'answerableCount': len(answerable), 'unanswerableCount': len(unanswerable),
            'answerableRecallAt5': recall,
            'answerableGroundedResponseCount': grounded_count,
            'answerableGroundedResponseRate': grounded_rate,
            'answerableExpectedSourceCitedCount': expected_cited_count,
            'answerableExpectedSourceCitedRate': expected_cited_rate,
            'answerableKeywordGroupsPassedCount': sum(case.get('keywordGroupsPassed', False) for case in answerable),
            'unsupportedUnanswerableCount': unsupported,
            'invalidCitationCount': sum(len(case.get('citationProblems', [])) for case in cases),
            'unexpectedDocumentIdCount': sum(len(case.get('unexpectedDocumentIds', [])) for case in cases),
            'injectionMarkerResponseCount': sum(case.get('injectionMarkerFound', False) for case in cases),
            'answerLatencyMs': {'p50': statistics.median(latencies) if latencies else None,
                                'p95': percentile(latencies, .95), 'max': max(latencies) if latencies else None},
            'thresholds': gates, 'checks': checks, 'passed': all(checks.values())}


def run(args):
    data_bytes = args.dataset.read_bytes()
    dataset = json.loads(data_bytes)
    validate_dataset(dataset)
    dataset_hash = hashlib.sha256(data_bytes).hexdigest()
    if args.validate_only:
        print(json.dumps({'datasetSha256': dataset_hash, 'documents': len(dataset['documents']),
                          'questions': len(dataset['questions']), 'valid': True}))
        return 0
    if not args.report:
        raise EvaluationError('--report is required for a real evaluation')
    if args.report.exists():
        raise EvaluationError('Report already exists; choose a new path to preserve earlier evidence')
    if not math.isfinite(args.minimum_score) or not -1 <= args.minimum_score <= 1 or not 1 <= args.dimensions <= 16000:
        raise EvaluationError('Invalid retrieval parameters')
    state_path = args.state or args.report.with_suffix('.state.json')
    report = {'schemaVersion': 1, 'startedAt': timestamp(), 'datasetSha256': dataset_hash,
              'evaluationCriteriaVersion': 2, 'gates': evaluation_gates(dataset['gates']),
              'scope': 'Synthetic local admin evaluation only; not an ACL suite or production quality estimate',
              'cases': [], 'status': 'running', 'passed': False,
              'retrievalSettings': {'topK': 5, 'minimumScore': args.minimum_score, 'dimensions': args.dimensions},
              'cost': {'externalApiCurrency': 'USD', 'externalApiSpend': 0,
                       'localHardwareAndEnergyCost': None, 'note': 'Only loopback Ollama; hardware/energy not measured'}}
    write_json(args.report, report)
    try:
        app = JsonClient(args.base_url, args.http_timeout)
        ollama = JsonClient(args.ollama_url, args.http_timeout)
        database = ReadOnlyPostgres()
        report['ollamaVersion'] = ollama.require('/api/version')
        installed = ollama.require('/api/tags')['models']
        report['models'] = {}
        for purpose, name in [('embedding', args.embedding_model), ('generation', args.generation_model)]:
            model = next((model for model in installed if name in (model.get('name'), model.get('model'))), None)
            if not model:
                raise EvaluationError('Required model is not installed: ' + name)
            report['models'][purpose] = {'name': name, 'digest': model.get('digest'), 'sizeBytes': model.get('size'), 'details': model.get('details')}
        report['postgres'] = database.rows("SELECT current_setting('server_version') AS version, extversion AS vector_version FROM pg_extension WHERE extname = 'vector'")
        if not report['postgres']:
            raise EvaluationError('pgvector extension is absent')
        app.require('/login')
        app.require('/api/auth/login', {'login': os.environ.get('RAG_EVAL_LOGIN', 'admin'),
                                      'password': os.environ.get('RAG_EVAL_PASSWORD', 'admin123')}, csrf=True)
        if app.require('/api/ai/rag/status').get('enabled') is not True:
            raise EvaluationError('RAG is unavailable; enable the local test deployment with its explicit RAG migration')
        state = seed(app, dataset, dataset_hash, state_path, args.resume)
        report['fixtures'] = state
        write_json(args.report, report)
        print('Waiting for real index: ' + state['prefix'], flush=True)
        index_started = time.perf_counter()
        chunks = wait_for_index(database, state, args.embedding_model, args.dimensions, args.index_timeout)
        report['indexReadinessWaitMs'] = round((time.perf_counter() - index_started) * 1000, 2)
        document_ids = ids_sql(state['documents'])
        known_ids = {document['id'] for document in state['documents'].values()}
        known_chunks = {(row['document_id'], row['version_sha'], row['chunk_index']) for row in chunks}
        title_by_id = {document['id']: document['title'] for document in state['documents'].values()}
        report['indexedChunks'] = chunks
        previous_start = None
        for question in dataset['questions']:
            case = {'id': question['id'], 'question': question['question'], 'answerable': question['answerable']}
            expected_id = state['documents'][question['expectedDocument']]['id'] if question['answerable'] else None
            start = time.perf_counter()
            embedding = ollama.require('/api/embed', {'model': args.embedding_model, 'input': [question['question']], 'truncate': False})
            vector = embedding['embeddings'][0]
            if len(vector) != args.dimensions or any(not isinstance(value, (int, float)) or not math.isfinite(value) for value in vector):
                raise EvaluationError('Embedding dimension or numeric validation failed')
            case['embeddingLatencyMs'] = round((time.perf_counter() - start) * 1000, 2)
            vector_literal = json.dumps(vector, separators=(',', ':'))
            candidates = database.rows(f"""
                WITH eligible AS MATERIALIZED ({eligible_sql(document_ids, args.embedding_model, args.dimensions)}),
                scored AS (SELECT document_id, version_sha, chunk_index,
                            1 - (embedding <=> {quote(vector_literal)}::vector) AS score FROM eligible)
                SELECT document_id, version_sha, chunk_index, score FROM scored
                WHERE score >= {args.minimum_score} ORDER BY score DESC, document_id, chunk_index LIMIT 5
            """)
            case['retrievedTop5'] = candidates
            case['expectedDocumentId'] = expected_id
            case['expectedInTop5'] = expected_id in {row['document_id'] for row in candidates} if expected_id else None
            if previous_start is not None:
                time.sleep(max(0, args.minimum_interval_seconds - (time.monotonic() - previous_start)))
            previous_start = time.monotonic()
            start = time.perf_counter()
            status, answer = app.call('/api/ai/ask', {'question': question['question'], 'spaceIds': list(state['spaces'].values())}, csrf=True)
            case['answerLatencyMs'] = round((time.perf_counter() - start) * 1000, 2)
            case['httpStatus'] = status
            case['response'] = answer
            case['citationProblems'] = []
            if status == 200:
                sources = answer.get('sources', [])
                source_ids = {source.get('documentId') for source in sources}
                case['sourceCount'] = len(sources)
                case['insufficientEvidence'] = answer.get('insufficientEvidence')
                case['unexpectedDocumentIds'] = sorted(source_ids - known_ids)
                case['expectedSourceCited'] = expected_id in source_ids if expected_id else None
                text = answer.get('answer', '')
                case['injectionMarkerFound'] = dataset['gates']['injectionMarker'].casefold() in text.casefold()
                case['keywordGroupsPassed'] = all(any(keyword.casefold() in text.casefold() for keyword in group.split('|'))
                                                 for group in question['expectedAllKeywordGroups']) if question['answerable'] else None
                if answer.get('insufficientEvidence') is True and sources:
                    case['citationProblems'].append('Insufficient-evidence response includes citations')
                if answer.get('insufficientEvidence') is False and (not sources or not text.strip()):
                    case['citationProblems'].append('Substantive response has no answer or citations')
                for source in sources:
                    identity = (source.get('documentId'), source.get('versionSha'), source.get('chunkIndex'))
                    if identity not in known_chunks:
                        case['citationProblems'].append('Unknown document/version/chunk identity')
                    if source.get('url') != '/documents/' + str(source.get('documentId')):
                        case['citationProblems'].append('Unexpected citation URL')
                    if source.get('title') != title_by_id.get(source.get('documentId')):
                        case['citationProblems'].append('Citation title mismatch')
                    if source.get('documentId') not in {row['document_id'] for row in candidates}:
                        case['citationProblems'].append('Citation not present in replicated retrieval candidates')
            report['cases'].append(case)
            report['summary'] = summarize(report['cases'], dataset['gates'])
            write_json(args.report, report)
            print(f"{len(report['cases']):02}/{len(dataset['questions'])} {question['id']} HTTP {status} "
                  f"recall={case.get('expectedInTop5')} abstained={case.get('insufficientEvidence')} "
                  f"latency={case['answerLatencyMs']}ms", flush=True)
        report['status'] = 'completed'
        report['completedAt'] = timestamp()
        report['summary'] = summarize(report['cases'], dataset['gates'])
        report['passed'] = report['summary']['passed']
        write_json(args.report, report)
        print(json.dumps(report['summary'], ensure_ascii=False), flush=True)
        return 0 if report['passed'] else 1
    except (EvaluationError, KeyError, ValueError, OSError, subprocess.SubprocessError) as failure:
        report['status'] = 'failed'
        report['completedAt'] = timestamp()
        report['errorType'] = type(failure).__name__
        report['error'] = str(failure) if isinstance(failure, EvaluationError) else 'Unexpected local evaluation failure; inspect the runner and test configuration'
        write_json(args.report, report)
        raise EvaluationError(report['error']) from None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dataset', type=Path, default=Path(__file__).with_name('evaluation.json'))
    parser.add_argument('--report', type=Path, help='New JSON report path; never overwrites previous evidence')
    parser.add_argument('--state', type=Path, help='Fixture ID checkpoint; default is report path with .state.json suffix')
    parser.add_argument('--resume', action='store_true', help='Reuse only fixture IDs from an existing compatible state file; rerun every question')
    parser.add_argument('--validate-only', action='store_true', help='Check the dataset without network, DB, models or mutation')
    parser.add_argument('--base-url', default=os.environ.get('E2E_BASE_URL', 'http://127.0.0.1:18080'))
    parser.add_argument('--ollama-url', default=os.environ.get('OLLAMA_BASE_URL', 'http://127.0.0.1:11434'))
    parser.add_argument('--embedding-model', default='qwen3-embedding:0.6b')
    parser.add_argument('--generation-model', default='qwen3:4b')
    parser.add_argument('--dimensions', type=int, default=1024)
    parser.add_argument('--minimum-score', type=float, default=.3, help='Must match app.rag.minimum-score')
    parser.add_argument('--minimum-interval-seconds', type=float, default=11, help='Between ask starts; default respects six requests/minute')
    parser.add_argument('--http-timeout', type=int, default=180)
    parser.add_argument('--index-timeout', type=int, default=600)
    args = parser.parse_args()
    try:
        return run(args)
    except EvaluationError as failure:
        print('Evaluation failed: ' + str(failure), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
