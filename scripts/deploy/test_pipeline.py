"""Local-only regression tests: no VPS, SSH, systemd or user database is contacted."""
import copy
import io
import json
import os
import shutil
import subprocess
import tarfile
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
import urllib.error
import urllib.request
import zipfile
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path

import release
import server
import ui_server
import github_actions

ID = "20261109T170000Z-abcdef123456-12345678"
REPO = Path(__file__).resolve().parents[2]
TMP = REPO / ".deploy/test-tmp"

class Fixture(unittest.TestCase):
    def setUp(self):
        TMP.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(prefix="fixture-", dir=TMP)
        self.root = Path(self.temp.name).resolve()
    def tearDown(self):
        if not self.root.is_relative_to(TMP.resolve()): raise RuntimeError("Güvensiz test temizlik hedefi")
        self.temp.cleanup()
    def artifact(self, path=None, versions=(1, 2)):
        path = path or self.root / "artifact"
        path.mkdir(parents=True)
        with zipfile.ZipFile(path / "app.jar", "w") as archive:
            archive.writestr("BOOT-INF/classes/com/vetos/VetosApplication.class", b"fixture")
            for version in versions:
                archive.writestr(f"BOOT-INF/classes/db/migration/V{version}__test_{version}.sql", "-- Türkçe\nSELECT 1;\n")
        migrations = release.jar_migrations(path / "app.jar")
        release.write_json(path / "migrations.json", migrations)
        release.write_json(path / "source-files.json", {"backend/pom.xml": "fixture"})
        files = {"index.html": f'<meta name="api" content="{release.PUBLIC_URL}"><script src="/assets/new.js"></script>'.encode(),
                 "assets/new.js": b"new-code", "downloads/vetly-tarbil-eklenti.json": b'{"version":"0.3.1"}',
                 "downloads/vetly-tarbil-eklenti.zip": b"PK-zip-fixture"}
        with tarfile.open(path / "frontend.tar.gz", "w:gz") as archive:
            for name, content in files.items():
                entry = tarfile.TarInfo(name); entry.size = len(content); archive.addfile(entry, io.BytesIO(content))
        manifest = {"schemaVersion": 1, "releaseId": ID, "commit": "a" * 40, "preparedAt": "2026-11-09T17:00:00Z",
                    "dirty": [], "publicUrl": release.PUBLIC_URL, "extensionVersion": "0.3.1", "migrationCount": len(migrations),
                    "files": {name: release.digest(path / name) for name in release.PAYLOADS}}
        release.write_json(path / "release.json", manifest)
        return path
    def rehash(self, path, name):
        manifest = release.read_json(path / "release.json"); manifest["files"][name] = release.digest(path / name)
        release.write_json(path / "release.json", manifest)

