"""Loopback-only deployment dashboard. Executes fixed PowerShell modes, not arbitrary input."""
from __future__ import annotations
import argparse
import hashlib
import hmac
import json
import os
import re
import secrets
import subprocess
import sys
import threading
import uuid
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parent))
from release import RELEASE_ID, read_json, write_json

STAGES = ("source", "build", "package", "connect", "preflight", "backup", "backend", "frontend", "smoke")
MODES = {"Prepare", "Check", "Deploy"}

def now(): return datetime.now(timezone.utc).isoformat()

def redact(line):
    return re.sub(r"(?i)((?:api[_-]?key|secret|password|token)\s*[=:]\s*)[^\s,;]+", r"\1[gizli]", line)[:4096]

class Dashboard:
    def __init__(self, repo: Path, powershell: str, executor=None):
        self.repo = repo.resolve()
        self.repo_id = hashlib.sha256(str(self.repo).lower().encode("utf-8")).hexdigest()
        self.powershell = powershell
        self.token = secrets.token_urlsafe(32)
        self.executor = executor or self.execute
        self.lock = threading.RLock()
        self.job = None
        self.closing = False
        self.history = self.repo / ".deploy/ui/history"
        self.history.mkdir(parents=True, exist_ok=True)

    def releases(self):
        root = self.repo / ".deploy/releases"
        result = []
        if not root.is_dir(): return result
        for directory in sorted(root.iterdir(), reverse=True):
            if directory.is_symlink() or not directory.is_dir() or not RELEASE_ID.fullmatch(directory.name): continue
            try:
                data = read_json(directory / "release.json")
                if data.get("releaseId") != directory.name or data.get("schemaVersion") != 1: continue
                result.append({"releaseId": directory.name, "commit": data.get("commit"), "preparedAt": data.get("preparedAt"),
                               "migrationCount": data.get("migrationCount"), "dirtyCount": len(data.get("dirty", [])),
                               "extensionVersion": data.get("extensionVersion"), "sourceMode": data.get("sourceMode", "working-tree"),
                               "ignoredLocalChangeCount": data.get("ignoredLocalChangeCount", 0)})
            except (OSError, ValueError): continue
            if len(result) == 20: break
        return result

    def state(self):
        with self.lock:
            # JSON copy prevents concurrent mutations during response encoding.
            return json.loads(json.dumps({"job": self.job, "releases": self.releases(), "target": "uygulama.vetly.com.tr"}))

    def persist(self):
        path = self.history / (self.job["id"] + ".json")
        temporary = path.with_suffix(".tmp")
        write_json(temporary, self.job)
        os.replace(temporary, path)

    def start(self, body):
        if not isinstance(body, dict) or set(body) - {"mode", "releaseId", "allowDirty", "sourceMode"}: raise ValueError("İstek alanları geçersiz")
        mode = body.get("mode")
        source_mode = body.get("sourceMode", "commit")
        if source_mode not in {"commit", "working-tree"}: raise ValueError("Kaynak seçimi geçersiz")
        raw_id = body.get("releaseId")
        if raw_id is not None and not isinstance(raw_id, str): raise ValueError("Paket kimliği metin olmalı")
        release_id = raw_id or None
        if mode not in MODES or type(body.get("allowDirty", False)) is not bool: raise ValueError("Pipeline seçimi geçersiz")
        if release_id and (not isinstance(release_id, str) or not RELEASE_ID.fullmatch(release_id)): raise ValueError("Paket kimliği geçersiz")
        if mode == "Check" and not release_id: raise ValueError("Kontrol için hazır paket seçin")
        if release_id:
            path = self.repo / ".deploy/releases" / release_id
            if path.is_symlink() or not (path / "release.json").is_file(): raise ValueError("Hazır paket bulunamadı")
        with self.lock:
            if self.closing: raise RuntimeError("Panel kapanıyor")
            if self.job and self.job["status"] == "running": raise RuntimeError("Bir pipeline zaten çalışıyor")
            self.job = {"id": uuid.uuid4().hex, "mode": mode, "releaseId": release_id, "status": "running", "startedAt": now(),
                        "sourceMode": source_mode, "finishedAt": None, "stages": {}, "logs": [], "result": None}
            self.persist()
            thread = threading.Thread(target=self.run, args=(mode, release_id, body.get("allowDirty", False), source_mode), daemon=True)
            thread.start()
            return self.job["id"]

    def line(self, text):
        text = text.rstrip("\r\n")
        with self.lock:
            if text.startswith("@@VETLY_EVENT@@"):
                try:
                    event = json.loads(text[len("@@VETLY_EVENT@@"):])
                    if event.get("stage") in STAGES and event.get("status") in {"start", "ok", "skip", "error"}:
                        self.job["stages"][event["stage"]] = {"status": event["status"], "message": redact(str(event.get("message", "")))}
                except (ValueError, TypeError): pass
            elif text.startswith("@@VETLY_RESULT@@"):
                try:
                    result = json.loads(text[len("@@VETLY_RESULT@@"):])
                    self.job["result"] = result
                    self.job["releaseId"] = result.get("releaseId")
                except (ValueError, TypeError): pass
            if text:
                self.job["logs"].append({"at": now(), "text": redact(text)})
                self.job["logs"] = self.job["logs"][-600:]
                with (self.history / (self.job["id"] + ".log")).open("a", encoding="utf-8") as output:
                    output.write(redact(text) + "\n")
            self.persist()

    def execute(self, args, on_line):
        env = dict(os.environ, PYTHONIOENCODING="utf-8", PYTHONUTF8="1")
        flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        process = subprocess.Popen(args, cwd=self.repo, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, encoding="utf-8", errors="replace", env=env, creationflags=flags)
        for line in process.stdout: on_line(line)
        return process.wait()

    def run(self, mode, release_id, allow_dirty, source_mode="commit"):
        args = [self.powershell, "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
                str(self.repo / "scripts/deploy.ps1"), "-Mode", mode, "-PythonExecutable", sys.executable, "-SourceMode", source_mode]
        if release_id: args += ["-ReleaseId", release_id]
        if allow_dirty: args += ["-AllowDirty"]
        try:
            code = self.executor(args, self.line)
            with self.lock:
                expected = {"Prepare": "prepared", "Check": "checked", "Deploy": "succeeded"}[mode]
                success = code == 0 and (self.job["result"] or {}).get("status") == expected
                self.job["status"] = "succeeded" if success else "failed"
                self.job["exitCode"] = code
        except Exception as exc:
            self.line("Pipeline başlatılamadı: " + str(exc))
            with self.lock: self.job["status"] = "failed"
        finally:
            with self.lock:
                self.job["finishedAt"] = now()
                self.persist()


