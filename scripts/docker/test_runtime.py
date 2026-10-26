"""Isolated filesystem and mock-HTTP checks; never start Docker, a DB or real models."""
from contextlib import redirect_stdout
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def load(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


runtime = load("runtime_init")
models = load("models_init")


class RuntimeInitializationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="kb-docker-init-")
        self.root = Path(self.temporary.name)
        self.secret = self.root / "secrets"
        self.content = self.root / "content"
        self.database = self.root / "database"
        self.database.mkdir()

    def tearDown(self):
        self.temporary.cleanup()

    def initialize(self):
        with redirect_stdout(io.StringIO()):
            return runtime.initialize(self.secret, self.content, self.database, os.getuid(), os.getgid())

    def test_initialization_and_retry_preserve_secrets_database_and_content(self):
        self.assertTrue(self.initialize())
        published = self.secret / "current"
        snapshot = {p.name: (p.read_bytes(), p.stat().st_mtime_ns) for p in published.iterdir()}
        self.assertEqual({"db_password", "jwt_secret"}, set(snapshot))
        self.assertEqual(65, len(snapshot["db_password"][0]))
        self.assertEqual(97, len(snapshot["jwt_secret"][0]))
        self.assertNotEqual(snapshot["db_password"][0], snapshot["jwt_secret"][0][:65])
        for secret in published.iterdir():
            self.assertEqual(0o400, secret.stat().st_mode & 0o777)
        pg_version = self.database / "18/docker/PG_VERSION"
        pg_version.parent.mkdir(parents=True)
        pg_version.write_text("18\n")
        article = self.content / "git/published.md"
        article.write_text("User content")
        blob = self.content / "blobs/attachment.png"
        blob.write_bytes(b"unchanged attachment")
        self.assertFalse(self.initialize())
        self.assertEqual(snapshot, {p.name: (p.read_bytes(), p.stat().st_mtime_ns) for p in published.iterdir()})
        self.assertEqual("18\n", pg_version.read_text())
        self.assertEqual("User content", article.read_text())
        self.assertEqual(b"unchanged attachment", blob.read_bytes())

    def test_existing_pg18_without_secret_set_fails_without_creating_password(self):
        pg_version = self.database / "18/docker/PG_VERSION"
        pg_version.parent.mkdir(parents=True)
        pg_version.write_text("18\n")
        with self.assertRaisesRegex(runtime.InitializationError, "Database state exists"):
            self.initialize()
        self.assertFalse((self.secret / "current").exists())
        self.assertEqual("18\n", pg_version.read_text())

    def test_partial_secret_set_is_not_repaired_or_rotated(self):
        self.initialize()
        password = (self.secret / "current/db_password").read_bytes()
        (self.secret / "current/jwt_secret").unlink()
        with self.assertRaisesRegex(runtime.InitializationError, "incomplete"):
            self.initialize()
        self.assertEqual(password, (self.secret / "current/db_password").read_bytes())
        self.assertFalse((self.secret / "current/jwt_secret").exists())

    def test_failure_between_secret_creation_never_publishes_half_a_pair(self):
        with patch.object(runtime.secrets, "token_hex", side_effect=["a" * 64, OSError("simulated disk failure")]):
            with self.assertRaises(OSError):
                self.initialize()
        self.assertFalse((self.secret / "current").exists())
        with self.assertRaisesRegex(runtime.InitializationError, "Unpublished"):
            self.initialize()
        self.assertFalse((self.secret / "current").exists())

    def test_world_readable_secret_is_rejected_and_not_silently_replaced(self):
        self.initialize()
        password = self.secret / "current/db_password"
        original = password.read_bytes()
        password.chmod(0o644)
        with self.assertRaisesRegex(runtime.InitializationError, "permissions"):
            self.initialize()
        self.assertEqual(original, password.read_bytes())

    def test_existing_content_without_secrets_is_preserved_and_refused(self):
        self.content.mkdir()
        article = self.content / "existing.md"
        article.write_text("Keep this")
        with self.assertRaisesRegex(runtime.InitializationError, "Content state exists"):
            self.initialize()
        self.assertEqual("Keep this", article.read_text())
        self.assertFalse((self.secret / "current").exists())

    def test_linked_secret_file_is_never_followed(self):
        self.initialize()
        password = self.secret / "current/db_password"
        original = password.read_bytes()
        outside = self.root / "outside-password"
        outside.write_bytes(original)
        password.unlink()
        password.symlink_to(outside)
        with self.assertRaisesRegex(runtime.InitializationError, "ordinary file"):
            self.initialize()
        self.assertEqual(original, outside.read_bytes())

    def test_application_entrypoint_uses_files_over_legacy_environment_without_printing_secrets(self):
        self.initialize()
        fake_bin = self.root / "bin"
        fake_bin.mkdir()
        java = fake_bin / "java"
        java.write_text('#!/bin/sh\n[ "$DB_PASSWORD" = "$(cat "$EXPECTED_DB_FILE")" ] || exit 21\n'
                        '[ "$JWT_SECRET_KEY" = "$(cat "$EXPECTED_JWT_FILE")" ] || exit 22\n'
                        '[ "$1" = "-jar" ] && [ "$2" = "/app/application.jar" ] || exit 23\n'
                        'printf "%s\\n" "entrypoint verified"\n')
        java.chmod(0o700)
        db_file, jwt_file = self.secret / "current/db_password", self.secret / "current/jwt_secret"
        env = dict(os.environ, PATH=str(fake_bin) + ":" + os.environ["PATH"], DB_PASSWORD="legacy-weak",
                   JWT_SECRET_KEY="legacy-weak", DB_PASSWORD_FILE=str(db_file), JWT_SECRET_KEY_FILE=str(jwt_file),
                   EXPECTED_DB_FILE=str(db_file), EXPECTED_JWT_FILE=str(jwt_file))
        entrypoint = Path(__file__).resolve().parents[2] / "backend/docker-entrypoint.sh"
        result = subprocess.run(["/bin/sh", str(entrypoint)], env=env, text=True, capture_output=True, check=True)
        self.assertEqual("entrypoint verified\n", result.stdout)
        self.assertEqual("", result.stderr)
        self.assertNotIn(db_file.read_text().strip(), result.stdout + result.stderr)