class Artifacts(Fixture):
    def test_real_flyway_checksums_match_previously_verified_production_values(self):
        migrations = REPO / "backend/src/main/resources/db/migration"
        self.assertEqual(release.flyway_checksum((migrations / "V1__tenant_module.sql").read_bytes()), -2089161119)
        self.assertEqual(release.flyway_checksum((migrations / "V90__tur_adlari_turkce_karakter.sql").read_bytes()), 1039373221)
    def test_line_endings_and_bom_match_flyway_but_spaces_remain_significant(self):
        self.assertEqual(release.flyway_checksum(b"\xef\xbb\xbfA\r\nB\r\n"), release.flyway_checksum(b"A\nB\n"))
        self.assertNotEqual(release.flyway_checksum(b"A \nB"), release.flyway_checksum(b"A\nB"))
    def test_valid_package_is_verified_against_independent_manifest_digest(self):
        path = self.artifact()
        self.assertEqual(release.verify(path, release.digest(path / "release.json"))["releaseId"], ID)
        with self.assertRaises(release.ReleaseError): release.verify(path, "0" * 64)
    def test_modified_jar_fails_hash_validation(self):
        path = self.artifact(); (path / "app.jar").write_bytes(b"tampered")
        with self.assertRaisesRegex(release.ReleaseError, "SHA-256"): release.verify(path)
    def test_migration_catalog_cannot_lie_about_the_actual_jar(self):
        path = self.artifact(); data = release.read_json(path / "migrations.json"); data[0]["checksum"] += 1
        release.write_json(path / "migrations.json", data); self.rehash(path, "migrations.json")
        with self.assertRaisesRegex(release.ReleaseError, "gerçek JAR"): release.verify(path)
    def test_tar_traversal_symlinks_and_missing_extension_are_rejected(self):
        for name, kind in [("../outside", tarfile.REGTYPE), ("link", tarfile.SYMTYPE), ("index.html", tarfile.REGTYPE)]:
            path = self.root / ("bad-" + str(len(list(self.root.iterdir())))); path.mkdir()
            with tarfile.open(path / "frontend.tar.gz", "w:gz") as archive:
                entry = tarfile.TarInfo(name); entry.type = kind; entry.linkname = "/etc/passwd"
                archive.addfile(entry, io.BytesIO())
            with self.assertRaises(release.ReleaseError): release.frontend_members(path / "frontend.tar.gz")
    def test_wrong_production_target_is_rejected(self):
        path = self.artifact(); data = release.read_json(path / "release.json"); data["publicUrl"] = "http://localhost:8080"
        release.write_json(path / "release.json", data)
        with self.assertRaisesRegex(release.ReleaseError, "production"): release.verify(path)
    def test_snapshot_isolated_and_excludes_graph_secrets_and_outputs(self):
        repo = self.root / "repo"; repo.mkdir()
        contents = {"backend/pom.xml": "pom", "backend/mvnw.cmd": "wrapper", "frontend/package-lock.json": "{}", "extension/package-lock.json": "{}",
                    "frontend/src/app.ts": "source", "frontend/.env.production": "SECRET=never-copy", "extension/key.pem": "private",
                    "graphify-out/graph.json": "graph", "frontend/node_modules/dep.js": "build-output"}
        for name, value in contents.items():
            file = repo / name; file.parent.mkdir(parents=True, exist_ok=True); file.write_text(value)
        subprocess.run(["git", "init", "-q", str(repo)], check=True)
        subprocess.run(["git", "-C", str(repo), "add", "."], check=True)
        subprocess.run(["git", "-C", str(repo), "-c", "user.name=Test", "-c", "user.email=test@example.invalid", "commit", "-qm", "fixture"], check=True)
        metadata = release.snapshot(repo, self.root / "out", source_mode="working-tree")
        source = Path(metadata["source"])
        self.assertTrue((source / "frontend/src/app.ts").is_file())
        for bad in ["graphify-out/graph.json", "frontend/.env.production", "extension/key.pem", "frontend/node_modules/dep.js"]:
            self.assertFalse((source / bad).exists())
        (repo / "frontend/src/app.ts").write_text("changed")
        self.assertEqual((source / "frontend/src/app.ts").read_text(), "source")
        with self.assertRaisesRegex(release.ReleaseError, "commitlenmemiş"): release.snapshot(repo, self.root / "other", source_mode="working-tree")

    def test_default_commit_snapshot_excludes_staged_unstaged_and_new_files_without_touching_index(self):
        repo=self.root/"commit-repo";repo.mkdir()
        files={"backend/pom.xml":"pom","backend/mvnw.cmd":"wrapper","frontend/package-lock.json":"{}","extension/package-lock.json":"{}",
               "frontend/src/app.ts":"committed","backend/deleted.txt":"committed-deleted"}
        for name,data in files.items():
            p=repo/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(data)
        subprocess.run(["git","init","-q",str(repo)],check=True)
        subprocess.run(["git","-C",str(repo),"add","."],check=True)
        subprocess.run(["git","-C",str(repo),"-c","user.name=Test","-c","user.email=test@example.invalid","commit","-qm","fixture"],check=True)
        (repo/"frontend/src/app.ts").write_text("staged")
        (repo/"frontend/src/new-staged.ts").write_text("new-staged")
        subprocess.run(["git","-C",str(repo),"add","frontend"],check=True)
        (repo/"frontend/src/app.ts").write_text("unstaged")
        (repo/"frontend/src/untracked.ts").write_text("untracked")
        (repo/"backend/deleted.txt").unlink()
        index_before=(repo/".git/index").read_bytes()
        status_before=release.git(repo,"status","--porcelain","--untracked-files=all")
        metadata=release.snapshot(repo,self.root/"commit-out")
        source=Path(metadata["source"])
        self.assertEqual((source/"frontend/src/app.ts").read_text(),"committed")
        self.assertEqual((source/"backend/deleted.txt").read_text(),"committed-deleted")
        self.assertFalse((source/"frontend/src/new-staged.ts").exists())
        self.assertFalse((source/"frontend/src/untracked.ts").exists())
        self.assertEqual((repo/"frontend/src/app.ts").read_text(),"unstaged")
        self.assertFalse((repo/"backend/deleted.txt").exists())
        self.assertEqual((repo/".git/index").read_bytes(),index_before)
        self.assertEqual(release.git(repo,"status","--porcelain","--untracked-files=all"),status_before)
        self.assertEqual(metadata["sourceMode"],"commit");self.assertEqual(metadata["dirty"],[])
        self.assertGreater(metadata["ignoredLocalChangeCount"],0)


    def test_commit_pack_ignores_later_working_edits_but_rejects_snapshot_tampering(self):
        artifact=self.artifact()
        source=self.root/"build";source.mkdir()
        original=self.root/"working";original.mkdir()
        (source/"backend/target").mkdir(parents=True)
        shutil.copyfile(artifact/"app.jar",source/"backend/target/myvet-backend-0.1.0-SNAPSHOT.jar")
        (source/"backend/pom.xml").write_text("committed")
        (original/"backend").mkdir();(original/"backend/pom.xml").write_text("edited-locally")
        (source/"extension").mkdir();release.write_json(source/"extension/package.json",{"version":"0.3.1"})
        dist=source/"frontend/dist";dist.mkdir(parents=True)
        with tarfile.open(artifact/"frontend.tar.gz") as archive:
            archive.extractall(dist,filter="data")
        work=self.root/"snapshot";work.mkdir()
        release.write_json(work/"snapshot.json",{"releaseId":ID,"commit":"a"*40,"sourceMode":"commit","source":str(source),
            "repo":str(original),"sourceFiles":{"backend/pom.xml":release.digest(source/"backend/pom.xml")},
            "dirty":[],"ignoredLocalChangeCount":2,"preparedAt":"2026-11-09T17:00:00Z"})
        result=release.pack(work,self.root/"packed")
        manifest=release.verify(Path(result["path"]))
        self.assertEqual(manifest["sourceMode"],"commit");self.assertEqual(manifest["dirty"],[])
        self.assertEqual(manifest["ignoredLocalChangeCount"],2)
        self.assertEqual((original/"backend/pom.xml").read_text(),"edited-locally")
        (source/"backend/pom.xml").write_text("tampered")
        with self.assertRaisesRegex(release.ReleaseError,"İzole derleme kaynağı değişti"):
            release.pack(work,self.root/"tampered-output")


