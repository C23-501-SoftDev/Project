"""Prepare local Ollama models and verify actual bounded inference before app startup."""
from contextlib import contextmanager
import json
import math
import os
import signal
import time
import urllib.error
import urllib.request


EMBEDDING_MODEL = "qwen3-embedding:0.6b"
GENERATION_MODEL = "qwen3:4b"
INFERENCE_TIMEOUT = 120
MAX_JSON_BYTES = 1024 * 1024


class ModelInitializationError(Exception):
    pass


@contextmanager
def deadline(seconds):
    """Bound the whole operation, including a peer that keeps sending tiny pieces."""
    def expired(_signum, _frame):
        raise TimeoutError("Ollama operation exceeded its deadline")
    previous = signal.signal(signal.SIGALRM, expired)
    signal.setitimer(signal.ITIMER_REAL, seconds)
    try:
        yield
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)
        signal.signal(signal.SIGALRM, previous)


class Ollama:
    def __init__(self, url="http://ollama:11434"):
        self.url = url.rstrip("/")
        self.http = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def request(self, path, payload=None, timeout=INFERENCE_TIMEOUT):
        body = json.dumps(payload).encode() if payload is not None else None
        request = urllib.request.Request(self.url + path, data=body,
                                         headers={"Content-Type": "application/json"})
        try:
            with deadline(timeout):
                with self.http.open(request, timeout=timeout) as response:
                    raw = response.read(MAX_JSON_BYTES + 1)
        except urllib.error.HTTPError as error:
            status = error.code
            error.close()
            raise ModelInitializationError(f"Ollama readiness endpoint returned HTTP {status}; inspect its service logs.") from None
        if len(raw) > MAX_JSON_BYTES:
            raise ModelInitializationError("Ollama returned an oversized readiness response.")
        try:
            result = json.loads(raw)
        except (ValueError, UnicodeDecodeError) as error:
            raise ModelInitializationError("Ollama returned invalid readiness JSON.") from error
        if not isinstance(result, dict) or result.get("error"):
            raise ModelInitializationError("Ollama reported a readiness error; inspect its service logs.")
        return result

    def pull(self, model):
        print("Downloading missing local model " + model + "; progress is retained in the models volume.", flush=True)
        request = urllib.request.Request(self.url + "/api/pull", data=json.dumps({"model": model, "stream": True}).encode(),
                                         headers={"Content-Type": "application/json"})
        completed = False
        previous_status, last_print = None, 0.0
        with deadline(1500):
            with self.http.open(request, timeout=120) as response:
                while raw := response.readline(MAX_JSON_BYTES + 1):
                    if len(raw) > MAX_JSON_BYTES:
                        raise ModelInitializationError("Ollama model download returned an oversized progress record.")
                    progress = json.loads(raw)
                    if not isinstance(progress, dict) or progress.get("error"):
                        raise ModelInitializationError("Ollama could not download the model; inspect its service logs and retry.")
                    status = progress.get("status", "working")
                    now = time.monotonic()
                    if status != previous_status or now - last_print >= 20:
                        total, downloaded = progress.get("total"), progress.get("completed")
                        percent = f" {100 * downloaded / total:.0f}%" if isinstance(total, int) and total > 0 and isinstance(downloaded, int) else ""
                        print(model + ": " + str(status)[:100] + percent, flush=True)
                        previous_status, last_print = status, now
                    if status == "success":
                        completed = True
        if not completed:
            raise ModelInitializationError("Model download ended without a success record; retry preserves downloaded layers.")


def prepare(ollama):
    tags = ollama.request("/api/tags", timeout=30)
    models = tags.get("models")
    if not isinstance(models, list):
        raise ModelInitializationError("Ollama model listing has an invalid shape.")
    existing = {model.get("name") for model in models if isinstance(model, dict)}
    for model in (EMBEDDING_MODEL, GENERATION_MODEL):
        if model not in existing:
            ollama.pull(model)
        else:
            print("Reusing local model " + model, flush=True)
    embedded = ollama.request("/api/embed", {"model": EMBEDDING_MODEL,
        "input": ["Local knowledge base readiness check."], "truncate": False})
    vectors = embedded.get("embeddings")
    if not isinstance(vectors, list) or len(vectors) != 1 or not isinstance(vectors[0], list) or len(vectors[0]) != 1024:
        raise ModelInitializationError("Embedding model did not produce exactly one 1024-dimensional vector.")
    vector = vectors[0]
    if any(isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) for value in vector):
        raise ModelInitializationError("Embedding readiness vector contains invalid or non-finite values.")
    if not any(value != 0 for value in vector):
        raise ModelInitializationError("Embedding readiness vector has zero norm.")
    generated = ollama.request("/api/chat", {"model": GENERATION_MODEL,
        "messages": [{"role": "user", "content": "Reply with the single word READY."}],
        "stream": False, "think": False,
        "options": {"temperature": 0, "num_predict": 16, "num_ctx": 8192}})
    message = generated.get("message")
    if generated.get("done") is not True or not isinstance(message, dict) or not isinstance(message.get("content"), str) or not message["content"].strip():
        raise ModelInitializationError("Generation model did not finish with a non-empty answer.")
    print("Models ready: finite 1024-dimensional embedding and completed local generation verified.", flush=True)


def main():
    if os.environ.get("SKIP_MODEL_INIT", "false").lower() == "true":
        print("Model initialization explicitly skipped for an isolated E2E override; this is not real-model readiness.", flush=True)
        return
    prepare(Ollama(os.environ.get("OLLAMA_URL", "http://ollama:11434")))


if __name__ == "__main__":
    try:
        main()
    except (ModelInitializationError, urllib.error.URLError, OSError, ValueError) as error:
        message = str(error) if isinstance(error, ModelInitializationError) else "Ollama request/download failed or timed out; inspect service logs and retry. Existing data was retained."
        raise SystemExit("models-init: " + message)