class ModelPreparationTest(unittest.TestCase):
    def setUp(self):
        self.requests = []
        self.available = [models.EMBEDDING_MODEL, models.GENERATION_MODEL]
        self.embedding = [0.01] * 1024
        self.chat = {"done": True, "message": {"content": "READY"}}
        self.pull_success = True
        self.delay = 0
        self.http_status = 200
        test = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_args):
                pass

            def do_GET(self):
                self.respond()

            def do_POST(self):
                self.respond()

            def respond(self):
                size = int(self.headers.get("Content-Length", 0))
                payload = json.loads(self.rfile.read(size)) if size else None
                test.requests.append((self.path, payload))
                time.sleep(test.delay)
                if self.path == "/api/tags":
                    body = json.dumps({"models": [{"name": name} for name in test.available]}).encode()
                elif self.path == "/api/pull":
                    records = [{"status": "pulling manifest"}]
                    records.append({"status": "success"} if test.pull_success else {"error": "failed"})
                    body = b"".join(json.dumps(record).encode() + b"\n" for record in records)
                elif self.path == "/api/embed":
                    body = json.dumps({"embeddings": [test.embedding]}).encode()
                elif self.path == "/api/chat":
                    body = json.dumps(test.chat).encode()
                else:
                    body = b"{}"
                self.send_response(test.http_status)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                try:
                    self.wfile.write(body)
                except (BrokenPipeError, ConnectionResetError):
                    pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.client = models.Ollama("http://127.0.0.1:" + str(self.server.server_port))

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)

    def prepare(self):
        with redirect_stdout(io.StringIO()):
            models.prepare(self.client)

    def test_existing_models_are_not_pulled_but_both_inferences_are_checked(self):
        self.prepare()
        self.assertEqual(["/api/tags", "/api/embed", "/api/chat"], [path for path, _ in self.requests])
        chat = self.requests[-1][1]
        self.assertFalse(chat["think"])
        self.assertFalse(chat["stream"])
        self.assertEqual(16, chat["options"]["num_predict"])
        self.assertEqual(8192, chat["options"]["num_ctx"])

    def test_only_missing_model_is_downloaded_before_inference(self):
        self.available = [models.EMBEDDING_MODEL]
        self.prepare()
        self.assertEqual([(models.GENERATION_MODEL, True)], [(body["model"], body["stream"])
                         for path, body in self.requests if path == "/api/pull"])
        self.assertEqual("/api/chat", self.requests[-1][0])

    def test_failed_download_does_not_claim_readiness_or_infer(self):
        self.available = []
        self.pull_success = False
        with self.assertRaisesRegex(models.ModelInitializationError, "download"):
            self.prepare()
        self.assertEqual(["/api/tags", "/api/pull"], [path for path, _ in self.requests])

    def test_wrong_embedding_dimension_prevents_chat_and_readiness(self):
        self.embedding = [0.01] * 768
        with self.assertRaisesRegex(models.ModelInitializationError, "1024"):
            self.prepare()
        self.assertNotIn("/api/chat", [path for path, _ in self.requests])

    def test_nonfinite_boolean_and_zero_embeddings_are_rejected(self):
        for bad_vector in ([float("nan")] * 1024, [True] * 1024, [0.0] * 1024):
            with self.subTest(first=bad_vector[0]):
                self.embedding = bad_vector
                with self.assertRaises(models.ModelInitializationError):
                    self.prepare()
        self.assertNotIn("/api/chat", [path for path, _ in self.requests])

    def test_empty_or_incomplete_generation_is_not_ready(self):
        for reply in ({"done": True, "message": {"content": "  "}},
                      {"done": False, "message": {"content": "READY"}}):
            with self.subTest(reply=reply):
                self.chat = reply
                with self.assertRaisesRegex(models.ModelInitializationError, "non-empty"):
                    self.prepare()

    def test_http_error_is_not_readiness(self):
        self.http_status = 503
        with self.assertRaisesRegex(models.ModelInitializationError, "HTTP 503"):
            self.prepare()

    def test_total_http_deadline_is_enforced(self):
        self.delay = 0.3
        started = time.monotonic()
        with self.assertRaises((TimeoutError, models.urllib.error.URLError)):
            self.client.request("/api/tags", timeout=0.05)
        self.assertLess(time.monotonic() - started, 0.2)

    def test_skip_requires_explicit_test_override(self):
        with patch.dict(os.environ, {"SKIP_MODEL_INIT": "true"}), patch.object(models, "prepare") as prepare:
            with redirect_stdout(io.StringIO()) as output:
                models.main()
        prepare.assert_not_called()
        self.assertIn("not real-model readiness", output.getvalue())


if __name__ == "__main__":
    unittest.main()