class FakeRuntime:
    def __init__(self, history, after=None, ready=(True,), backup_error=False, public_error=False):
        self.rows = copy.deepcopy(history); self.after = after; self.readiness = iter(ready)
        self.backup_error = backup_error; self.public_error = public_error
        self.restarts = self.backups = 0
    def history(self): return copy.deepcopy(self.rows)
    def reserve_space(self, size): pass
    def safe_web_directory(self, path): pass
    def preflight(self, pending, size): pass
    def backup_database(self, path):
        self.backups += 1
        if self.backup_error: raise release.ReleaseError("backup failed")
        path.write_bytes(b"verified-db-dump")
    def install_extensions(self, pending): pass
    def restart(self):
        self.restarts += 1
        if self.restarts == 1 and self.after is not None: self.rows = copy.deepcopy(self.after)
    def ready(self, timeout=180): return next(self.readiness)
    def set_app_owner(self, path): pass
    def public_checks(self, path, manifest):
        if self.public_error: raise release.ReleaseError("public checks failed")

class ServerFlow(Fixture):
    def setup_release(self, versions=(1, 2)):
        layout = server.Layout(incoming=self.root / "incoming", app=self.root / "app", web=self.root / "web", state=self.root / "server-state")
        layout.app.mkdir(); layout.web.mkdir(); (layout.web / "assets").mkdir()
        (layout.app / "app.jar").write_bytes(b"old-app")
        (layout.web / "index.html").write_text("old-index"); (layout.web / "assets/old.js").write_text("old-asset")
        incoming = self.artifact(layout.incoming / ID, versions)
        migrations = release.read_json(incoming / "migrations.json")
        history = [{**row, "success": True, "installed_rank": n + 1} for n, row in enumerate(migrations)]
        return layout, incoming, history
    def runner(self, layout, runtime): return server.Deployment(layout, runtime, notify=lambda *_: None)
    def test_successful_pipeline_backs_up_before_publish_and_retains_old_assets(self):
        layout, incoming, history = self.setup_release()
        runtime = FakeRuntime(history[:1], after=history)
        result = self.runner(layout, runtime).apply(ID, release.digest(incoming / "release.json"))
        self.assertEqual(result["status"], "succeeded"); self.assertEqual(runtime.backups, 1)
        self.assertEqual((layout.backups / ID / "app.jar").read_bytes(), b"old-app")
        self.assertTrue((layout.backups / ID / "database.dump").is_file())
        self.assertEqual((layout.web / "assets/old.js").read_text(), "old-asset")
        self.assertEqual((layout.web / "assets/new.js").read_bytes(), b"new-code")
    def test_checksum_mismatch_stops_before_backup_or_backend_changes(self):
        layout, incoming, history = self.setup_release(); history[0]["checksum"] += 1
        runtime = FakeRuntime(history)
        with self.assertRaisesRegex(release.ReleaseError, "checksum"):
            self.runner(layout, runtime).apply(ID, release.digest(incoming / "release.json"))
        self.assertEqual(runtime.backups, 0); self.assertEqual((layout.app / "app.jar").read_bytes(), b"old-app")
    def test_insufficient_staging_space_stops_before_root_package_copy(self):
        layout, incoming, history = self.setup_release()
        runtime = FakeRuntime(history)
        def no_space(size): raise release.ReleaseError("disk full")
        runtime.reserve_space = no_space
        with self.assertRaisesRegex(release.ReleaseError, "disk full"):
            self.runner(layout, runtime).apply(ID, release.digest(incoming / "release.json"))
        self.assertFalse((layout.releases / ID).exists())
        self.assertEqual(runtime.backups, 0); self.assertEqual(runtime.restarts, 0)
    def test_database_backup_failure_prevents_restart(self):
        layout, incoming, history = self.setup_release()
        runtime = FakeRuntime(history, backup_error=True)
        result = self.runner(layout, runtime).apply(ID, release.digest(incoming / "release.json"))
        self.assertEqual(result["status"], "failed"); self.assertEqual(runtime.restarts, 0)
        self.assertEqual((layout.web / "index.html").read_text(), "old-index")
    def test_backend_failure_without_migration_restores_previous_jar(self):
        layout, incoming, history = self.setup_release()
        runtime = FakeRuntime(history, ready=(False, True))
        result = self.runner(layout, runtime).apply(ID, release.digest(incoming / "release.json"))
        self.assertTrue(result["backendRestored"]); self.assertEqual(result["status"], "failed")
        self.assertEqual((layout.app / "app.jar").read_bytes(), b"old-app")
        self.assertEqual((layout.web / "index.html").read_text(), "old-index")
    def test_failure_after_schema_change_never_restores_old_jar_or_database(self):
        layout, incoming, history = self.setup_release()
        runtime = FakeRuntime(history[:1], after=history, ready=(False,))
        result = self.runner(layout, runtime).apply(ID, release.digest(incoming / "release.json"))
        self.assertTrue(result["manualRecoveryRequired"]); self.assertFalse(result["backendRestored"])
        self.assertNotEqual((layout.app / "app.jar").read_bytes(), b"old-app")
        self.assertEqual((layout.web / "index.html").read_text(), "old-index")
        self.assertEqual(runtime.restarts, 1)
    def test_public_failure_restores_old_frontend_and_backend_when_schema_unchanged(self):
        layout, incoming, history = self.setup_release()
        runtime = FakeRuntime(history, ready=(True, True), public_error=True)
        result = self.runner(layout, runtime).apply(ID, release.digest(incoming / "release.json"))
        self.assertTrue(result["frontendRestored"]); self.assertTrue(result["backendRestored"])
        self.assertEqual((layout.web / "index.html").read_text(), "old-index")
    def test_readiness_up_is_not_enough_when_expected_migrations_are_missing(self):
        layout, incoming, history = self.setup_release()
        runtime = FakeRuntime(history[:1], ready=(True, True))
        result = self.runner(layout, runtime).apply(ID, release.digest(incoming / "release.json"))
        self.assertEqual(result["status"], "failed"); self.assertFalse(result["backendRestored"])
        self.assertTrue(result["manualRecoveryRequired"])
        self.assertIn("migration", result["error"])
    def test_public_failure_after_migration_restores_only_frontend(self):
        layout, incoming, history = self.setup_release()
        runtime = FakeRuntime(history[:1], after=history, public_error=True)
        result = self.runner(layout, runtime).apply(ID, release.digest(incoming / "release.json"))
        self.assertTrue(result["frontendRestored"]); self.assertFalse(result["backendRestored"])
        self.assertTrue(result["manualRecoveryRequired"])
        self.assertEqual((layout.web / "index.html").read_text(), "old-index")
        self.assertNotEqual((layout.app / "app.jar").read_bytes(), b"old-app")
    def test_schema_change_during_backup_stops_before_backend_installation(self):
        layout, incoming, history = self.setup_release()
        runtime = FakeRuntime(history[:1])
        original = runtime.backup_database
        def concurrent_change(path):
            original(path); runtime.rows = copy.deepcopy(history)
        runtime.backup_database = concurrent_change
        result = self.runner(layout, runtime).apply(ID, release.digest(incoming / "release.json"))
        self.assertEqual(result["status"], "failed"); self.assertEqual(runtime.restarts, 0)
        self.assertEqual((layout.app / "app.jar").read_bytes(), b"old-app")
    def test_incomplete_server_snapshot_is_not_reused(self):
        layout, incoming, history = self.setup_release()
        partial = layout.releases / ID; partial.mkdir(parents=True)
        for name in (*release.PAYLOADS, "release.json"): shutil.copyfile(incoming / name, partial / name)
        runtime = FakeRuntime(history)
        with self.assertRaisesRegex(release.ReleaseError, "Eksik sunucu paketi"):
            self.runner(layout, runtime).apply(ID, release.digest(incoming / "release.json"))
        self.assertEqual(runtime.backups, 0)
    def test_closed_progress_stream_does_not_trigger_deployment_rollback(self):
        layout, incoming, history = self.setup_release()
        runtime = FakeRuntime(history)
        with patch("builtins.print", side_effect=BrokenPipeError):
            result = server.Deployment(layout, runtime, notify=server.event).apply(ID, release.digest(incoming / "release.json"))
        self.assertEqual(result["status"], "succeeded")
        self.assertFalse(result["backendRestored"])
    def test_same_release_cannot_be_applied_twice(self):
        layout, incoming, history = self.setup_release()
        runtime = FakeRuntime(history)
        runner = self.runner(layout, runtime); runner.apply(ID, release.digest(incoming / "release.json"))
        with self.assertRaisesRegex(release.ReleaseError, "daha önce"):
            runner.apply(ID, release.digest(incoming / "release.json"))
        self.assertEqual(runtime.backups, 1)
    def test_future_unknown_and_failed_live_migrations_are_rejected(self):
        migrations = [{"version": "1", "script": "V1__x.sql", "checksum": 1}]
        for row in [{"version": "2", "script": "V2__x.sql", "checksum": 2, "success": True}, {**migrations[0], "success": False}]:
            with self.assertRaises(release.ReleaseError): server.compare_history([row], migrations)

