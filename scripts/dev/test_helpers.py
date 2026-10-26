"""Exercise shell entrypoints with a fake Docker CLI; never contact an engine.

Run: python3 -m unittest discover -s scripts/dev -p 'test_*.py'
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
FAKE_DOCKER = r'''
import json
import os
from pathlib import Path
import sys

args = sys.argv[1:]
with open(os.environ["FAKE_DOCKER_LOG"], "a") as output:
    output.write(json.dumps({"args": args, "ambientProject": os.environ.get("COMPOSE_PROJECT_NAME"),
                             "ambientFile": os.environ.get("COMPOSE_FILE")}) + "\n")
if args[0] == "inspect":
    print(os.environ.get("FAKE_INSPECT_OWNER", args[-1].removesuffix("-browser")))
elif args[0] == "cp":
    pass
elif args[0] == "compose":
    if "version" in args:
        print("Docker Compose version v2.40.0")
    elif "config" in args:
        project = args[args.index("--project-name") + 1]
        volume = {"name": project + "_postgres_data"}
        app = {"environment": {"RAG_ENABLED": "false"}, "depends_on": {
            "runtime-init": {"condition": "service_completed_successfully"},
            "postgres": {"condition": "service_healthy"},
            "models-init": {"condition": "service_completed_successfully"}}, "volumes": [
            {"type": "volume", "source": "postgres_data", "target": "/data"}]}
        services = {"app": app, "models-init": {"environment": {"SKIP_MODEL_INIT": "true"}},
                    "runtime-init": {}, "postgres": {}, "ollama": {}, "e2e": {}}
        mode = os.environ.get("FAKE_UNSAFE_CONFIG", "")
        if mode == "shared-volume":
            volume["name"] = "user-live_postgres_data"
        elif mode == "external-volume":
            volume["external"] = True
        elif mode == "host-mount":
            app["volumes"] = [{"type": "bind", "source": "/user/data", "target": "/data"}]
        elif mode == "published-port":
            app["ports"] = [{"target": 8080, "published": "8080"}]
        elif mode == "fixed-container":
            app["container_name"] = "user_app"
        elif mode == "rag-enabled":
            app["environment"]["RAG_ENABLED"] = "true"
        elif mode == "app-needs-ollama":
            app["depends_on"]["ollama"] = {"condition": "service_healthy"}
        elif mode == "models-init-needs-ollama":
            services["models-init"]["depends_on"] = {"ollama": {"condition": "service_healthy"}}
        elif mode == "postgres-needs-ollama":
            services["postgres"]["depends_on"] = {"ollama": {"condition": "service_healthy"}}
        elif mode == "postgres-not-healthy":
            app["depends_on"]["postgres"]["condition"] = "service_started"
        elif mode == "initializer-optional":
            app["depends_on"]["runtime-init"]["required"] = False
        print(json.dumps({"name": project, "volumes": {"postgres_data": volume},
                          "networks": {"default": {"name": project + "_default"}},
                          "services": services}))
    elif "up" in args:
        sys.exit(int(os.environ.get("FAKE_UP_EXIT", "0")))
    elif "run" in args:
        sys.exit(int(os.environ.get("FAKE_TEST_EXIT", "0")))
    elif "down" in args:
        sys.exit(int(os.environ.get("FAKE_DOWN_EXIT", "0")))
'''


class DockerHelperSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="kb-helper-tests-")
        self.directory = Path(self.temporary.name)
        self.checkout = self.directory / "project with spaces"
        (self.checkout / "scripts/dev").mkdir(parents=True)
        for relative in ("run-e2e.sh", "scripts/dev/bootstrap.sh", "scripts/dev/attach.sh",
                         "scripts/dev/attach-vscode.sh"):
            shutil.copy2(ROOT / relative, self.checkout / relative)
        (self.checkout / "docker-compose.yml").write_text("services: {}\n")
        self.bin = self.directory / "bin"
        self.bin.mkdir()
        self.log = self.directory / "docker.jsonl"
        docker = self.bin / "docker"
        docker.write_text("#!" + sys.executable + "\n" + FAKE_DOCKER)
        docker.chmod(0o700)
        # Resolve python3 through the current interpreter, without installing anything.
        (self.bin / "python3").symlink_to(sys.executable)
        self.env = dict(os.environ, PATH=str(self.bin) + ":" + os.environ["PATH"],
                        FAKE_DOCKER_LOG=str(self.log), TMPDIR=str(self.directory),
                        COMPOSE_PROJECT_NAME="user-live", COMPOSE_FILE="user-compose.yml",
                        COMPOSE_PROFILES="user-profile", APP_PORT="18081")

    def tearDown(self):
        self.temporary.cleanup()

    def run_script(self, relative="run-e2e.sh", *args, **environment):
        return subprocess.run([str(self.checkout / relative), *args],
                              cwd=self.directory, env=dict(self.env, **environment),
                              text=True, capture_output=True, timeout=20)

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def test_e2e_uses_fresh_project_for_every_mutation_and_retains_reports(self):
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        calls = self.calls()
        compose = [call for call in calls if call["args"][0] == "compose" and "version" not in call["args"]]
        projects = {call["args"][call["args"].index("--project-name") + 1] for call in compose}
        self.assertEqual(1, len(projects))
        project = projects.pop()
        self.assertRegex(project, r"^kb-e2e-[a-z0-9]+$")
        self.assertNotEqual("user-live", project)
        for call in compose:
            self.assertIsNone(call["ambientProject"])
            self.assertIsNone(call["ambientFile"])
            args = call["args"]
            self.assertEqual("/dev/null", args[args.index("--env-file") + 1])
            self.assertEqual(str(self.checkout.resolve()), args[args.index("--project-directory") + 1])
        self.assertTrue(any("up" in call["args"] and "--wait" in call["args"] for call in calls))
        self.assertEqual(1, sum("down" in call["args"] for call in calls))
        self.assertFalse(any(call["args"][0] in ("volume", "rm", "stop") for call in calls))
        self.assertEqual(2, sum(call["args"][0] == "cp" for call in calls))
        self.assertTrue((self.checkout / ".local/e2e-results" / project).is_dir())
        self.assertFalse(list(self.directory.glob("kb-e2e.*")), "temporary override should be removed")

    def test_test_failure_exit_code_survives_cleanup(self):
        result = self.run_script(FAKE_TEST_EXIT="17")
        self.assertEqual(17, result.returncode, result.stdout + result.stderr)
        self.assertTrue(any("down" in call["args"] for call in self.calls()))

    def test_startup_failure_cleans_only_new_project_and_does_not_run_browser(self):
        result = self.run_script(FAKE_UP_EXIT="23")
        self.assertEqual(23, result.returncode, result.stdout + result.stderr)
        self.assertFalse(any("run" in call["args"] for call in self.calls()))
        self.assertEqual(1, sum("down" in call["args"] for call in self.calls()))

    def test_failed_cleanup_is_not_reported_as_success(self):
        result = self.run_script(FAKE_DOWN_EXIT="9")
        self.assertEqual(1, result.returncode)
        self.assertIn("Cleanup failed", result.stderr)

    def test_unwritable_report_location_does_not_skip_cleanup_or_hide_failure(self):
        (self.checkout / ".local").mkdir()
        (self.checkout / ".local/e2e-results").write_text("not a directory")
        result = self.run_script(FAKE_TEST_EXIT="19")
        self.assertEqual(19, result.returncode, result.stdout + result.stderr)
        self.assertIn("Cannot save browser artifacts", result.stderr)
        self.assertTrue(any("down" in call["args"] for call in self.calls()))

    def test_shared_data_or_unisolated_configuration_is_rejected_before_start(self):
        for mode in ("shared-volume", "external-volume", "host-mount", "published-port",
                     "fixed-container", "rag-enabled"):
            with self.subTest(mode=mode):
                if self.log.exists():
                    self.log.unlink()
                result = self.run_script(FAKE_UNSAFE_CONFIG=mode)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("Unsafe E2E configuration", result.stderr)
                self.assertFalse(any(action in call["args"] for call in self.calls()
                                     for action in ("up", "run", "down")))

    def test_foreign_container_label_does_not_authorize_artifact_copy(self):
        result = self.run_script(FAKE_INSPECT_OWNER="user-live")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertFalse(any(call["args"][0] == "cp" for call in self.calls()))

    def test_direct_or_transitive_ollama_dependency_is_rejected_before_start(self):
        for mode in ("app-needs-ollama", "models-init-needs-ollama", "postgres-needs-ollama"):
            with self.subTest(mode=mode):
                if self.log.exists():
                    self.log.unlink()
                result = self.run_script(FAKE_UNSAFE_CONFIG=mode)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("Unsafe E2E configuration", result.stderr)
                self.assertFalse(any(action in call["args"] for call in self.calls()
                                     for action in ("up", "run", "down")))

    def test_dependency_override_cannot_weaken_database_or_initializer_readiness(self):
        for mode in ("postgres-not-healthy", "initializer-optional"):
            with self.subTest(mode=mode):
                if self.log.exists():
                    self.log.unlink()
                result = self.run_script(FAKE_UNSAFE_CONFIG=mode)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("readiness dependency is missing", result.stderr)
                self.assertFalse(any(action in call["args"] for call in self.calls()
                                     for action in ("up", "run", "down")))

    def test_optional_browser_command_is_forwarded_without_shell_evaluation(self):
        marker = self.directory / "should-not-exist"
        text = "$(touch " + str(marker) + ")"
        result = self.run_script("run-e2e.sh", "npx", "playwright", "test", "--grep", text)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        run = next(call["args"] for call in self.calls() if "run" in call["args"])
        self.assertEqual(["e2e", "npx", "playwright", "test", "--grep", text], run[-6:])
        self.assertFalse(marker.exists())

    def test_bootstrap_preserves_environment_file_and_never_stops_existing_services(self):
        env_file = self.checkout / ".env"
        env_file.write_text("APP_PORT=18082\n")
        result = self.run_script("scripts/dev/bootstrap.sh")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("APP_PORT=18082\n", env_file.read_text())
        mutations = [call["args"] for call in self.calls() if "version" not in call["args"]]
        self.assertEqual(1, len(mutations))
        self.assertIn("up", mutations[0])
        self.assertIn("--wait", mutations[0])
        self.assertNotIn("down", mutations[0])

    def test_attach_uses_service_and_literal_argument_boundaries(self):
        result = self.run_script("scripts/dev/attach.sh", "printf", "%s", "hello; echo bad")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(["exec", "app", "printf", "%s", "hello; echo bad"], self.calls()[0]["args"][-5:])

    def test_vscode_opens_checkout_without_starting_or_inspecting_containers(self):
        code_log = self.directory / "code.json"
        code = self.bin / "code"
        code.write_text("#!" + sys.executable + "\nimport json,sys\n"
                        + "open(" + repr(str(code_log)) + ", 'w').write(json.dumps(sys.argv[1:]))\n")
        code.chmod(0o700)
        result = self.run_script("scripts/dev/attach-vscode.sh")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual([str(self.checkout.resolve())], json.loads(code_log.read_text()))
        self.assertFalse(self.calls())


class DevelopmentConfigurationContractTest(unittest.TestCase):
    """Static checks only: these do not claim a PowerShell or VS Code execution pass."""

    def test_powershell_wrappers_use_project_services_and_never_reset_data(self):
        bootstrap = (ROOT / "scripts/dev/bootstrap.ps1").read_text()
        attach = (ROOT / "scripts/dev/attach.ps1").read_text()
        editor = (ROOT / "scripts/dev/attach-vscode.ps1").read_text()
        for text in (bootstrap, attach, editor):
            for obsolete in ("kb_app", "kb_postgres", "/workspace", "--env-file", "Invoke-Expression",
                             "volume rm", " compose down", "docker inspect", "docker ps"):
                self.assertNotIn(obsolete, text)
            self.assertIn("exit $LASTEXITCODE", text)
        self.assertIn("up -d --build --wait --wait-timeout 1800", bootstrap)
        self.assertIn("exec app @Command", attach)
        self.assertIn("& code $ProjectRoot", editor)
        self.assertNotIn("& docker", editor)

    def test_devcontainer_is_independent_of_the_application_runtime(self):
        config = json.loads((ROOT / ".devcontainer/devcontainer.json").read_text())
        self.assertEqual("maven:3.9.9-eclipse-temurin-21", config["image"])
        self.assertEqual("stopContainer", config["shutdownAction"])
        self.assertIn("target=" + config["workspaceFolder"], config["workspaceMount"])
        self.assertIn("source=${localWorkspaceFolder}", config["workspaceMount"])
        self.assertNotIn("dockerComposeFile", config)
        self.assertNotIn("runServices", config)
        self.assertNotIn("docker.sock", json.dumps(config))
        self.assertNotIn(".ssh", json.dumps(config))


if __name__ == "__main__":
    unittest.main()
