"""Developer fixture: official Minecraft assets and platform-matched legacy natives.

This is not the player-facing first-run installer (section 4).
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import tempfile
import urllib.parse
import urllib.request
import zipfile

from client_contract import PLATFORMS, digest, write_json

VERSION_SHA1 = "d546f1707a3f2b7d034eece5ea2e311eda875787"
VERSION_URL = "https://piston-meta.mojang.com/v1/packages/%s/1.8.9.json" % VERSION_SHA1
ALLOWED_HOSTS = {"piston-meta.mojang.com", "launchermeta.mojang.com", "libraries.minecraft.net", "resources.download.minecraft.net"}


def checked_url(url):
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or parsed.hostname not in ALLOWED_HOSTS or parsed.username or parsed.password or parsed.port not in (None, 443):
        raise ValueError("Download must use an official HTTPS Minecraft host.")
    return url


def download(url, target, expected_sha1, expected_size=None):
    checked_url(url)
    if not re.fullmatch(r"[0-9a-f]{40}", expected_sha1):
        raise ValueError("Invalid expected digest.")
    target = Path(target)
    if target.is_file() and (expected_size is None or target.stat().st_size == expected_size) and digest(target, "sha1") == expected_sha1:
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = None
    try:
        with tempfile.NamedTemporaryFile(dir=target.parent, prefix=".download-", delete=False) as stream:
            partial = Path(stream.name)
            request = urllib.request.Request(url, headers={"User-Agent": "Neutron-Launcher-Developer-Validation/1"})
            with urllib.request.urlopen(request, timeout=45) as response:
                checked_url(response.url)
                shutil.copyfileobj(response, stream)
        if expected_size is not None and partial.stat().st_size != expected_size:
            raise ValueError("Download size mismatch.")
        if digest(partial, "sha1") != expected_sha1:
            raise ValueError("Download digest mismatch.")
        partial.replace(target)
        return target
    finally:
        if partial and partial.exists():
            partial.unlink()


def applies(library, mojang_os):
    rules = library.get("rules")
    if not rules:
        return True
    allowed = False
    for rule in rules:
        system = rule.get("os", {})
        if set(system) - {"name"} or rule.get("features"):
            raise ValueError("Unsupported Mojang library rule; review before extending.")
        if not system or system.get("name") == mojang_os:
            allowed = rule["action"] == "allow"
    return allowed


def extract_natives(archive, target, excludes):
    target = Path(target).resolve()
    with zipfile.ZipFile(archive) as jar:
        for entry in jar.infolist():
            if entry.is_dir() or any(entry.filename.startswith(x) for x in excludes):
                continue
            name = PurePosixPath(entry.filename)
            if name.is_absolute() or ".." in name.parts or "\\" in entry.filename or ":" in entry.filename:
                raise ValueError("Unsafe archive entry.")
            if (entry.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError("Native archive symlinks are not supported.")
            path = (target / str(name)).resolve()
            if not path.is_relative_to(target):
                raise ValueError("Archive entry escapes native directory.")
            path.parent.mkdir(parents=True, exist_ok=True)
            content = jar.read(entry)
            if path.exists() and path.read_bytes() != content:
                raise ValueError("Conflicting native files: " + entry.filename)
            path.write_bytes(content)


def provision(destination, platform, assets=True):
    destination = Path(destination).resolve()
    if platform not in PLATFORMS or destination.exists():
        raise ValueError("Choose a supported candidate platform and a new empty destination.")
    destination.mkdir(parents=True)
    metadata_path = download(VERSION_URL, destination / "metadata/1.8.9.json", VERSION_SHA1)
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    index_meta = metadata["assetIndex"]
    if index_meta["id"] != "1.8":
        raise ValueError("Unexpected Minecraft asset index.")
    index_path = download(index_meta["url"], destination / "assets/indexes/1.8.json", index_meta["sha1"], index_meta["size"])
    index = json.loads(index_path.read_text(encoding="utf-8"))
    objects = {a["hash"]: a for a in index["objects"].values()}
    if assets:
        def asset_job(asset):
            h = asset["hash"]
            return download("https://resources.download.minecraft.net/%s/%s" % (h[:2], h),
                            destination / "assets/objects" / h[:2] / h, h, asset["size"])
        with ThreadPoolExecutor(max_workers=8) as workers:
            for number, _ in enumerate(workers.map(asset_job, objects.values()), 1):
                if number % 100 == 0:
                    print("Verified assets %d/%d" % (number, len(objects)), flush=True)
    mojang_os = {"windows-x64": "windows", "linux-x64": "linux", "macos-x64": "osx"}[platform]
    libraries, archives = [], []
    natives = destination / "natives"
    natives.mkdir()
    for library in metadata["libraries"]:
        if not applies(library, mojang_os):
            continue
        native_classifier = library.get("natives", {}).get(mojang_os)
        if native_classifier:
            classifier = native_classifier.replace("${arch}", "64")
            item = library["downloads"]["classifiers"][classifier]
            archive = download(item["url"], destination / "native-archives" / Path(item["path"]).name, item["sha1"], item["size"])
            extract_natives(archive, natives, library.get("extract", {}).get("exclude", []))
            archives.append({"coordinate": library["name"], "classifier": classifier, **item})
        # Override both Java binding jars as a pair, using Mojang's OS rules.
        if library["name"].startswith(("org.lwjgl.lwjgl:lwjgl:", "org.lwjgl.lwjgl:lwjgl_util:")):
            item = library["downloads"]["artifact"]
            jar = download(item["url"], destination / "java-libraries" / Path(item["path"]).name, item["sha1"], item["size"])
            libraries.append({"path": jar.relative_to(destination).as_posix(), "sha256": digest(jar), "coordinate": library["name"], "upstreamSha1": item["sha1"]})
    record = {"schemaVersion": 1, "platform": platform, "releaseStatus": "candidate-until-real-client-start",
              "minecraftVersion": "1.8.9", "metadataUrl": VERSION_URL, "metadataSha1": VERSION_SHA1,
              "assetsComplete": assets, "uniqueAssetObjects": len(objects),
              "assetIndexFile": {"path": index_path.relative_to(destination).as_posix(), "sha256": digest(index_path)},
              "javaLibraries": libraries, "nativeArchives": archives,
              "nativeFiles": [{"path": p.relative_to(destination).as_posix(), "sha256": digest(p)} for p in sorted(natives.rglob("*")) if p.is_file()]}
    if len(libraries) != 2:
        raise ValueError("Expected exactly one matched LWJGL Java/util pair.")
    write_json(destination / "runtime-manifest.json", record)
    print(json.dumps({"platform": platform, "runtime": str(destination), "nativeFiles": len(record["nativeFiles"]), "assetsComplete": assets}), flush=True)
    return record


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", required=True, type=Path)
    parser.add_argument("--platform", required=True, choices=PLATFORMS)
    parser.add_argument("--natives-only", action="store_true", help="Inspect another platform's delivery without downloading assets; not launchable.")
    args = parser.parse_args()
    provision(args.destination, args.platform, not args.natives_only)