class DashboardSecurity(Fixture):
    def setUp(self):
        super().setUp()
        (self.root / "scripts").mkdir()
        (self.root / "scripts/deploy.ps1").write_text("fixture")
        self.calls = []
        def executor(args, line):
            self.calls.append(args)
            line('@@VETLY_EVENT@@' + json.dumps({"stage": "package", "status": "ok", "message": "Paket hazır"}))
            line('@@VETLY_RESULT@@' + json.dumps({"status": "prepared", "releaseId": ID}))
            return 0
        self.board = ui_server.Dashboard(self.root, "powershell-test", executor)
        self.http = ThreadingHTTPServer(("127.0.0.1", 0), BaseHTTPRequestHandler)
        self.port = self.http.server_address[1]
        self.http.RequestHandlerClass = ui_server.make_handler(self.board, self.port)
        self.thread = threading.Thread(target=self.http.serve_forever, daemon=True); self.thread.start()
    def tearDown(self):
        self.http.shutdown(); self.http.server_close(); self.thread.join(2)
        for _ in range(200):
            if not self.board.job or self.board.job["status"] != "running": break
            time.sleep(.01)
        super().tearDown()
    def request(self, path="/api/state", method="GET", body=None, token=True, origin=None, host=None):
        headers = {"Content-Type": "application/json"}
        if token: headers["X-Deploy-Token"] = self.board.token
        if origin is not None: headers["Origin"] = origin
        elif method == "POST": headers["Origin"] = f"http://127.0.0.1:{self.port}"
        if host: headers["Host"] = host
        req = urllib.request.Request(f"http://127.0.0.1:{self.port}" + path, method=method, data=json.dumps(body).encode() if body is not None else None, headers=headers)
        try:
            with urllib.request.urlopen(req) as response: return response.status, response.read(), response.headers
        except urllib.error.HTTPError as exc: return exc.code, exc.read(), exc.headers
    def test_state_requires_token_and_has_no_cors_access(self):
        status, _, headers = self.request(token=False)
        self.assertEqual(status, 403); self.assertIsNone(headers.get("Access-Control-Allow-Origin"))
        self.assertEqual(self.request()[0], 200)
    def test_foreign_origin_and_dns_rebinding_host_cannot_launch_commands(self):
        self.assertEqual(self.request("/api/run", "POST", {"mode": "Prepare"}, origin="https://evil.invalid")[0], 403)
        self.assertEqual(self.request("/api/run", "POST", {"mode": "Prepare"}, host=f"evil.invalid:{self.port}")[0], 403)
        self.assertEqual(self.calls, [])
    def test_arbitrary_command_and_release_traversal_are_rejected(self):
        for body in [{"mode": "Prepare", "command": "arbitrary"}, {"mode": "Deploy", "releaseId": "../../etc"}, {"mode": "Execute"}, {"mode": "Prepare", "allowDirty": "yes"}, {"mode": "Deploy", "releaseId": []}, {"mode":"Prepare","sourceMode":"arbitrary"}]:
            self.assertEqual(self.request("/api/run", "POST", body)[0], 400)
        self.assertEqual(self.request("/../../backend/.env")[0], 404)
        self.assertEqual(self.calls, [])
    def test_allowed_prepare_uses_only_fixed_powershell_script_and_arguments(self):
        self.assertEqual(self.request("/api/run", "POST", {"mode": "Prepare"})[0], 202)
        for _ in range(200):
            if self.board.job["status"] != "running": break
            time.sleep(.01)
        self.assertEqual(self.board.job["status"], "succeeded")
        self.assertIn(str(self.root / "scripts/deploy.ps1"), self.calls[0]); self.assertIn("Prepare", self.calls[0])
        self.assertEqual(self.calls[0][self.calls[0].index("-SourceMode")+1],"commit")
        self.assertTrue(list(self.board.history.glob("*.json")))
    def test_second_pipeline_is_rejected_until_first_completes(self):
        started, finish = threading.Event(), threading.Event()
        def executor(args, line): started.set(); finish.wait(3); return 1
        self.board.executor = executor
        self.board.start({"mode": "Prepare"}); self.assertTrue(started.wait(1))
        try:
            with self.assertRaises(RuntimeError): self.board.start({"mode": "Prepare"})
        finally: finish.set()
    def test_panel_cannot_shutdown_while_a_pipeline_is_running(self):
        started, finish = threading.Event(), threading.Event()
        def executor(args, line): started.set(); finish.wait(3); return 1
        self.board.executor = executor
        self.board.start({"mode": "Prepare"}); self.assertTrue(started.wait(1))
        try:
            self.assertEqual(self.request("/api/shutdown", "POST", {})[0], 409)
            self.assertFalse(self.board.closing)
        finally: finish.set()
    def test_log_redacts_obvious_secrets(self):
        self.assertEqual(ui_server.redact("password=do-not-show api_key=also-secret"), "password=[gizli] api_key=[gizli]")


