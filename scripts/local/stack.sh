#!/bin/bash
# Native macOS runtime. Python's stdlib handles config and detached process identity.
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"
exec python3 - "$@" <<'PYTHON_RUNTIME'
import fcntl
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request


class RuntimeErrorSafe(Exception):
    pass


def fail(message):
    raise RuntimeErrorSafe(message)


def command(args, **kwargs):
    return subprocess.run(args, check=True, text=True, **kwargs)


def captured(args):
    return command(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout.strip()


def process_identity(pid):
    """PID reuse cannot authorize a stop: compare owner, birth time and command."""
    if not isinstance(pid, int) or pid <= 1:
        return None
    try:
        owner = captured(["/bin/ps", "-p", str(pid), "-o", "uid="])
        started = captured(["/bin/ps", "-p", str(pid), "-o", "lstart="])
        args = captured(["/bin/ps", "-ww", "-p", str(pid), "-o", "command="])
        status = captured(["/bin/ps", "-p", str(pid), "-o", "stat="])
    except subprocess.CalledProcessError:
        return None
    if not owner or "Z" in status:
        return None
    return {"uid": int(owner), "started": started, "command": args}


class Runtime:
    CONFIG_KEYS = {
        "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD", "APP_PORT",
        "OLLAMA_PORT", "APP_JWT_SECRET_KEY", "RAG_REQUESTS_PER_MINUTE", "RAG_DAILY_REQUESTS",
    }

    def __init__(self, root):
        self.root = Path(root).resolve()
        self.local = self.root / ".local"
        self.config_path = self.local / "config.env"
        self.config = {}
        self.lock_handle = None
        self.http = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def ensure_directory(self, path):
        if path.is_symlink():
            fail(f"Refusing symlink runtime directory: {path}")
        path.mkdir(mode=0o700, parents=True, exist_ok=True)
        if not path.is_dir() or path.stat().st_uid != os.getuid():
            fail(f"Runtime directory is not owned by this user: {path}")
        path.chmod(0o700)

    def prepare_directories(self):
        for relative in ("", "run", "logs", "app", "data", "data/git", "data/blobs",
                         "ollama-models", "m2", "bin"):
            self.ensure_directory(self.local / relative)

    def lock(self):
        self.ensure_directory(self.local)
        lock_path = self.local / "stack.lock"
        if lock_path.is_symlink():
            fail("Refusing symlink stack.lock")
        self.lock_handle = open(lock_path, "a")
        os.chmod(lock_path, 0o600)
        try:
            fcntl.flock(self.lock_handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            fail("Another stack command is running; wait for it to finish.")

    def check_private_file(self, path):
        if path.is_symlink() or not path.is_file():
            fail(f"Expected an ordinary owner-only file: {path}")
        info = path.stat()
        if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) & 0o077:
            fail(f"File must be owned by this user with mode 0600: {path}")

    def write_private(self, path, content):
        if path.is_symlink():
            fail(f"Refusing symlink file: {path}")
        handle, temporary = tempfile.mkstemp(prefix=".stack-", dir=str(path.parent))
        try:
            with os.fdopen(handle, "w") as output:
                output.write(content)
                output.flush()
                os.fsync(output.fileno())
            os.replace(temporary, path)
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)

    def create_config(self):
        if self.config_path.exists() or self.config_path.is_symlink():
            self.load_config()
            return
        values = {
            "DB_PORT": "55434", "DB_NAME": "knowledge_base_rag", "DB_USERNAME": "kb_user",
            "DB_PASSWORD": secrets.token_hex(32), "APP_PORT": "18081", "OLLAMA_PORT": "11434",
            "APP_JWT_SECRET_KEY": secrets.token_hex(48),
            "RAG_REQUESTS_PER_MINUTE": "6", "RAG_DAILY_REQUESTS": "100",
        }
        self.write_private(self.config_path, "# Private local settings. Plain KEY=VALUE; do not source this file.\n"
                           + "".join(f"{key}={value}\n" for key, value in values.items()))
        self.load_config()

    def load_config(self):
        if not self.config_path.exists():
            fail("Local config is missing. Run scripts/local/stack.sh setup first.")
        self.check_private_file(self.config_path)
        values = {}
        for number, raw in enumerate(self.config_path.read_text().splitlines(), 1):
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            key, separator, value = line.partition("=")
            if not separator or key not in self.CONFIG_KEYS or key in values or not value:
                fail(f"Invalid config key, empty value or duplicate on line {number}; values were not printed.")
            # Never source shell syntax, substitutions or quotes from the secret file.
            if not re.fullmatch(r"[A-Za-z0-9_./:+@%=-]+", value):
                fail(f"Unsupported config value on line {number}; use plain values without shell syntax.")
            values[key] = value
        if set(values) != self.CONFIG_KEYS:
            fail("Incomplete config; missing keys: " + ", ".join(sorted(self.CONFIG_KEYS - set(values))))
        for key in ("DB_PORT", "APP_PORT", "OLLAMA_PORT"):
            if not values[key].isdigit() or not 1024 <= int(values[key]) <= 65535:
                fail(f"{key} must be an unprivileged TCP port (1024..65535).")
        if len({values[key] for key in ("DB_PORT", "APP_PORT", "OLLAMA_PORT")}) != 3:
            fail("PostgreSQL, application and Ollama ports must be distinct.")
        for key in ("DB_NAME", "DB_USERNAME"):
            if not re.fullmatch(r"[a-z][a-z0-9_]{0,62}", values[key]):
                fail(f"{key} must be a lowercase PostgreSQL identifier.")
        for key, upper in (("RAG_REQUESTS_PER_MINUTE", 120), ("RAG_DAILY_REQUESTS", 10000)):
            if not values[key].isdigit() or not 1 <= int(values[key]) <= upper:
                fail(f"{key} must be within 1..{upper}.")
        if len(values["DB_PASSWORD"]) < 24 or len(values["APP_JWT_SECRET_KEY"]) < 64:
            fail("DB_PASSWORD requires at least 24 characters; APP_JWT_SECRET_KEY requires at least 64.")
        self.config = values

    def tool(self, name):
        found = shutil.which(name)
        if not found:
            fail(f"Missing {name}; source scripts/local/env.sh and install the documented native prerequisites.")
        return str(Path(found).resolve())

    def pg_tool(self, name):
        location = Path(os.environ["KB_PG_BIN"]) / name
        if not location.is_file() or not os.access(location, os.X_OK):
            fail(f"Missing PostgreSQL 18 tool: {location}; install postgresql@18.")
        return str(location.resolve())

    def port(self, service):
        return self.config[{"postgres": "DB_PORT", "app": "APP_PORT", "ollama": "OLLAMA_PORT"}[service]]

    def listeners(self, service):
        result = subprocess.run(["/usr/sbin/lsof", "-nP", "-iTCP:" + self.port(service),
                                 "-sTCP:LISTEN", "-Fp", "-Fn"], text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode not in (0, 1) or result.stderr.strip():
            fail("Cannot inspect TCP ownership with lsof; refusing to guess.")
        listeners, pid = [], None
        for line in result.stdout.splitlines():
            if line.startswith("p"):
                pid = int(line[1:])
            elif line.startswith("n") and pid is not None:
                listeners.append((pid, line[1:]))
        return listeners

    def record_path(self, service):
        return self.local / "run" / (service + ".json")

    def argv(self, service):
        if service == "postgres":
            return [self.pg_tool("postgres"), "-D", str(self.local / "postgres"), "-h", "127.0.0.1",
                    "-p", self.port(service), "-k", str(self.local / "run"),
                    "-c", "unix_socket_permissions=0700", "-c", "password_encryption=scram-sha-256"]
        if service == "ollama":
            return [self.tool("ollama"), "serve"]
        return [str((Path(os.environ["JAVA_HOME"]) / "bin/java").resolve()),
                "-Dkb.local.runtime=" + str(self.local), "-jar", str(self.local / "app/application.jar"),
                "--server.address=127.0.0.1", "--server.port=" + self.port(service),
                "--spring.profiles.active=dev", "--spring.jpa.show-sql=false",
                "--logging.level.org.hibernate.SQL=WARN", "--logging.level.org.springframework.security=WARN",
                "--logging.level.org.hibernate.type.descriptor.sql.BasicBinder=WARN",
                "--logging.level.com.knowledgebase=INFO"]

    def owned_record(self, service):
        path = self.record_path(service)
        if path.is_symlink():
            fail(f"Refusing symlink {service} process record.")
        if not path.exists():
            return None
        self.check_private_file(path)
        try:
            record = json.loads(path.read_text())
            pid = record["pid"]
            if record["root"] != str(self.local) or record["service"] != service:
                fail(f"Mismatched {service} process record; refusing to adopt or stop it.")
            identity = process_identity(pid)
            if identity is None:
                return None
            if identity != record["identity"] or identity["uid"] != os.getuid():
                fail(f"{service} PID belongs to a different process; refusing to adopt or stop it.")
            if record["argv"] != self.argv(service) or identity["command"] != " ".join(record["argv"]):
                fail(f"{service} command/config changed since launch; refusing to adopt or stop it.")
            return record
        except (ValueError, KeyError, TypeError):
            fail(f"Invalid {service} process record; inspect {path} manually.")

    def preflight(self, service):
        record = self.owned_record(service)
        listeners = self.listeners(service)
        if listeners and (record is None or any(pid != record["pid"] for pid, _ in listeners)):
            fail(f"{service}: port {self.port(service)} has an unmanaged listener. Stop it explicitly or change local ports.")
        for _, address in listeners:
            if address != "127.0.0.1:" + self.port(service):
                fail(f"{service}: managed listener is not the required IPv4 loopback address.")
        if service == "postgres" and record is None:
            postmaster = self.local / "postgres/postmaster.pid"
            if postmaster.exists():
                try:
                    old_pid = int(postmaster.read_text().splitlines()[0])
                except (ValueError, IndexError):
                    fail("Invalid postmaster.pid; inspect the cluster before starting.")
                if process_identity(old_pid) is not None:
                    fail("The PostgreSQL cluster has an unmanaged live postmaster; refusing to adopt it.")
        return record

    def clean_environment(self):
        prefixes = ("APP_", "SPRING_", "RAG_", "OLLAMA_", "DB_", "AI_", "MAIL_", "NOTIFICATIONS_", "GIT_")
        exact = {"BLOB_STORAGE_PATH", "JWT_SECRET_KEY", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"}
        return {key: value for key, value in os.environ.items()
                if not key.startswith(prefixes) and key not in exact}

    def environment(self, service):
        env = self.clean_environment()
        if service == "ollama":
            env.update(OLLAMA_HOST="127.0.0.1:" + self.config["OLLAMA_PORT"],
                       OLLAMA_MODELS=str(self.local / "ollama-models"), OLLAMA_NO_CLOUD="1")
        if service == "app":
            env.update(DB_URL="jdbc:postgresql://127.0.0.1:" + self.config["DB_PORT"] + "/" + self.config["DB_NAME"],
                       DB_USERNAME=self.config["DB_USERNAME"], DB_PASSWORD=self.config["DB_PASSWORD"],
                       APP_JWT_SECRET_KEY=self.config["APP_JWT_SECRET_KEY"],
                       GIT_REPO_PATH=str(self.local / "data/git"), BLOB_STORAGE_PATH=str(self.local / "data/blobs"),
                       AI_ENABLED="false", NOTIFICATIONS_ENABLED="false", RAG_ENABLED="true",
                       RAG_BASE_URL="http://127.0.0.1:" + self.config["OLLAMA_PORT"],
                       RAG_EMBEDDING_MODEL="qwen3-embedding:0.6b", RAG_EMBEDDING_DIMENSIONS="1024",
                       RAG_GENERATION_MODEL="qwen3:4b", RAG_REQUESTS_PER_MINUTE=self.config["RAG_REQUESTS_PER_MINUTE"],
                       RAG_DAILY_REQUESTS=self.config["RAG_DAILY_REQUESTS"], SPRING_LIQUIBASE_CONTEXTS="rag")
        return env

    def launch(self, service):
        if self.preflight(service):
            print(f"{service}: already managed; checking readiness")
            return
        args = self.argv(service)
        log_path = self.local / "logs" / (service + ".log")
        if log_path.is_symlink():
            fail(f"Refusing symlink log: {log_path}")
        with open(log_path, "ab") as log, open(os.devnull, "rb") as stdin:
            child = subprocess.Popen(args, cwd=str(self.root), stdin=stdin, stdout=log, stderr=log,
                                     env=self.environment(service), start_new_session=True, close_fds=True)
        # Wait for exec to replace the short-lived fork image before recording identity.
        identity = None
        for _ in range(30):
            identity = process_identity(child.pid)
            if identity and identity["command"] == " ".join(args):
                break
            if child.poll() is not None:
                fail(f"{service} exited during startup; inspect {log_path}.")
            time.sleep(0.1)
        if not identity or identity["command"] != " ".join(args):
            fail(f"Cannot confirm {service} identity; inspect PID {child.pid} and {log_path} before retrying.")
        self.write_private(self.record_path(service), json.dumps({
            "pid": child.pid, "service": service, "root": str(self.local), "argv": args, "identity": identity,
        }) + "\n")
        print(f"{service}: started PID {child.pid}", flush=True)

    def sql(self, query, database=None):
        env = self.clean_environment()
        env.update(PGPASSWORD=self.config["DB_PASSWORD"], PGCONNECT_TIMEOUT="3")
        return command([self.pg_tool("psql"), "-X", "-w", "-h", "127.0.0.1", "-p", self.config["DB_PORT"],
                        "-U", self.config["DB_USERNAME"], "-d", database or self.config["DB_NAME"],
                        "-At", "-v", "ON_ERROR_STOP=1", "-c", query], env=env,
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout.strip()

    def http_json(self, service, path):
        with self.http.open("http://127.0.0.1:" + self.port(service) + path, timeout=3) as response:
            return json.load(response)

    def wait_ready(self, service, timeout=60):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.preflight(service) is None:
                fail(f"{service} stopped before becoming ready; inspect .local/logs/{service}.log.")
            try:
                if service == "postgres":
                    ready = self.sql("SELECT 1", "postgres") == "1"
                elif service == "ollama":
                    ready = "models" in self.http_json(service, "/api/tags")
                else:
                    ready = self.http_json(service, "/actuator/health").get("status") == "UP"
                if ready:
                    print(f"{service}: ready at 127.0.0.1:{self.port(service)}", flush=True)
                    return
            except (subprocess.CalledProcessError, urllib.error.URLError, TimeoutError, ValueError, OSError):
                pass
            time.sleep(0.5)
        fail(f"{service} did not become ready within {timeout}s; inspect .local/logs/{service}.log. Managed dependencies were retained.")

    def check_models(self):
        available = {model.get("name") for model in self.http_json("ollama", "/api/tags").get("models", [])}
        missing = {"qwen3-embedding:0.6b", "qwen3:4b"} - available
        if missing:
            fail("Missing local Ollama models: " + ", ".join(sorted(missing))
                 + ". Pull these with OLLAMA_HOST=http://127.0.0.1:" + self.config["OLLAMA_PORT"]
                 + " ollama pull MODEL, then retry start.")

    def setup(self):
        self.prepare_directories()
        self.create_config()
        for name in ("mvn", "node", "ollama"):
            self.tool(name)
        if not captured([self.tool("node"), "--version"]).startswith("v24."):
            fail("Node 24 is required; source scripts/local/env.sh after installing node@24.")
        cluster = self.local / "postgres"
        self.ensure_directory(cluster)
        if not (cluster / "PG_VERSION").exists():
            if any(cluster.iterdir()):
                fail("PostgreSQL directory is nonempty without PG_VERSION; refusing to overwrite it.")
            self.preflight("postgres")
            descriptor, password_file = tempfile.mkstemp(prefix=".pg-password-", dir=str(self.local))
            try:
                with os.fdopen(descriptor, "w") as output:
                    output.write(self.config["DB_PASSWORD"] + "\n")
                command([self.pg_tool("initdb"), "-D", str(cluster), "--username=" + self.config["DB_USERNAME"],
                         "--pwfile=" + password_file, "--auth-host=scram-sha-256", "--auth-local=trust", "--encoding=UTF8"])
            finally:
                os.unlink(password_file)
        elif (cluster / "PG_VERSION").read_text().strip() != "18":
            fail("Existing cluster is not PostgreSQL 18; an explicit database upgrade is required.")
        self.launch("postgres")
        self.wait_ready("postgres")
        database = self.config["DB_NAME"]
        if self.sql(f"SELECT 1 FROM pg_database WHERE datname = '{database}'", "postgres") != "1":
            self.sql(f'CREATE DATABASE "{database}" OWNER "{self.config["DB_USERNAME"]}"', "postgres")
        self.sql("CREATE EXTENSION IF NOT EXISTS vector")
        self.install_openspec_wrapper()
        print("Setup complete. PostgreSQL remains managed and ready; existing data and secrets were preserved.")

    def install_openspec_wrapper(self):
        package = self.local / "tools/openspec/node_modules/@fission-ai/openspec/bin/openspec.js"
        if package.is_file():
            wrapper = self.local / "bin/openspec"
            script = '#!/bin/bash\nset -e\nsource "$(cd "$(dirname "$0")/../.." && pwd)/scripts/local/env.sh"\nexec node "$KB_LOCAL_ROOT/tools/openspec/node_modules/@fission-ai/openspec/bin/openspec.js" "$@"\n'
            self.write_private(wrapper, script)
            wrapper.chmod(0o700)
        else:
            print("OpenSpec CLI is not installed under .local/tools/openspec; see docs/LOCAL_SETUP_RU.md.")

    def build(self):
        self.prepare_directories()
        command([self.tool("mvn"), "-Dmaven.repo.local=" + str(self.local / "m2"), "clean", "verify"],
                cwd=str(self.root / "backend"))
        artifact = self.root / "backend/target/knowledge-base-backend-1.0.0-SNAPSHOT.jar"
        if not artifact.is_file():
            fail("Maven completed without the expected executable application JAR.")
        target = self.local / "app/application.jar"
        if target.is_symlink():
            fail("Refusing symlink application.jar")
        handle, temporary = tempfile.mkstemp(prefix=".application-", suffix=".jar", dir=str(target.parent))
        try:
            with os.fdopen(handle, "wb") as output, open(artifact, "rb") as source:
                shutil.copyfileobj(source, output)
                output.flush()
                os.fsync(output.fileno())
            os.replace(temporary, target)
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)
        print("Verified JAR installed atomically. Running processes were not restarted; stop/start to use the new build.")

    def start(self):
        self.load_config()
        self.prepare_directories()
        self.ensure_directory(self.local / "postgres")
        if not (self.local / "postgres/PG_VERSION").is_file():
            fail("PostgreSQL is not initialized; run setup first.")
        if (self.local / "app/application.jar").is_symlink():
            fail("Refusing symlink application.jar; run build to install a regular local artifact.")
        if not (self.local / "app/application.jar").is_file():
            fail("Application JAR is missing; run build first.")
        # Reject every foreign listener before starting any new dependency.
        for service in ("postgres", "ollama", "app"):
            self.preflight(service)
        self.launch("postgres")
        self.wait_ready("postgres")
        if self.sql("SELECT extversion FROM pg_extension WHERE extname = 'vector'") == "":
            fail("pgvector extension is missing from the local database; run setup.")
        self.launch("ollama")
        self.wait_ready("ollama")
        self.check_models()
        self.launch("app")
        self.wait_ready("app", timeout=120)
        print("Local application: http://127.0.0.1:" + self.config["APP_PORT"])

    def stop_service(self, service):
        record = self.preflight(service)
        if record is None:
            print(f"{service}: stopped (no managed live process)")
            return
        # Re-read identity immediately before sending a signal.
        if self.owned_record(service) != record:
            fail(f"{service} identity changed during stop; no signal sent.")
        os.kill(record["pid"], signal.SIGTERM)
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            current = process_identity(record["pid"])
            if current is None:
                self.record_path(service).unlink(missing_ok=True)
                print(f"{service}: stopped; persistent data retained", flush=True)
                return
            if current != record["identity"]:
                fail(f"{service} PID changed after TERM; no additional signal will be sent.")
            time.sleep(0.25)
        fail(f"{service} did not stop within 30s. No forced kill was attempted; inspect its log/process.")

    def stop(self):
        self.load_config()
        # Keep dependencies running if an application's identity is ambiguous.
        for service in ("app", "ollama", "postgres"):
            self.stop_service(service)

    def status(self):
        self.load_config()
        healthy = True
        for service in ("postgres", "ollama", "app"):
            try:
                record = self.preflight(service)
                if record is None:
                    print(f"{service}: stopped")
                    healthy = False
                    continue
                if service == "postgres":
                    ready = self.sql("SELECT 1") == "1"
                elif service == "ollama":
                    self.check_models()
                    ready = True
                else:
                    ready = self.http_json(service, "/actuator/health").get("status") == "UP"
                print(f"{service}: managed PID {record['pid']}, {'ready' if ready else 'not ready'}, 127.0.0.1:{self.port(service)}")
                healthy = healthy and ready
            except (RuntimeErrorSafe, subprocess.CalledProcessError, urllib.error.URLError, TimeoutError, ValueError, OSError) as error:
                detail = str(error) if isinstance(error, RuntimeErrorSafe) else "readiness check failed; inspect service log"
                print(f"{service}: ERROR: {detail}")
                healthy = False
        return 0 if healthy else 1

    def logs(self, service):
        path = self.local / "logs" / (service + ".log")
        if not path.is_file() or path.is_symlink():
            fail(f"No ordinary log file exists yet: {path}")
        command(["/usr/bin/tail", "-n", "100", str(path)])


def main(args):
    os.umask(0o077)
    usage = "Usage: scripts/local/stack.sh {setup|build|start|stop|status|logs [app|postgres|ollama]}"
    if not args or args[0] in ("help", "--help", "-h"):
        print(usage)
        return 0
    action = args[0]
    if action not in ("setup", "build", "start", "stop", "status", "logs"):
        fail(usage)
    if (action != "logs" and len(args) != 1) or len(args) > 2:
        fail(usage)
    service = args[1] if len(args) == 2 else "app"
    if action == "logs" and service not in ("app", "postgres", "ollama"):
        fail(usage)
    runtime = Runtime(os.environ["KB_PROJECT_ROOT"])
    if action in ("setup", "build", "start", "stop"):
        runtime.lock()
    if action == "logs":
        runtime.logs(service)
    else:
        return getattr(runtime, action)() or 0
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except RuntimeErrorSafe as error:
        print("ERROR: " + str(error), file=sys.stderr)
        sys.exit(1)
    except subprocess.CalledProcessError:
        # Do not expose command environments or database credentials in diagnostic traces.
        print("ERROR: A native command failed; inspect the preceding output or .local/logs. No data was reset.", file=sys.stderr)
        sys.exit(1)
    except (OSError, ValueError) as error:
        print("ERROR: Local runtime operation failed (" + type(error).__name__ + "). Check paths, permissions and logs.", file=sys.stderr)
        sys.exit(1)
PYTHON_RUNTIME
