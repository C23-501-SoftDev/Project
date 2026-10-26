"""Focused lifecycle safety regressions; no database, model or application startup.

Run: python3 -m unittest discover -s scripts/local -p 'test_*.py'
"""
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("stack.sh").read_text()
SOURCE = SCRIPT.split("<<'PYTHON_RUNTIME'\n", 1)[1].rsplit("\nPYTHON_RUNTIME", 1)[0]
MODULE = {"__name__": "stack_test"}
exec(compile(SOURCE, str(Path(__file__).with_name("stack.sh")), "exec"), MODULE)
Runtime = MODULE["Runtime"]
RuntimeErrorSafe = MODULE["RuntimeErrorSafe"]
process_identity = MODULE["process_identity"]


class LocalStackSafetyTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="kb-runtime-test-")
        self.runtime = Runtime(self.directory.name)
        self.runtime.prepare_directories()
        self.runtime.create_config()

    def tearDown(self):
        self.directory.cleanup()

    def test_repeated_configuration_keeps_secrets_and_existing_data(self):
        config = self.runtime.config_path
        original = config.read_bytes()
        before = config.stat().st_mtime_ns
        article = self.runtime.local / "data/git/article.md"
        article.write_text("Preserve the user's article")
        self.runtime.create_config()
        self.assertEqual(original, config.read_bytes())
        self.assertEqual(before, config.stat().st_mtime_ns)
        self.assertEqual("Preserve the user's article", article.read_text())
        self.assertEqual(0o600, config.stat().st_mode & 0o777)

    def test_shell_substitution_in_configuration_is_rejected_without_execution(self):
        marker = self.runtime.local / "executed"
        self.runtime.config_path.write_text(self.runtime.config_path.read_text().replace(
            "APP_PORT=18081", f"APP_PORT=$(touch {marker})"))
        with self.assertRaises(RuntimeErrorSafe):
            self.runtime.load_config()
        self.assertFalse(marker.exists())

    def test_public_secret_file_and_duplicate_config_are_rejected(self):
        self.runtime.config_path.chmod(0o644)
        with self.assertRaisesRegex(RuntimeErrorSafe, "0600"):
            self.runtime.load_config()
        self.runtime.config_path.chmod(0o600)
        with self.runtime.config_path.open("a") as output:
            output.write("APP_PORT=18082\n")
        with self.assertRaisesRegex(RuntimeErrorSafe, "duplicate"):
            self.runtime.load_config()

    def test_symlink_runtime_directory_does_not_change_outside_permissions(self):
        outside = Path(self.directory.name) / "outside"
        outside.mkdir(mode=0o755)
        linked = self.runtime.local / "linked"
        linked.symlink_to(outside, target_is_directory=True)
        with self.assertRaisesRegex(RuntimeErrorSafe, "symlink"):
            self.runtime.ensure_directory(linked)
        self.assertEqual(0o755, outside.stat().st_mode & 0o777)

    def _record(self, child, service="app"):
        identity = process_identity(child.pid)
        self.assertIsNotNone(identity)
        record = {"pid": child.pid, "service": service, "root": str(self.runtime.local),
                  "argv": ["/bin/sleep", "60"], "identity": identity}
        self.runtime.write_private(self.runtime.record_path(service), json.dumps(record))
        return record

    def test_reused_pid_birth_time_mismatch_does_not_signal_live_process(self):
        with subprocess.Popen(["/bin/sleep", "60"]) as child:
            try:
                record = self._record(child)
                record["identity"]["started"] = "different process birth time"
                self.runtime.write_private(self.runtime.record_path("app"), json.dumps(record))
                with self.assertRaisesRegex(RuntimeErrorSafe, "different process"):
                    self.runtime.stop_service("app")
                self.assertIsNone(child.poll())
            finally:
                child.terminate()
                child.wait()

    def test_matching_pid_record_with_unrelated_command_is_not_authority(self):
        with subprocess.Popen(["/bin/sleep", "60"]) as child:
            try:
                self._record(child)
                with patch.object(self.runtime, "argv", return_value=["/expected/java", "-jar", "application.jar"]):
                    with self.assertRaisesRegex(RuntimeErrorSafe, "command/config changed"):
                        self.runtime.stop_service("app")
                self.assertIsNone(child.poll())
            finally:
                child.terminate()
                child.wait()

    def test_exact_owned_process_stops_but_data_remains(self):
        with subprocess.Popen(["/bin/sleep", "60"]) as child:
            try:
                self._record(child)
                with patch.object(self.runtime, "argv", return_value=["/bin/sleep", "60"]), \
                        patch.object(self.runtime, "listeners", return_value=[]):
                    self.runtime.stop_service("app")
                child.wait(timeout=3)
                self.assertEqual(-signal.SIGTERM, child.returncode)
                self.assertFalse(self.runtime.record_path("app").exists())
                self.assertTrue(self.runtime.config_path.is_file())
            finally:
                if child.poll() is None:
                    child.terminate()
                    child.wait()

    def test_real_foreign_listener_is_not_adopted_or_terminated(self):
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            listener.listen(1)
            self.runtime.config["APP_PORT"] = str(listener.getsockname()[1])
            with self.assertRaisesRegex(RuntimeErrorSafe, "unmanaged listener"):
                self.runtime.preflight("app")
            self.assertGreaterEqual(listener.fileno(), 0)
            self.assertIn(os.getpid(), {pid for pid, _ in self.runtime.listeners("app")})

    def test_ambient_remote_and_java_configuration_cannot_override_local_services(self):
        with patch.dict(os.environ, {"RAG_BASE_URL": "https://remote.invalid", "SPRING_APPLICATION_JSON": "{}",
                                     "OLLAMA_HOST": "0.0.0.0:1234", "OLLAMA_MODELS": "/outside/models",
                                     "JAVA_TOOL_OPTIONS": "-Dapp.jwt.secret-key=bad", "AI_ENABLED": "true"}):
            app = self.runtime.environment("app")
            ollama = self.runtime.environment("ollama")
        self.assertEqual("http://127.0.0.1:11434", app["RAG_BASE_URL"])
        self.assertEqual("false", app["AI_ENABLED"])
        self.assertNotIn("SPRING_APPLICATION_JSON", app)
        self.assertNotIn("JAVA_TOOL_OPTIONS", app)
        self.assertEqual("127.0.0.1:11434", ollama["OLLAMA_HOST"])
        self.assertEqual(str(self.runtime.local / "ollama-models"), ollama["OLLAMA_MODELS"])
        self.assertEqual("1", ollama["OLLAMA_NO_CLOUD"])

    def test_openspec_wrapper_finds_checkout_even_with_spaces_and_different_cwd(self):
        root = Path(self.directory.name) / "checkout with spaces"
        runtime = Runtime(root)
        runtime.prepare_directories()
        scripts = root / "scripts/local"
        scripts.mkdir(parents=True)
        (scripts / "env.sh").write_text('export KB_LOCAL_ROOT="' + str(runtime.local) + '"\n')
        package = runtime.local / "tools/openspec/node_modules/@fission-ai/openspec/bin/openspec.js"
        package.parent.mkdir(parents=True)
        package.write_text("// fake package entry; no Node process needed\n")
        fake_bin = root / "fake-bin"
        fake_bin.mkdir()
        node = fake_bin / "node"
        node.write_text('#!/bin/bash\nprintf "%s\\n" "$@"\n')
        node.chmod(0o700)
        runtime.install_openspec_wrapper()
        env = dict(os.environ, PATH=str(fake_bin) + ":" + os.environ["PATH"])
        result = subprocess.run([str(runtime.local / "bin/openspec"), "status", "--json"],
                                cwd="/", env=env, check=True, text=True, capture_output=True)
        self.assertEqual([str(package), "status", "--json"], result.stdout.splitlines())

    def test_dangling_process_record_symlink_is_rejected_before_launch(self):
        self.runtime.record_path("app").symlink_to(self.runtime.local / "missing")
        with self.assertRaisesRegex(RuntimeErrorSafe, "symlink"):
            self.runtime.owned_record("app")

    def test_start_refuses_linked_external_pgdata_before_launching_any_service(self):
        outside = Path(self.directory.name) / "unrelated-cluster"
        outside.mkdir()
        (outside / "PG_VERSION").write_text("18\n")
        (self.runtime.local / "postgres").symlink_to(outside, target_is_directory=True)
        with patch.object(self.runtime, "launch") as launch:
            with self.assertRaisesRegex(RuntimeErrorSafe, "symlink"):
                self.runtime.start()
        launch.assert_not_called()
        self.assertEqual("18\n", (outside / "PG_VERSION").read_text())


if __name__ == "__main__":
    unittest.main()
