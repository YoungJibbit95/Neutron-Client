"""Install checksum-locked workflow linters without extracting archive paths."""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import sys
import tarfile
import time
from urllib.error import HTTPError, URLError
from urllib.request import urlopen
import zipfile

ROOT = Path(__file__).resolve().parents[2]
MAX_DOWNLOAD = 128 * 1024 * 1024


def download_locked_archive(url, fetch):
    # GitHub release CDN may transiently answer 429/5xx. Keep the pinned URL,
    # size cap, and SHA256 check; corrupt or missing artifacts never retry as valid.
    for attempt in range(4):
        try:
            with fetch(url, timeout=60) as response:
                return response.read(MAX_DOWNLOAD + 1)
        except HTTPError as exc:
            if exc.code not in (429, 500, 502, 503, 504) or attempt == 3:
                raise
        except (URLError, TimeoutError):
            if attempt == 3:
                raise
        time.sleep(3 * (attempt + 1))


def install(entry, destination, binary, fetch=urlopen):
    if Path(binary).name != binary or any(c in binary for c in ("/", "\\")):
        raise ValueError("Expected a binary filename")
    if not entry["url"].startswith("https://github.com/") or "/releases/download/" not in entry["url"]:
        raise ValueError("Expected a pinned GitHub release URL")
    data = download_locked_archive(entry["url"], fetch)
    if len(data) > MAX_DOWNLOAD or hashlib.sha256(data).hexdigest() != entry["sha256"]:
        raise ValueError("Workflow tool download size/checksum mismatch")
    if entry["url"].endswith(".zip"):
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            info = archive.getinfo(entry["member"])
            if info.is_dir() or info.file_size > MAX_DOWNLOAD:
                raise ValueError("Expected a bounded binary archive member")
            content = archive.read(info)
    else:
        with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
            member = archive.getmember(entry["member"])
            if not member.isfile() or member.size > MAX_DOWNLOAD:
                raise ValueError("Expected a regular binary archive member")
            content = archive.extractfile(member).read(MAX_DOWNLOAD + 1)
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    target = destination / binary
    if target.is_symlink():
        raise ValueError("Refusing to overwrite a symbolic link")
    target.write_bytes(content)
    if os.name != "nt":
        target.chmod(0o755)
    return target


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, required=True)
    args = parser.parse_args()
    system = platform.system().lower()
    if system not in ("windows", "linux") or platform.machine().lower() not in ("amd64", "x86_64"):
        raise SystemExit("Workflow tools are locked for Windows/Linux x64")
    lock = json.loads((ROOT / ".github/workflow-tools.json").read_text(encoding="utf-8"))
    try:
        for name, metadata in lock.items():
            binary = name + (".exe" if system == "windows" else "")
            print(install(metadata[system], args.destination, binary))
    except HTTPError as error:
        if error.code not in (429, 500, 502, 503, 504):
            raise
        print("Verified GitHub release mirror unavailable (HTTP {}); only a separately verified registry source may be used.".format(error.code), file=sys.stderr)
        sys.exit(75)
    except (URLError, TimeoutError) as error:
        print("Verified GitHub release mirror unavailable; only a separately verified registry source may be used: {}".format(error), file=sys.stderr)
        sys.exit(75)
