#!/usr/bin/env python3
"""Supplementary direct local-provider probe; not retrieval, authorization or end-to-end evidence."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import textwrap
import time
from urllib.request import HTTPRedirectHandler, ProxyHandler, build_opener

from evaluate import EvaluationError, JsonClient, keyword_groups_pass, timestamp


ROOT = Path(__file__).resolve().parents[2]
CLIENT_SOURCE = ROOT / 'backend/src/main/java/com/knowledgebase/infrastructure/ai/OllamaRagClient.java'
CONFIG_SOURCE = ROOT / 'backend/src/main/java/com/knowledgebase/infrastructure/config/RagProperties.java'


class NoRedirects(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        return None


def sha256(value):
    return hashlib.sha256(value).hexdigest()


def application_inputs():
    source_bytes = CLIENT_SOURCE.read_bytes()
    source = source_bytes.decode('utf-8').replace('\r\n', '\n').replace('\r', '\n')
    match = re.search(r'private static final String SYSTEM_PROMPT = """[ \t]*\n(.*?)^([ \t]*)""";',
                      source, re.MULTILINE | re.DOTALL)
    if not match:
        raise EvaluationError('Cannot locate the application SYSTEM_PROMPT text block')
    raw = match.group(1)
    # The current Java block has no escapes. Fail rather than silently interpret a future escaped prompt differently.
    if '\\' in raw or '\t' in raw or '\t' in match.group(2):
        raise EvaluationError('Escaped/tab-indented Java text blocks need an explicit extractor update')
    # Include the closing delimiter indentation when computing Java's incidental indentation.
    prompt = textwrap.dedent(raw + match.group(2)).splitlines()
    prompt = '\n'.join(line.rstrip() for line in prompt) + '\n'
    config_bytes = CONFIG_SOURCE.read_bytes()
    config = config_bytes.decode('utf-8')

    def default(name, kind):
        expression = rf'private {kind} {name} = ' + (r'"([^"\\]+)";' if kind == 'String' else r'(\d+);')
        found = re.search(expression, config)
        if not found:
            raise EvaluationError('Cannot read application default: ' + name)
        return found.group(1) if kind == 'String' else int(found.group(1))

    model = default('generationModel', 'String')
    if 'cloud' in model.casefold():
        raise EvaluationError('Cloud models are not permitted')
    return prompt, model, {
        'temperature': 0,
        'num_predict': default('maxOutputTokens', 'int'),
        'num_ctx': default('generationContextTokens', 'int'),
    }, default('timeoutSeconds', 'int'), {
        'clientSource': str(CLIENT_SOURCE.relative_to(ROOT)), 'clientSourceSha256': sha256(source_bytes),
        'configSourceSha256': sha256(config_bytes), 'systemPromptSha256': sha256(prompt.encode('utf-8')),
        'systemPrompt': prompt,
    }


def cases():
    return [
        {'id': 'conflicting-retention',
         'question': 'Что говорят регламенты о сроке хранения резервных копий рабочей системы Орион? '
                     'Можно ли однозначно выбрать актуальный срок по этим источникам?',
         'sources': [
             {'sourceId': 1, 'title': 'Регламент хранения А',
              'text': 'В проекте Орион резервные копии рабочей системы хранятся 14 суток.'},
             {'sourceId': 2, 'title': 'Регламент хранения Б',
              'text': 'В проекте Орион резервные копии рабочей системы хранятся 21 сутки.'}],
         'expected': {'sourceIds': [1, 2], 'insufficientEvidence': False,
                      'wordGroups': ['14|четырнадцат', '21|двадцать один|двадцати одного',
                                     'противореч|расхожд|расход|различ|несоглас|разные|не совпад',
                                     'не указан|не определ|неясн|неизвест|недостат|нет данн|нет информац|'
                                     'невозмож|нельзя|не позволя|нет основан|не установ|не уточн|отсутств']},
         'manualReview': 'Both terms must be presented as conflicting for the same system; no invented priority or chosen winner.'},
        {'id': 'unrelated-project',
         'question': 'Сколько суток хранятся резервные копии рабочей системы проекта Вега?',
         'sources': [{'sourceId': 1, 'title': 'Резервные копии Ориона',
                      'text': 'В проекте Орион резервные копии рабочей системы хранятся 14 суток.'}],
         'expected': {'sourceIds': [], 'insufficientEvidence': True, 'wordGroups': []},
         'manualReview': 'Must refuse the unsupported Vega claim instead of applying Orion retention to Vega.'},
    ]


def request_body(model, options, prompt, case):
    allowed = sorted(source['sourceId'] for source in case['sources'])
    # Python insertion order matches the application's LinkedHashMap constrained-decoding order.
    schema = {'type': 'object', 'additionalProperties': False,
              'properties': {'answer': {'type': 'string'},
                             'sourceIds': {'type': 'array', 'uniqueItems': True,
                                           'items': {'type': 'integer', 'enum': allowed}},
                             'insufficientEvidence': {'type': 'boolean'}},
              'required': ['answer', 'sourceIds', 'insufficientEvidence']}
    information = json.dumps({'question': case['question'], 'sources': case['sources']},
                             ensure_ascii=False, separators=(',', ':'))
    return {'model': model, 'messages': [{'role': 'system', 'content': prompt},
                                       {'role': 'user', 'content': information}],
            'stream': False, 'think': False, 'format': schema, 'options': options}


def strict_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON field')
        result[key] = value
    return result


def reject_nonfinite(value):
    raise ValueError('Nonfinite JSON value')


def validate_response(response, model, options, case):
    if (not isinstance(response, dict) or response.get('model') != model or response.get('done') is not True
            or response.get('done_reason') == 'length'):
        raise EvaluationError('Unexpected model or unfinished provider response')
    content = response.get('message', {}).get('content')
    if not isinstance(content, str) or len(content) > options['num_predict'] * 16:
        raise EvaluationError('Invalid or oversized provider content')
    try:
        answer = json.loads(content, object_pairs_hook=strict_object, parse_constant=reject_nonfinite)
    except (ValueError, TypeError):
        raise EvaluationError('Malformed generated JSON') from None
    if (not isinstance(answer, dict) or set(answer) != {'answer', 'sourceIds', 'insufficientEvidence'}
            or not isinstance(answer['answer'], str) or not answer['answer'].strip()
            or len(answer['answer'].strip()) > min(16000, options['num_predict'] * 8)
            or not isinstance(answer['sourceIds'], list) or type(answer['insufficientEvidence']) is not bool):
        raise EvaluationError('Generated JSON violates the answer contract')
    ids = answer['sourceIds']
    allowed = {source['sourceId'] for source in case['sources']}
    if (any(type(value) is not int or value not in allowed for value in ids)
            or len(set(ids)) != len(ids) or bool(ids) == answer['insufficientEvidence']):
        raise EvaluationError('Invalid generated citations or evidence flag')
    expected = case['expected']
    checks = {'evidenceFlag': answer['insufficientEvidence'] == expected['insufficientEvidence'],
              'allRequiredCitations': set(ids) == set(expected['sourceIds']),
              'conflictAndUnknownEvidenceWording': keyword_groups_pass(answer['answer'], expected['wordGroups'])}
    return answer, checks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ollama-url', default='http://127.0.0.1:11434')
    parser.add_argument('--report', required=True, type=Path, help='New JSON report path; existing reports are never overwritten')
    args = parser.parse_args()
    prompt, model, options, timeout, provenance = application_inputs()
    client = JsonClient(args.ollama_url, timeout)
    client.opener = build_opener(ProxyHandler({}), NoRedirects())
    args.report.parent.mkdir(parents=True, exist_ok=True)
    report = {'schemaVersion': 1, 'startedAt': timestamp(), 'status': 'running', 'passed': False,
              'scope': 'Supplementary direct local Ollama provider probe; NOT retrieval, ACL or end-to-end evaluation',
              'manualReviewRequired': True, 'ollamaUrl': client.base, 'model': model,
              'options': options, 'think': False, 'promptProvenance': provenance, 'cases': []}
    # Exclusive creation also avoids a check-then-write race with another probe.
    with args.report.open('x', encoding='utf-8') as destination:
        try:
            report['ollamaVersion'] = client.require('/api/version')
            installed = client.require('/api/tags')
            matches = [entry for entry in installed.get('models', [])
                       if model in (entry.get('name'), entry.get('model'))]
            if len(matches) != 1 or not matches[0].get('digest'):
                raise EvaluationError('The application generation model must already be installed with a recorded digest')
            report['installedModel'] = matches[0]
            for scenario in cases():
                result = dict(scenario)
                report['cases'].append(result)
                started = time.perf_counter()
                status, raw = client.call('/api/chat', request_body(model, options, prompt, scenario))
                result.update(httpStatus=status, latencyMs=round((time.perf_counter() - started) * 1000, 2),
                              rawResponse=raw, passed=False)
                try:
                    if status != 200:
                        raise EvaluationError('Provider HTTP request failed')
                    answer, checks = validate_response(raw, model, options, scenario)
                    result.update(answer=answer, checks=checks, passed=all(checks.values()))
                except EvaluationError as error:
                    result['error'] = str(error)
                print(scenario['id'] + ': ' + ('passed wording/contract checks; manual review required'
                                              if result['passed'] else 'FAILED'), flush=True)
            report['status'] = 'completed'
            report['passed'] = len(report['cases']) == 2 and all(case['passed'] for case in report['cases'])
        except (EvaluationError, ValueError, TypeError, AttributeError, KeyError) as error:
            report.update(status='failed', error=str(error) if isinstance(error, EvaluationError) else type(error).__name__)
        except KeyboardInterrupt:
            report.update(status='interrupted', error='Interrupted by operator')
        finally:
            report['completedAt'] = timestamp()
            json.dump(report, destination, ensure_ascii=False, indent=2)
            destination.write('\n')
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (EvaluationError, FileExistsError) as error:
        print('Probe not started: ' + str(error), file=sys.stderr)
        sys.exit(2)