class GitHubActions(Fixture):
    def committed_package(self, output=None):
        path=self.artifact((output or self.root)/ID)
        manifest=release.read_json(path/"release.json");manifest["sourceMode"]="commit"
        release.write_json(path/"release.json",manifest)
        return path

    def test_same_commit_and_manifest_are_required_for_promotion(self):
        path=self.committed_package()
        sha=release.digest(path/"release.json")
        self.assertEqual(github_actions.checked_package(path,"a"*40,sha)["releaseId"],ID)
        with self.assertRaisesRegex(release.ReleaseError,"commit'iyle uyuşmuyor"):
            github_actions.checked_package(path,"b"*40,sha)
        with self.assertRaisesRegex(release.ReleaseError,"SHA-256"):
            github_actions.checked_package(path,"a"*40,"0"*64)
        for mode,dirty in [("working-tree",[]),("commit",[" M frontend/app.ts"])]:
            manifest=release.read_json(path/"release.json");manifest.update(sourceMode=mode,dirty=dirty)
            release.write_json(path/"release.json",manifest)
            with self.assertRaises(release.ReleaseError):github_actions.checked_package(path,"a"*40)

    def test_package_outputs_bind_id_hash_and_commit_without_secrets(self):
        repo=self.root/"repo";output=repo/".deploy/releases"
        path=self.committed_package(output)
        out=self.root/"outputs";summary=self.root/"summary"
        with patch.dict(os.environ,{"GITHUB_OUTPUT":str(out),"GITHUB_SHA":"a"*40,"GITHUB_STEP_SUMMARY":str(summary)}):
            github_actions.export_package(repo)
        self.assertIn("release_id="+ID,out.read_text())
        self.assertIn("manifest_sha256="+release.digest(path/"release.json"),out.read_text())
        with patch.dict(os.environ,{"GITHUB_SHA":"a"*40,"VETLY_RELEASE_ID":ID,"VETLY_MANIFEST_SHA256":release.digest(path/"release.json")}):
            github_actions.verify_download(repo)
        (output/"extra").mkdir()
        with self.assertRaisesRegex(release.ReleaseError,"tam bir paket"):
            github_actions.export_package(repo)

    def test_untrusted_release_ids_hashes_and_output_newlines_are_rejected(self):
        for release_id,sha in [("../outside","a"*64),(ID,"invalid")]:
            with patch.dict(os.environ,{"VETLY_RELEASE_ID":release_id,"VETLY_MANIFEST_SHA256":sha}):
                with self.assertRaises(release.ReleaseError):github_actions.verify_download(self.root)
        output=self.root/"outputs"
        with patch.dict(os.environ,{"GITHUB_OUTPUT":str(output)}):
            with self.assertRaises(release.ReleaseError):github_actions.append_environment("GITHUB_OUTPUT",{"x":"value\nother=bad"})
        self.assertFalse(output.exists())

    def test_ssh_cleanup_rejects_paths_outside_runner_temp(self):
        outside=self.root/"outside";outside.mkdir()
        with patch.dict(os.environ,{"RUNNER_TEMP":str(self.root/"runner")}):
            with self.assertRaises(release.ReleaseError):github_actions.remove_ssh(outside)
        self.assertTrue(outside.exists())

    @unittest.skipUnless(shutil.which("ssh-keygen") and shutil.which("ssh"),"OpenSSH gerekli")
    def test_isolated_ssh_configuration_validates_identity_and_cleans_up(self):
        key=self.root/"fixture-key"
        subprocess.run(["ssh-keygen","-q","-t","ed25519","-N","","-f",str(key)],check=True,capture_output=True)
        private=key.read_text();public=(self.root/"fixture-key.pub").read_text().strip()
        output=self.root/"environment"
        env={"RUNNER_TEMP":str(self.root),"GITHUB_ENV":str(output),"VETLY_SSH_PRIVATE_KEY":private,
             "VETLY_SSH_KNOWN_HOSTS":"70.40.139.40 "+public}
        with patch.dict(os.environ,env):
            github_actions.configure_ssh()
            values=dict(line.split("=",1) for line in output.read_text().splitlines())
            config=Path(values["VETLY_SSH_CONFIG"])
            result=subprocess.run(["ssh","-G","-F",str(config),"70.40.139.40"],capture_output=True,text=True,check=True).stdout.lower()
            for option in ["stricthostkeychecking true","identitiesonly yes","batchmode yes","passwordauthentication no"]:
                self.assertIn(option,result)
            self.assertNotIn(private,output.read_text())
            github_actions.remove_ssh(Path(values["VETLY_SSH_DIR"]))
            self.assertFalse(config.parent.exists())
            os.environ["VETLY_SSH_KNOWN_HOSTS"]="wrong.invalid "+public
            with self.assertRaisesRegex(release.ReleaseError,"VETLY_SSH_KNOWN_HOSTS"):github_actions.configure_ssh()
            self.assertEqual(list(self.root.glob("vetly-ssh-*")),[])


    @unittest.skipUnless(shutil.which("ssh-keygen"),"OpenSSH gerekli")
    def test_encrypted_private_key_reports_its_secret_name_and_cleans_up(self):
        key=self.root/"encrypted-key"
        subprocess.run(["ssh-keygen","-q","-t","ed25519","-N","fixture-only-password","-f",str(key)],check=True,capture_output=True)
        output=self.root/"environment"
        with patch.dict(os.environ,{"RUNNER_TEMP":str(self.root),"GITHUB_ENV":str(output),
             "VETLY_SSH_PRIVATE_KEY":key.read_text(),"VETLY_SSH_KNOWN_HOSTS":"70.40.139.40 ssh-ed25519 invalid"}):
            with self.assertRaisesRegex(release.ReleaseError,"VETLY_SSH_PRIVATE_KEY.*parolal") as error:
                github_actions.configure_ssh()
        self.assertNotIn("fixture-only-password",str(error.exception))
        self.assertNotIn(key.read_text(),str(error.exception))
        self.assertFalse(output.exists());self.assertEqual(list(self.root.glob("vetly-ssh-*")),[])

    @unittest.skipUnless(shutil.which("ssh-keygen"),"OpenSSH gerekli")
    def test_malformed_private_key_reports_only_safe_instructions(self):
        private="-----BEGIN OPENSSH PRIVATE KEY-----\nfixture-sensitive-content\n-----END OPENSSH PRIVATE KEY-----"
        with patch.dict(os.environ,{"RUNNER_TEMP":str(self.root),"GITHUB_ENV":str(self.root/"environment"),
             "VETLY_SSH_PRIVATE_KEY":private,"VETLY_SSH_KNOWN_HOSTS":"70.40.139.40 ssh-ed25519 invalid"}):
            with self.assertRaisesRegex(release.ReleaseError,"VETLY_SSH_PRIVATE_KEY") as error:github_actions.configure_ssh()
        self.assertNotIn("fixture-sensitive-content",str(error.exception))
        self.assertEqual(list(self.root.glob("vetly-ssh-*")),[])


    def test_private_key_acl_error_is_distinguished_without_raw_stderr(self):
        real_run=subprocess.run
        def run(args,**kwargs):
            if args[:2]==["ssh-keygen","-y"]:
                return subprocess.CompletedProcess(args,255,stdout=b"",stderr=b"bad permissions fixture-sensitive-detail")
            return real_run(args,**kwargs)
        with patch.dict(os.environ,{"RUNNER_TEMP":str(self.root),"GITHUB_ENV":str(self.root/"environment"),
             "VETLY_SSH_PRIVATE_KEY":"-----BEGIN OPENSSH PRIVATE KEY-----\nfixture\n-----END OPENSSH PRIVATE KEY-----",
             "VETLY_SSH_KNOWN_HOSTS":"70.40.139.40 ssh-ed25519 fixture"}),patch.object(github_actions.subprocess,"run",side_effect=run):
            with self.assertRaisesRegex(release.ReleaseError,"VETLY_SSH_PRIVATE_KEY.*izin") as error:github_actions.configure_ssh()
        self.assertNotIn("fixture-sensitive-detail",str(error.exception))
        self.assertEqual(list(self.root.glob("vetly-ssh-*")),[])


if __name__ == "__main__": unittest.main()
