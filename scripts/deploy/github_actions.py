"""GitHub Actions glue: artifact identity and isolated SSH; no deployment commands."""
from __future__ import annotations
import argparse
import os
import re
import subprocess
import tempfile
from pathlib import Path
from release import RELEASE_ID, ReleaseError, digest, verify


def append_environment(name: str, values: dict):
    path = os.environ.get(name)
    if not path: raise ReleaseError("GitHub Actions çıktı dosyası bulunamadı: " + name)
    for value in values.values():
        if any(c in str(value) for c in "\r\n"): raise ReleaseError("Çıktı değeri satır sonu içeremez")
    with open(path, "a", encoding="utf-8") as stream:
        for key, value in values.items(): stream.write(f"{key}={value}\n")


def checked_package(path: Path, expected_commit: str, expected_hash: str | None = None) -> dict:
    if not re.fullmatch(r"[a-f0-9]{40}", expected_commit): raise ReleaseError("Workflow commit kimliği geçersiz")
    manifest = verify(path, expected_hash)
    if manifest.get("commit") != expected_commit: raise ReleaseError("Paket workflow commit'iyle uyuşmuyor")
    if manifest.get("sourceMode") != "commit" or manifest.get("dirty"):
        raise ReleaseError("GitHub yayını yalnız temiz commit paketi kabul eder")
    if path.name != manifest["releaseId"]: raise ReleaseError("Paket dizini manifest kimliğiyle uyuşmuyor")
    return manifest


def export_package(repo: Path):
    packages = [p for p in (repo / ".deploy/releases").iterdir() if p.is_dir()]
    if len(packages) != 1: raise ReleaseError("Yeni runner üzerinde tam bir paket bulunmalı")
    path = packages[0]
    manifest = checked_package(path, os.environ.get("GITHUB_SHA", ""))
    sha = digest(path / "release.json")
    append_environment("GITHUB_OUTPUT", {"release_id": manifest["releaseId"], "manifest_sha256": sha})
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write(f"## Hazırlanan production paketi\n\nCommit: `{manifest['commit']}`\n\nPaket: `{manifest['releaseId']}`\n\nManifest SHA-256: `{sha}`\n\nMigration: {manifest['migrationCount']}\n\nCI testleri ayrı işte zorunludur. Maven paketleme adımı testleri tekrar çalıştırmaz.\n")


def verify_download(repo: Path):
    release_id = os.environ.get("VETLY_RELEASE_ID", "")
    sha = os.environ.get("VETLY_MANIFEST_SHA256", "")
    if not RELEASE_ID.fullmatch(release_id) or not re.fullmatch(r"[a-f0-9]{64}", sha):
        raise ReleaseError("İşler arası paket kimliği veya hash değeri geçersiz")
    checked_package(repo / ".deploy/releases" / release_id, os.environ.get("GITHUB_SHA", ""), sha)
    print("İndirilen paket: SHA-256, commit ve kaynak modu doğrulandı")


def remove_ssh(directory: Path):
    root = Path(os.environ["RUNNER_TEMP"]).resolve()
    directory = directory.resolve()
    if directory.parent != root or not directory.name.startswith("vetly-ssh-"):
        raise ReleaseError("SSH temizlik dizini runner geçici alanında olmalıdır")
    for name in ("config", "identity", "known_hosts"):
        (directory / name).unlink(missing_ok=True)
    directory.rmdir()


def configure_ssh():
    key = os.environ.get("VETLY_SSH_PRIVATE_KEY", "").replace("\r\n", "\n").strip()
    hosts = os.environ.get("VETLY_SSH_KNOWN_HOSTS", "").replace("\r\n", "\n").strip()
    if not key.startswith("-----BEGIN OPENSSH PRIVATE KEY-----") or not hosts:
        raise ReleaseError("production SSH anahtarı veya doğrulanmış known_hosts eksik")
    directory = Path(tempfile.mkdtemp(prefix="vetly-ssh-", dir=os.environ["RUNNER_TEMP"])).resolve()
    try:
        if os.name == "nt":
            user = subprocess.check_output(["whoami"], text=True).strip()
            subprocess.run(["icacls", str(directory), "/inheritance:r", "/grant:r", user + ":(OI)(CI)F"],
                           check=True, capture_output=True)
        else: directory.chmod(0o700)
        identity, known_hosts = directory / "identity", directory / "known_hosts"
        identity.write_text(key + "\n", encoding="ascii", newline="\n")
        if os.name == "nt":
            subprocess.run(["icacls", str(identity), "/reset"], check=True, capture_output=True)
            subprocess.run(["icacls", str(identity), "/inheritance:r", "/grant:r", user + ":F", "/remove:g", "*S-1-3-4"],
                           check=True, capture_output=True)
        else: identity.chmod(0o600)
        known_hosts.write_text(hosts + "\n", encoding="ascii", newline="\n")
        for args in (["ssh-keygen", "-y", "-P", "", "-f", str(identity)],
                     ["ssh-keygen", "-F", "70.40.139.40", "-f", str(known_hosts)]):
            result = subprocess.run(args, capture_output=True, timeout=15)
            if result.returncode: raise ReleaseError("SSH anahtarı parolasız/geçerli olmalı ve known_hosts hedef sunucuyu içermelidir")
        config = directory / "config"
        config.write_text('Host 70.40.139.40\n    HostName 70.40.139.40\n    User furkan\n'
            + f'    IdentityFile "{identity.as_posix()}"\n    UserKnownHostsFile "{known_hosts.as_posix()}"\n'
            + '    GlobalKnownHostsFile none\n    StrictHostKeyChecking yes\n    IdentitiesOnly yes\n'
            + '    BatchMode yes\n    PasswordAuthentication no\n    KbdInteractiveAuthentication no\n'
            + '    ForwardAgent no\n    ConnectTimeout 10\n', encoding="utf-8", newline="\n")
        append_environment("GITHUB_ENV", {"VETLY_SSH_CONFIG": config, "VETLY_SSH_DIR": directory})
        print("Geçici SSH yapılandırması hazır; parola ve anahtar loglanmadı")
    except Exception:
        remove_ssh(directory)
        raise


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["export-package", "verify-package", "configure-ssh", "cleanup-ssh"])
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    if args.action == "export-package": export_package(repo)
    elif args.action == "verify-package": verify_download(repo)
    elif args.action == "configure-ssh": configure_ssh()
    elif os.environ.get("VETLY_SSH_DIR"): remove_ssh(Path(os.environ["VETLY_SSH_DIR"]))


if __name__ == "__main__":
    try: main()
    except (ReleaseError, OSError, ValueError, subprocess.SubprocessError) as exc:
        import sys
        print("GitHub hazırlık/doğrulama hatası: " + str(exc), file=sys.stderr)
        sys.exit(1)