def make_handler(dashboard: Dashboard, port: int):
    origins = {f"http://127.0.0.1:{port}", f"http://localhost:{port}"}
    hosts = {f"127.0.0.1:{port}", f"localhost:{port}"}
    assets = Path(__file__).resolve().parent / "ui"
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_): pass
        def headers_for(self, status, content_type):
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("X-Frame-Options", "DENY")
            self.send_header("Referrer-Policy", "no-referrer")
            self.send_header("Cross-Origin-Opener-Policy", "same-origin")
            self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")
            self.end_headers()
        def json(self, status, value):
            self.headers_for(status, "application/json; charset=utf-8")
            self.wfile.write(json.dumps(value, ensure_ascii=False).encode("utf-8"))
        def host_ok(self):
            return self.headers.get("Host") in hosts
        def authorized(self, post=False):
            if not self.host_ok(): return False
            origin = self.headers.get("Origin")
            if origin is not None and origin not in origins: return False
            if post and origin not in origins: return False
            try: return hmac.compare_digest(self.headers.get("X-Deploy-Token", ""), dashboard.token)
            except TypeError: return False
        def do_GET(self):
            if not self.host_ok(): self.json(403, {"error": "Host reddedildi"}); return
            path = urlsplit(self.path).path
            if path == "/api/health": self.json(200, {"app": "vetly-deploy-ui", "repoId": dashboard.repo_id}); return
            if path.startswith("/api/"):
                if not self.authorized(): self.json(403, {"error": "Oturum doğrulanamadı"}); return
                if path == "/api/state": self.json(200, dashboard.state()); return
                self.json(404, {"error": "Kaynak yok"}); return
            mapping = {"/": ("index.html", "text/html; charset=utf-8"), "/app.js": ("app.js", "text/javascript; charset=utf-8"), "/style.css": ("style.css", "text/css; charset=utf-8")}
            if path not in mapping: self.json(404, {"error": "Kaynak yok"}); return
            file, content_type = mapping[path]
            content = (assets / file).read_text(encoding="utf-8")
            if path == "/": content = content.replace("__DEPLOY_TOKEN__", dashboard.token)
            self.headers_for(200, content_type); self.wfile.write(content.encode("utf-8"))
        def do_POST(self):
            if not self.authorized(post=True): self.json(403, {"error": "Oturum veya Origin doğrulanamadı"}); return
            if urlsplit(self.path).path == "/api/shutdown":
                with dashboard.lock:
                    if dashboard.job and dashboard.job["status"] == "running":
                        self.json(409, {"error": "Pipeline çalışırken panel kapatılamaz"}); return
                    dashboard.closing = True
                self.json(200, {"closed": True})
                threading.Thread(target=self.server.shutdown, daemon=True).start()
                return
            if urlsplit(self.path).path != "/api/run": self.json(404, {"error": "Kaynak yok"}); return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 16384 or self.headers.get("Content-Type", "").split(";")[0] != "application/json": raise ValueError("JSON isteği gerekli")
                body = json.loads(self.rfile.read(length))
                job_id = dashboard.start(body)
                self.json(202, {"jobId": job_id})
            except RuntimeError as exc: self.json(409, {"error": str(exc)})
            except (ValueError, TypeError) as exc: self.json(400, {"error": str(exc)})
    return Handler


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--powershell", required=True)
    parser.add_argument("--port", type=int, default=9177)
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535 or not (args.repo / "scripts/deploy.ps1").is_file():
        parser.error("Geçerli port ve proje yolu gerekiyor")
    board = Dashboard(args.repo, args.powershell)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), make_handler(board, args.port))
    try: server.serve_forever()
    finally: server.server_close()

if __name__ == "__main__": main()
