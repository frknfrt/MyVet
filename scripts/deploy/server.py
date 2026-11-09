#!/usr/bin/python3 -I
"""Root-owned, fixed-target VPS deployment helper; never accepts shell commands."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import shutil
import stat
import subprocess
import sys
import tarfile
import time
import urllib.request
from contextlib import contextmanager
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path, PurePosixPath

def require_trusted_installation():
    if not hasattr(os, "geteuid") or os.geteuid() != 0:
        raise RuntimeError("Sunucu yardımcısı Linux üzerinde sudo ile çalıştırılmalıdır")
    directory = Path(__file__).resolve().parent
    if directory != Path("/usr/local/lib/vetly-deploy"):
        raise RuntimeError("Yardımcı /usr/local/lib/vetly-deploy altında kurulmalıdır")
    for path in [directory / "server.py", directory / "release.py", directory, *directory.parents]:
        info = path.stat()
        if info.st_uid != 0 or info.st_mode & 0o022:
            raise RuntimeError("Deploy yardımcı kodu ve üst dizinleri root'a ait, diğer kullanıcılara yazmaya kapalı olmalıdır")

if __name__ == "__main__":
    try: require_trusted_installation()
    except Exception as exc:
        print(str(exc), file=sys.stderr); sys.exit(1)

sys.path.insert(0, str(Path(__file__).resolve().parent))
from release import PAYLOADS, PUBLIC_URL, RELEASE_ID, ReleaseError, digest, frontend_members, read_json, verify, write_json


def event(stage: str, status: str, message: str):
    try:
        print("@@VETLY_EVENT@@" + json.dumps({"stage": stage, "status": status, "message": message}, ensure_ascii=False), flush=True)
    except (BrokenPipeError, OSError):
        # Telemetry loss must not trigger rollback of an otherwise healthy deployment.
        pass

@dataclass(frozen=True)
class Layout:
    incoming: Path = Path("/home/furkan/vetly-deploy/incoming")
    app: Path = Path("/opt/vetly")
    web: Path = Path("/var/www/vetly")
    state: Path = Path("/var/lib/vetly-deploy")
    service: str = "vetly-backend"
    database: str = "vetly"
    @property
    def releases(self): return self.state / "releases"
    @property
    def backups(self): return self.state / "backups"

class Runtime:
    def __init__(self, layout: Layout):
        self.layout = layout
        self.http = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def command(self, args: list[str], timeout=90, stdout=None) -> str:
        result = subprocess.run(args, stdout=stdout if stdout else subprocess.PIPE, stderr=subprocess.PIPE,
                                timeout=timeout, env={"PATH": "/usr/sbin:/usr/bin:/sbin:/bin", "LANG": "C.UTF-8"})
        if result.returncode:
            raise ReleaseError("Sunucu komutu başarısız: " + Path(args[0]).name + ": " + result.stderr.decode("utf-8", "replace")[-2000:])
        return "" if stdout else result.stdout.decode("utf-8")

    def sql(self, query: str) -> str:
        return self.command(["/usr/sbin/runuser", "-u", "postgres", "--", "psql", "-X", "--no-password", "-v", "ON_ERROR_STOP=1", "-d", self.layout.database, "-t", "-A", "-c", query]).strip()

    def history(self) -> list[dict]:
        return json.loads(self.sql("SELECT COALESCE(json_agg(x ORDER BY installed_rank),'[]'::json) FROM (SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history) x"))

    def reserve_space(self, required_bytes: int):
        if shutil.disk_usage(self.layout.state).free < required_bytes:
            raise ReleaseError("Paket kopyası/açılımı için boş disk alanı yetersiz")

    def safe_web_directory(self, path: Path):
        info = path.stat()
        if path.is_symlink() or info.st_uid != 0 or info.st_mode & 0o022:
            raise ReleaseError("Web yayın dizinleri root'a ait ve diğer kullanıcılara yazmaya kapalı olmalıdır")

    def preflight(self, pending: list[str], required_bytes: int):
        self.safe_web_directory(self.layout.web)
        web_backup_size = sum(p.stat().st_size for p in self.layout.web.rglob("*") if p.is_file() and not p.is_symlink())
        old_jar_size = (self.layout.app / "app.jar").stat().st_size
        required_bytes += web_backup_size + old_jar_size + int(self.sql("SELECT pg_database_size(current_database())")) * 2 + 100 * 1024**2
        self.reserve_space(required_bytes)
        if shutil.disk_usage(self.layout.app).free < old_jar_size * 2 + 50 * 1024**2:
            raise ReleaseError("Backend atomik değişimi için boş alan yetersiz")
        start = self.command(["/usr/bin/systemctl", "show", self.layout.service, "-p", "ExecStart", "--value"])
        service_user = self.command(["/usr/bin/systemctl", "show", self.layout.service, "-p", "User", "--value"]).strip()
        if service_user != "vetly":
            raise ReleaseError("Backend systemd kullanıcısı vetly olmalıdır")
        if "/opt/vetly/app.jar" not in start:
            raise ReleaseError("systemd ExecStart beklenen /opt/vetly/app.jar yolunu kullanmıyor")
        if "96" in pending:
            if self.sql("SELECT COUNT(*) FROM pg_available_extensions WHERE name='btree_gist'") != "1":
                raise ReleaseError("btree_gist sunucuda kullanılabilir değil")
            invalid = self.sql("SELECT COUNT(*) FROM appointments WHERE assigned_staff_id IS NOT NULL AND status IN ('CONFIRMED','CHECKED_IN','IN_PROGRESS') AND scheduled_end < scheduled_start")
            if invalid != "0":
                raise ReleaseError("V96 öncesi geçersiz aktif randevu aralığı var")
            overlap = self.sql("SELECT COUNT(*) FROM appointments a JOIN appointments b ON a.tenant_id=b.tenant_id AND a.assigned_staff_id=b.assigned_staff_id AND a.id<b.id AND a.scheduled_start<b.scheduled_end AND b.scheduled_start<a.scheduled_end WHERE a.assigned_staff_id IS NOT NULL AND a.status IN ('CONFIRMED','CHECKED_IN','IN_PROGRESS') AND b.status IN ('CONFIRMED','CHECKED_IN','IN_PROGRESS') AND a.scheduled_end>a.scheduled_start AND b.scheduled_end>b.scheduled_start")
            if overlap != "0":
                raise ReleaseError("V96 öncesi çakışan aktif hekim randevuları var")

    def backup_database(self, path: Path):
        with path.open("xb") as stream:
            os.chmod(path, 0o600)
            self.command(["/usr/sbin/runuser", "-u", "postgres", "--", "pg_dump", "--no-password", "-Fc", "-d", self.layout.database], timeout=600, stdout=stream)
        if path.stat().st_size == 0:
            raise ReleaseError("Veritabanı yedeği boş")
        self.command(["/usr/bin/pg_restore", "--list", str(path)])

    def install_extensions(self, pending: list[str]):
        if "96" in pending:
            self.sql("CREATE EXTENSION IF NOT EXISTS btree_gist")

    def restart(self):
        self.command(["/usr/bin/systemctl", "restart", self.layout.service])

    def ready(self, timeout=180) -> bool:
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            try:
                with self.http.open("http://127.0.0.1:8081/actuator/health/readiness", timeout=4) as response:
                    if response.status == 200 and json.load(response).get("status") == "UP":
                        return True
            except Exception:
                pass
            time.sleep(2)
        return False

    def set_app_owner(self, path: Path):
        import grp
        import pwd
        os.chown(path, pwd.getpwnam("vetly").pw_uid, grp.getgrnam("vetly").gr_gid)

    def public_checks(self, release: Path, manifest: dict):
        class Assets(HTMLParser):
            def __init__(self): super().__init__(); self.paths = set()
            def handle_starttag(self, tag, attrs):
                for key, value in attrs:
                    if key in {"src", "href"} and value and value.lstrip("./").startswith("assets/"):
                        p = PurePosixPath(value.lstrip("/"))
                        if ".." in p.parts:
                            raise ReleaseError("Index asset yolu güvenli değil")
                        self.paths.add(str(p))
        def fetch(path):
            separator = "&" if "?" in path else "?"
            with self.http.open(PUBLIC_URL + "/" + path.lstrip("/") + separator + "release=" + manifest["releaseId"], timeout=15) as response:
                if response.status != 200:
                    raise ReleaseError("Canlı HTTP kontrolü başarısız")
                return response.read()
        index = (release / "frontend/index.html").read_bytes()
        if fetch("") != index:
            raise ReleaseError("Canlı index yeni paketle uyuşmuyor")
        parser = Assets(); parser.feed(index.decode("utf-8"))
        for asset in parser.paths:
            if fetch(asset) != (release / "frontend" / asset).read_bytes():
                raise ReleaseError("Canlı asset uyuşmuyor: " + asset)
        version = json.loads(fetch("downloads/vetly-tarbil-eklenti.json"))
        if version.get("version") != manifest["extensionVersion"]:
            raise ReleaseError("Canlı eklenti sürümü uyuşmuyor")
        if fetch("downloads/vetly-tarbil-eklenti.zip") != (release / "frontend/downloads/vetly-tarbil-eklenti.zip").read_bytes():
            raise ReleaseError("Canlı eklenti ZIP uyuşmuyor")
        if json.loads(fetch("health/ready")).get("status") != "UP":
            raise ReleaseError("Dış readiness UP değil")


def compare_history(history: list[dict], migrations: list[dict]) -> list[str]:
    expected = {m["version"]: m for m in migrations}
    seen = set()
    for row in history:
        version = row.get("version")
        if not row.get("success") or version not in expected or version in seen:
            raise ReleaseError("Canlı migration geçmişi paketle uyumsuz; yayın durduruldu")
        if any(row.get(key) != expected[version][key] for key in ("script", "checksum")):
            raise ReleaseError("Migration dosyası/checksum uyuşmuyor: V" + version)
        seen.add(version)
    maximum = max((int(v) for v in seen), default=0)
    pending = [m["version"] for m in migrations if m["version"] not in seen]
    if any(int(v) <= maximum for v in pending):
        raise ReleaseError("Sıra dışı/eksik eski migration var; otomatik yayın yapılamaz")
    return pending

class Deployment:
    def __init__(self, layout: Layout, runtime=None, notify=event):
        self.layout = layout
        self.runtime = runtime or Runtime(layout)
        self.notify = notify
        self.stage = "preflight"

    def emit(self, stage, status, message):
        self.stage = stage
        self.notify(stage, status, message)

    def safe_release(self, release_id: str) -> Path:
        if not RELEASE_ID.fullmatch(release_id):
            raise ReleaseError("Release kimliği geçersiz")
        return self.layout.releases / release_id

    def prepare(self, release_id: str, manifest_hash: str) -> tuple[Path, dict]:
        target = self.safe_release(release_id)
        incoming = self.layout.incoming / release_id
        if self.layout.incoming.resolve() != self.layout.incoming or not incoming.resolve().is_relative_to(self.layout.incoming) or incoming.is_symlink():
            raise ReleaseError("Staging yolu güvenli değil")
        manifest = verify(incoming, manifest_hash)
        if manifest["releaseId"] != release_id:
            raise ReleaseError("Staging ve manifest kimliği uyuşmuyor")
        self.layout.releases.mkdir(parents=True, exist_ok=True)
        if target.exists():
            if target.is_symlink(): raise ReleaseError("Release hedefi sembolik bağlantı")
            verified = verify(target, manifest_hash)
            if not (target / "package-ready.json").is_file() or read_json(target / "package-ready.json").get("manifestSha256") != manifest_hash or not (target / "frontend/index.html").is_file():
                raise ReleaseError("Eksik sunucu paketi var; yeni release hazırlayın")
            return target, verified
        _, unpacked_size = frontend_members(incoming / "frontend.tar.gz")
        self.runtime.reserve_space(sum((incoming / name).stat().st_size for name in PAYLOADS) + unpacked_size + 50 * 1024**2)
        target.mkdir(mode=0o700)
        try:
            for name in (*PAYLOADS, "release.json"):
                source = incoming / name
                # O_NOFOLLOW prevents changing a staging file to a link between validation and opening.
                with os.fdopen(os.open(source, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)), "rb") as stream, (target / name).open("xb") as output:
                    shutil.copyfileobj(stream, output)
                os.chmod(target / name, 0o600)
            verify(target, manifest_hash)
        except Exception:
            # Keep failed package for diagnosis; never replace a root-owned snapshot in place.
            raise ReleaseError("Sunucuya paket kopyası doğrulanamadı; yeni release hazırlayın")
        members, _ = frontend_members(target / "frontend.tar.gz")
        frontend = target / "frontend"; frontend.mkdir(mode=0o755)
        with tarfile.open(target / "frontend.tar.gz", "r:gz") as archive:
            for member in members:
                destination = frontend / str(PurePosixPath(member.name))
                if member.isdir(): destination.mkdir(parents=True, exist_ok=True); continue
                destination.parent.mkdir(parents=True, exist_ok=True)
                with archive.extractfile(member) as source, destination.open("xb") as output:
                    shutil.copyfileobj(source, output)
                os.chmod(destination, 0o644)
        for path in frontend.rglob("*"):
            if path.is_dir(): os.chmod(path, 0o755)
        write_json(target / "package-ready.json", {"manifestSha256": manifest_hash})
        return target, manifest

    def check(self, release_id: str, manifest_hash: str):
        self.emit("preflight", "start", "Paket, migration, disk ve systemd kontrolleri")
        release, manifest = self.prepare(release_id, manifest_hash)
        history = self.runtime.history()
        pending = compare_history(history, read_json(release / "migrations.json"))
        _, frontend_size = frontend_members(release / "frontend.tar.gz")
        self.runtime.preflight(pending, (release / "app.jar").stat().st_size * 3 + frontend_size * 3)
        if not (self.layout.app / "app.jar").is_file() or self.layout.web.is_symlink() or not self.layout.web.is_dir():
            raise ReleaseError("Mevcut backend/web yerleşimi beklenenden farklı")
        self.emit("preflight", "ok", "Kontroller geçti; bekleyen migration: " + (", ".join(pending) or "yok"))
        return release, manifest, history, pending

    def atomic_copy(self, source: Path, target: Path, app=False):
        if target.is_symlink() or target.parent.is_symlink():
            raise ReleaseError("Yayın hedefi sembolik bağlantı olamaz")
        temp = target.with_name("." + target.name + ".deploy-next")
        if temp.exists() or temp.is_symlink():
            raise ReleaseError("Önceki geçici yayın dosyası var; inceleyin: " + str(temp))
        with temp.open("xb") as stream, source.open("rb") as data:
            shutil.copyfileobj(data, stream)
            stream.flush(); os.fsync(stream.fileno())
        os.chmod(temp, 0o644)
        if app: self.runtime.set_app_owner(temp)
        os.replace(temp, target)

    def publish_frontend(self, source: Path):
        files = [p for p in source.rglob("*") if p.is_file()]
        # index last: existing browsers keep using retained hashed assets.
        files.sort(key=lambda p: (p.relative_to(source).as_posix() == "index.html", p.as_posix()))
        for file in files:
            target = self.layout.web / file.relative_to(source)
            for parent in [target.parent, *target.parent.parents]:
                if parent == self.layout.web.parent: break
                if parent.is_symlink(): raise ReleaseError("Web hedefinde symlink var")
            target.parent.mkdir(parents=True, exist_ok=True)
            self.runtime.safe_web_directory(target.parent)
            os.chmod(target.parent, 0o755)
            self.atomic_copy(file, target)

    def backup(self, release_id: str) -> Path:
        backup = self.layout.backups / release_id
        backup.mkdir(parents=True, mode=0o700, exist_ok=False)
        self.runtime.backup_database(backup / "database.dump")
        current_jar = self.layout.app / "app.jar"
        with os.fdopen(os.open(current_jar, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)), "rb") as source, (backup / "app.jar").open("xb") as target:
            if not stat.S_ISREG(os.fstat(source.fileno()).st_mode):
                raise ReleaseError("Çalışan JAR normal dosya olmalıdır")
            os.chmod(backup / "app.jar", 0o600)
            shutil.copyfileobj(source, target)
        # Copy only ordinary public files; no user-provided tar is extracted for rollback.
        shutil.copytree(self.layout.web, backup / "frontend", symlinks=True)
        if any(p.is_symlink() for p in (backup / "frontend").rglob("*")):
            raise ReleaseError("Mevcut web dizininde symlink var; otomatik yayın durduruldu")
        return backup

    def apply(self, release_id: str, manifest_hash: str) -> dict:
        release, manifest, before, pending = self.check(release_id, manifest_hash)
        state_path = release / "deployment-state.json"
        if state_path.exists():
            raise ReleaseError("Bu release daha önce uygulandı/denendi; yeni paket hazırlayın")
        result = {"releaseId": release_id, "status": "running", "pendingMigrations": pending, "backendRestored": False, "frontendRestored": False}
        write_json(state_path, result)
        backend_changed = frontend_changed = False
        backup = None
        try:
            self.emit("backup", "start", "Veritabanı ve uygulama yedekleniyor")
            backup = self.backup(release_id)
            self.emit("backup", "ok", "Veritabanı yedeği okunabilir; eski JAR ve frontend korundu")
            if self.runtime.history() != before:
                raise ReleaseError("Yedek sırasında migration geçmişi değişti; yeni paket/kontrol ile tekrar deneyin")
            self.runtime.install_extensions(pending)
            self.emit("backend", "start", "Backend atomik değiştiriliyor ve yeniden başlatılıyor")
            self.atomic_copy(release / "app.jar", self.layout.app / "app.jar", app=True)
            backend_changed = True
            self.runtime.restart()
            if not self.runtime.ready(): raise ReleaseError("Yeni backend readiness UP olmadı")
            if compare_history(self.runtime.history(), read_json(release / "migrations.json")):
                raise ReleaseError("Backend hazır görünse de beklenen migration'lar uygulanmamış")
            self.emit("backend", "ok", "Backend hazır")
            self.emit("frontend", "start", "Statik dosyalar yükleniyor; index en son değişecek")
            frontend_changed = True
            self.publish_frontend(release / "frontend")
            self.emit("frontend", "ok", "Frontend yayınlandı; eski hashed asset dosyaları korundu")
            self.emit("smoke", "start", "HTTPS, asset ve eklenti kontrolleri")
            self.runtime.public_checks(release, manifest)
            self.emit("smoke", "ok", "Canlı kontroller başarılı")
            result["status"] = "succeeded"
        except Exception as exc:
            result.update(status="failed", error=str(exc))
            try:
                if frontend_changed and backup:
                    self.publish_frontend(backup / "frontend")
                    result["frontendRestored"] = True
                if backend_changed and backup:
                    after = self.runtime.history()
                    result["schemaChanged"] = after != before
                    if after == before and not pending:
                        self.atomic_copy(backup / "app.jar", self.layout.app / "app.jar", app=True)
                        self.runtime.restart()
                        result["backendRestored"] = self.runtime.ready()
                    else:
                        result["manualRecoveryRequired"] = True
                        result["recoveryNote"] = "Migration geçmişi değişti veya bekleyen migration ile başlangıç denendi: eski JAR/veritabanı otomatik geri yüklenmedi. Şemayı, yeni backend'i ve yedeği inceleyin."
            except Exception as recovery:
                result["recoveryError"] = str(recovery)
                result["manualRecoveryRequired"] = True
            self.emit(self.stage, "error", str(exc))
        write_json(state_path, result)
        return result

@contextmanager
def deployment_lock(layout: Layout):
    import fcntl
    layout.state.mkdir(parents=True, mode=0o700, exist_ok=True)
    for path in [layout.state, *layout.state.parents]:
        info = path.stat()
        if path.is_symlink() or info.st_uid != 0 or info.st_mode & 0o022:
            raise ReleaseError("Deploy durum dizini root-owned ve yazmaya kapalı olmalıdır")
    with (layout.state / ".deploy.lock").open("a") as stream:
        try: fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError: raise ReleaseError("Başka bir deployment çalışıyor")
        try: yield
        finally: fcntl.flock(stream, fcntl.LOCK_UN)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["check", "apply", "status"])
    parser.add_argument("--release", required=True)
    parser.add_argument("--manifest-sha256")
    parser.add_argument("--helper-sha256")
    parser.add_argument("--release-helper-sha256")
    args = parser.parse_args()
    if args.action != "status":
        code_dir = Path(__file__).resolve().parent
        if not re_full_hash(args.helper_sha256) or not re_full_hash(args.release_helper_sha256):
            raise ReleaseError("Deploy yardımcı sürüm hash'leri zorunlu")
        if digest(code_dir / "server.py") != args.helper_sha256 or digest(code_dir / "release.py") != args.release_helper_sha256:
            raise ReleaseError("Sunucu yardımcı sürümü yerel kodla uyuşmuyor; iki yardımcı dosyayı yeniden kurun")
    runner = Deployment(Layout())
    runner.safe_release(args.release)
    if args.action != "status" and not re_full_hash(args.manifest_sha256):
        raise ReleaseError("Manifest SHA-256 zorunlu")
    with deployment_lock(runner.layout):
        if args.action == "check":
            _, manifest, _, pending = runner.check(args.release, args.manifest_sha256)
            result = {"releaseId": args.release, "status": "checked", "pendingMigrations": pending, "commit": manifest["commit"]}
        elif args.action == "apply": result = runner.apply(args.release, args.manifest_sha256)
        else:
            path = runner.safe_release(args.release) / "deployment-state.json"
            result = read_json(path) if path.is_file() else {"status": "not-applied", "releaseId": args.release}
    print("@@VETLY_RESULT@@" + json.dumps(result, ensure_ascii=False), flush=True)
    if result.get("status") == "failed": sys.exit(1)


def re_full_hash(value):
    import re
    return bool(value and re.fullmatch("[a-f0-9]{64}", value))

if __name__ == "__main__":
    try: main()
    except Exception as exc:
        event("preflight", "error", str(exc)); sys.exit(1)
