"""Immutable deployment artifacts and source snapshots. Python standard library only."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import tarfile
import uuid
import zipfile
import zlib
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath

PUBLIC_URL = "https://uygulama.vetly.com.tr"
RELEASE_ID = re.compile(r"[0-9]{8}T[0-9]{6}Z-[a-f0-9]{7,40}-[a-f0-9]{8}\Z")
PAYLOADS = ("app.jar", "frontend.tar.gz", "migrations.json", "source-files.json")
DENIED = ("backend/target/", "frontend/node_modules/", "frontend/dist/", "frontend/public/downloads/", "extension/node_modules/", "extension/dist/")

class ReleaseError(RuntimeError):
    pass

def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()

def write_json(path: Path, value) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")

def read_json(path: Path):
    if path.stat().st_size > 8 * 1024 * 1024:
        raise ReleaseError("JSON dosyası beklenenden büyük")
    return json.loads(path.read_text(encoding="utf-8-sig"))

def git(repo: Path, *args: str) -> str:
    result = subprocess.run(["git", "-c", "core.quotepath=false", "-C", str(repo), *args], capture_output=True, text=True, encoding="utf-8", env=dict(os.environ, GIT_OPTIONAL_LOCKS="0"))
    if result.returncode:
        raise ReleaseError("Git komutu başarısız: " + result.stderr.strip())
    return result.stdout.strip()

def source_allowed(name: str) -> bool:
    p = PurePosixPath(name)
    return (not p.is_absolute() and ".." not in p.parts and p.parts[0] in {"backend", "frontend", "extension"}
            and not name.startswith(DENIED) and not any(part in {".git", "node_modules", ".claude", ".codex"} for part in p.parts)
            and p.name not in {".npmrc", ".pypirc", ".netrc"} and not p.name.startswith(".env") and p.suffix.lower() not in {".pem", ".key", ".pfx", ".p12"})

def committed_files(repo: Path, commit: str, source: Path) -> dict:
    """Read raw Git tree/blob objects; never checkout, stash or modify the index."""
    listing = subprocess.run(["git", "-C", str(repo), "ls-tree", "-r", "-z", "--full-tree", commit, "--", "backend", "frontend", "extension"],
                             capture_output=True, check=True).stdout
    files = []
    for entry in listing.split(b"\0"):
        if not entry: continue
        header, raw_name = entry.split(b"\t", 1)
        mode, kind, object_id = header.decode("ascii").split()
        name = raw_name.decode("utf-8")
        if not source_allowed(name): continue
        if mode not in {"100644", "100755"} or kind != "blob":
            raise ReleaseError("Commit içinde desteklenmeyen kaynak türü: " + name)
        files.append((name, object_id))
    process = subprocess.Popen(["git", "-C", str(repo), "cat-file", "--batch"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    result = {}
    try:
        for name, object_id in files:
            process.stdin.write((object_id + "\n").encode("ascii")); process.stdin.flush()
            header = process.stdout.readline().decode("ascii").strip().split()
            if len(header) != 3 or header[0] != object_id or header[1] != "blob":
                raise ReleaseError("Commit kaynak nesnesi okunamadı: " + name)
            size = int(header[2])
            if size > 256 * 1024 * 1024: raise ReleaseError("Kaynak dosya boyut sınırını aşıyor: " + name)
            data = process.stdout.read(size)
            if len(data) != size or process.stdout.read(1) != b"\n":
                raise ReleaseError("Commit kaynak nesnesi eksik: " + name)
            if data.startswith(b"version https://git-lfs.github.com/spec/v1"):
                raise ReleaseError("Git LFS kaynağı doğrudan paketlenemez: " + name)
            target = source / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            result[name] = hashlib.sha256(data).hexdigest()
    except Exception:
        process.kill()
        raise
    finally:
        process.stdin.close()
        process.stdout.close()
        errors = process.stderr.read().decode("utf-8", "replace")
        process.stderr.close()
        code = process.wait(timeout=10)
    if code: raise ReleaseError("Commit kaynakları okunamadı: " + errors)
    return result


def snapshot(repo: Path, output: Path, allow_dirty: bool = False, source_mode: str = "commit") -> dict:
    repo = repo.resolve()
    if source_mode not in {"commit", "working-tree"}: raise ReleaseError("Kaynak seçimi geçersiz")
    commit = git(repo, "rev-parse", "HEAD")
    if not re.fullmatch(r"[a-f0-9]{40,64}", commit):
        raise ReleaseError("Geçerli Git commit'i bulunamadı")
    local_changes = git(repo, "status", "--porcelain", "--untracked-files=all", "--", "backend", "frontend", "extension").splitlines()
    if source_mode == "working-tree" and local_changes and not allow_dirty:
        raise ReleaseError("Uygulama kaynaklarında commitlenmemiş değişiklik var. İnceleyip commit edin veya panelde yerel değişiklikleri dahil etmeyi açıkça seçin.")
    release_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + commit[:12] + "-" + uuid.uuid4().hex[:8]
    work = output.resolve() / "work" / release_id
    source = work / "source"
    source.mkdir(parents=True, exist_ok=False)
    if source_mode == "commit":
        entries = committed_files(repo, commit, source)
    else:
        entries = {}
        names = git(repo, "ls-files", "--cached", "--others", "--exclude-standard", "--", "backend", "frontend", "extension").splitlines()
        for name in sorted(set(names)):
            if not source_allowed(name): continue
            original = repo / name
            if not original.exists(): continue
            if original.is_symlink() or not original.is_file() or not original.resolve().is_relative_to(repo):
                raise ReleaseError("Kaynak dosya yolu güvenli değil: " + name)
            target = source / name
            target.parent.mkdir(parents=True, exist_ok=True)
            before = digest(original)
            shutil.copyfile(original, target)
            if digest(target) != before or digest(original) != before:
                raise ReleaseError("Kopyalama sırasında kaynak değişti: " + name)
            entries[name] = before
    for required in ("backend/pom.xml", "backend/mvnw.cmd", "frontend/package-lock.json", "extension/package-lock.json"):
        if required not in entries:
            raise ReleaseError("Gerekli kaynak eksik: " + required)
    metadata = {"releaseId": release_id, "commit": commit, "sourceMode": source_mode,
                "dirty": local_changes if source_mode == "working-tree" else [],
                "ignoredLocalChangeCount": len(local_changes) if source_mode == "commit" else 0,
                "preparedAt": datetime.now(timezone.utc).isoformat(), "repo": str(repo), "work": str(work),
                "source": str(source), "sourceFiles": entries}
    write_json(work / "snapshot.json", metadata)
    return metadata


def flyway_checksum(raw: bytes) -> int:
    text = raw.decode("utf-8-sig")
    crc = 0
    # BufferedReader.readLine ignores CR/LF; UTF-8 content and spaces remain significant.
    for line in re.split(r"\r\n|\n|\r", text):
        crc = zlib.crc32(line.encode("utf-8"), crc)
    return crc if crc < 2**31 else crc - 2**32

def jar_migrations(jar: Path) -> list[dict]:
    entries = []
    with zipfile.ZipFile(jar) as archive:
        if "BOOT-INF/classes/com/vetos/VetosApplication.class" not in archive.namelist():
            raise ReleaseError("Beklenen Spring Boot uygulaması JAR içinde yok")
        for info in archive.infolist():
            match = re.fullmatch(r"BOOT-INF/classes/db/migration/(V([0-9]+)__[a-zA-Z0-9_]+\.sql)", info.filename)
            if match:
                if info.file_size > 5 * 1024 * 1024:
                    raise ReleaseError("Migration beklenenden büyük")
                entries.append({"version": str(int(match[2])), "script": match[1], "checksum": flyway_checksum(archive.read(info))})
    entries.sort(key=lambda e: int(e["version"]))
    if not entries or len({e["version"] for e in entries}) != len(entries):
        raise ReleaseError("Migration listesi boş veya aynı sürüm tekrarlanıyor")
    return entries

def frontend_members(archive_path: Path) -> tuple[list[tarfile.TarInfo], int]:
    seen = set()
    result = []
    size = 0
    with tarfile.open(archive_path, "r:gz") as archive:
        for entry in archive:
            path = PurePosixPath(entry.name)
            if path.is_absolute() or ".." in path.parts or "\\" in entry.name or not (entry.isdir() or entry.isfile()):
                raise ReleaseError("Frontend arşivinde güvenli olmayan giriş: " + entry.name)
            name = str(path)
            if name == "." and entry.isdir():
                continue
            if name in seen:
                raise ReleaseError("Frontend arşivinde tekrarlı yol: " + name)
            seen.add(name)
            size += entry.size
            if size > 1024**3 or len(seen) > 20000:
                raise ReleaseError("Frontend arşivi boyut sınırını aşıyor")
            result.append(entry)
    if not {"index.html", "downloads/vetly-tarbil-eklenti.json", "downloads/vetly-tarbil-eklenti.zip"}.issubset(seen):
        raise ReleaseError("Frontend index veya eklenti dosyaları eksik")
    return result, size

def pack(work: Path, output: Path) -> dict:
    metadata = read_json(work / "snapshot.json")
    source = Path(metadata["source"])
    for name, expected in metadata["sourceFiles"].items():
        copied = source / name
        if not copied.is_file() or copied.is_symlink() or digest(copied) != expected:
            raise ReleaseError("İzole derleme kaynağı değişti; yeniden hazırlayın: " + name)
        if metadata.get("sourceMode", "working-tree") == "working-tree":
            original = Path(metadata["repo"]) / name
            if not original.is_file() or digest(original) != expected:
                raise ReleaseError("Derleme sırasında kaynak değişti; yeniden hazırlayın: " + name)
    release = output.resolve() / "releases" / metadata["releaseId"]
    release.mkdir(parents=True, exist_ok=False)
    jar = source / "backend/target/myvet-backend-0.1.0-SNAPSHOT.jar"
    shutil.copyfile(jar, release / "app.jar")
    migrations = jar_migrations(release / "app.jar")
    write_json(release / "migrations.json", migrations)
    write_json(release / "source-files.json", metadata["sourceFiles"])
    dist = source / "frontend/dist"
    extension = read_json(dist / "downloads/vetly-tarbil-eklenti.json")["version"]
    if extension != read_json(source / "extension/package.json")["version"]:
        raise ReleaseError("Eklenti sürümü kaynak ile uyuşmuyor")
    with tarfile.open(release / "frontend.tar.gz", "w:gz") as archive:
        for path in sorted(dist.rglob("*")):
            if path.is_symlink() or not path.is_file() and not path.is_dir():
                raise ReleaseError("Frontend derlemesinde özel dosya var")
            archive.add(path, arcname=path.relative_to(dist).as_posix(), recursive=False)
    frontend_members(release / "frontend.tar.gz")
    manifest = {"schemaVersion": 1, "releaseId": metadata["releaseId"], "commit": metadata["commit"],
                "dirty": metadata["dirty"], "sourceMode": metadata.get("sourceMode", "working-tree"),
                "ignoredLocalChangeCount": metadata.get("ignoredLocalChangeCount", 0), "preparedAt": metadata["preparedAt"], "publicUrl": PUBLIC_URL,
                "extensionVersion": extension, "migrationCount": len(migrations),
                "verification": {"backendTests": "not-run-by-deploy", "frontendBuild": "typescript-and-vite"},
                "files": {name: digest(release / name) for name in PAYLOADS}}
    write_json(release / "release.json", manifest)
    verify(release)
    return {"releaseId": manifest["releaseId"], "path": str(release), "manifestSha256": digest(release / "release.json")}

def verify(directory: Path, expected_manifest: str | None = None) -> dict:
    directory = directory.resolve()
    for name in (*PAYLOADS, "release.json"):
        path = directory / name
        if path.is_symlink() or not path.is_file():
            raise ReleaseError("Paket dosyası eksik veya sembolik bağlantı: " + name)
    if expected_manifest and digest(directory / "release.json") != expected_manifest:
        raise ReleaseError("Manifest SHA-256 uyuşmuyor")
    manifest = read_json(directory / "release.json")
    if manifest.get("schemaVersion") != 1 or not RELEASE_ID.fullmatch(manifest.get("releaseId", "")):
        raise ReleaseError("Paket kimliği veya protokol sürümü geçersiz")
    if manifest.get("sourceMode", "working-tree") not in {"commit", "working-tree"}:
        raise ReleaseError("Paketin kaynak seçimi geçersiz")
    if manifest.get("sourceMode") == "commit" and manifest.get("dirty"):
        raise ReleaseError("Commit paketine yerel değişiklik bilgisi dahil edilemez")
    if manifest.get("publicUrl") != PUBLIC_URL:
        raise ReleaseError("Paket yanlış production adresi için hazırlanmış")
    if set(manifest.get("files", {})) != set(PAYLOADS):
        raise ReleaseError("Paket dosya listesi beklenenle uyuşmuyor")
    for name in PAYLOADS:
        if digest(directory / name) != manifest["files"][name]:
            raise ReleaseError("SHA-256 uyuşmuyor: " + name)
    migrations = jar_migrations(directory / "app.jar")
    if migrations != read_json(directory / "migrations.json") or len(migrations) != manifest.get("migrationCount"):
        raise ReleaseError("Migration kataloğu gerçek JAR ile uyuşmuyor")
    frontend_members(directory / "frontend.tar.gz")
    with tarfile.open(directory / "frontend.tar.gz", "r:gz") as archive:
        files = {str(PurePosixPath(m.name)): m for m in archive.getmembers() if m.isfile()}
        extension = json.loads(archive.extractfile(files["downloads/vetly-tarbil-eklenti.json"]).read())
        if extension.get("version") != manifest.get("extensionVersion"):
            raise ReleaseError("Frontend eklenti sürümü manifest ile uyuşmuyor")
        index = archive.extractfile(files["index.html"]).read().decode("utf-8")
        if PUBLIC_URL not in index:
            raise ReleaseError("Frontend production CSP/API adresi beklenenle uyuşmuyor")
    return manifest

def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="action", required=True)
    p = sub.add_parser("snapshot"); p.add_argument("--repo", type=Path, required=True); p.add_argument("--output", type=Path, required=True); p.add_argument("--allow-dirty", action="store_true"); p.add_argument("--source-mode", choices=["commit", "working-tree"], default="commit")
    p = sub.add_parser("pack"); p.add_argument("--work", type=Path, required=True); p.add_argument("--output", type=Path, required=True)
    p = sub.add_parser("verify"); p.add_argument("--directory", type=Path, required=True)
    args = parser.parse_args()
    if args.action == "snapshot": result = snapshot(args.repo, args.output, args.allow_dirty, args.source_mode)
    elif args.action == "pack": result = pack(args.work, args.output)
    else: result = verify(args.directory)
    print(json.dumps(result, ensure_ascii=False))

if __name__ == "__main__":
    try:
        main()
    except (ReleaseError, OSError, ValueError, zipfile.BadZipFile, tarfile.TarError) as exc:
        import sys
        print(str(exc), file=sys.stderr)
        sys.exit(1)
