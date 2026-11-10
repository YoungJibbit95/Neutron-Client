"""Assemble an official-launcher version folder from the release-profile JAR."""

import argparse
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
from urllib.request import urlopen
import zipfile

METADATA_URL = "https://piston-meta.mojang.com/v1/packages/d546f1707a3f2b7d034eece5ea2e311eda875787/1.8.9.json"
METADATA_SHA1 = "d546f1707a3f2b7d034eece5ea2e311eda875787"
VERSION = re.compile(r"v[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?\Z")
REQUIRED_CLASSES = (
    "net/minecraft/client/main/Main.class", "me/youngjibbit/Pulsar.class",
    "org/lwjgl/opengl/Display.class", "com/sun/jna/Native.class",
    "oshi/SystemInfo.class", "org/apache/logging/log4j/core/LoggerContext.class",
    "com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService.class",
    "org/reflections/Reflections.class",
)
RESOURCE_ALIASES = {
    "assets/minecraft/Tenacity/shaders/vertex.vsh":
        "assets/minecraft/Tenacity/Shaders/vertex.vsh",
}


def git(*args):
    return subprocess.check_output(["git", *args], text=True, encoding="utf-8").strip()


def version_id(tag):
    if not VERSION.fullmatch(tag):
        raise ValueError("Use a version such as v1.0.0 or v1.0.0-alpha.1")
    return "Neutron-" + tag[1:]


def load_metadata():
    with urlopen(METADATA_URL, timeout=30) as response:
        raw = response.read()
    if hashlib.sha1(raw).hexdigest() != METADATA_SHA1:
        raise ValueError("Minecraft 1.8.9 metadata checksum mismatch")
    return json.loads(raw)


def launcher_manifest(vanilla, identifier, timestamp):
    if vanilla.get("id") != "1.8.9" or vanilla.get("assets") != "1.8":
        raise ValueError("Expected official Minecraft 1.8.9 metadata")
    result = deepcopy(vanilla)
    # All Java dependencies are in the fat JAR. Only native extraction remains
    # with the launcher; vanilla Java libraries would shadow patched versions.
    result["libraries"] = [lib for lib in result["libraries"] if "natives" in lib]
    if not result["libraries"] or not all("downloads" in lib for lib in result["libraries"]):
        raise ValueError("Native download metadata missing")
    result.pop("downloads", None)  # Never let the launcher replace Neutron with vanilla.
    result.pop("logging", None)  # Use the packaged Log4j configuration.
    result.update(id=identifier, type="release", mainClass="net.minecraft.client.main.Main",
                  time=timestamp, releaseTime=timestamp,
                  javaVersion={"component": "jre-legacy", "majorVersion": 8})
    return result


def changelog(subjects, repository, head, previous=None):
    bullets = []
    for subject in subjects:
        subject = re.sub(r"^(?:feat|fix|docs|test|refactor|perf|build|ci|chore)(?:\([^)]*\))?!?:\s*", "", subject.strip(), flags=re.I)
        if not subject or re.search(r"refresh.*traffic.*badge", subject, re.I):
            continue
        # Keep commit text on one line and prevent Markdown/HTML injection.
        subject = re.sub(r"[\x00-\x1f\x7f]", " ", subject)
        subject = re.sub(r"[\\`*_{}\[\]<>]", "", subject)
        subject = subject[:137] + "…" if len(subject) > 140 else subject
        if subject not in bullets:
            bullets.append(subject)
    lines = ["## Changes", "", *["- " + item for item in bullets[:10]]]
    if not bullets:
        lines.append("- Initial release bundle.")
    history = "compare/{}...{}".format(previous, head) if previous else "commits/" + head
    lines += ["", "[Full history](https://github.com/{}/{})".format(repository, history), ""]
    return "\n".join(lines)


def prepare_jar(source, destination):
    with zipfile.ZipFile(source) as jar:
        names = set(jar.namelist())
        missing = set(REQUIRED_CLASSES) - names
        if missing:
            raise ValueError("Incomplete release JAR: " + ", ".join(sorted(missing)))
        if "Start.class" in names or any(n.upper().endswith((".SF", ".RSA", ".DSA")) and n.startswith("META-INF/") for n in names):
            raise ValueError("Test entrypoint or stale dependency signatures in release JAR")
        if "Main-Class: net.minecraft.client.main.Main" not in jar.read("META-INF/MANIFEST.MF").decode():
            raise ValueError("Release JAR entrypoint mismatch")
        if jar.testzip():
            raise ValueError("Corrupt JAR entry")
    shutil.copyfile(source, destination)
    with zipfile.ZipFile(destination, "a", compression=zipfile.ZIP_DEFLATED) as jar:
        for alias, original in RESOURCE_ALIASES.items():
            content = jar.read(original)
            if alias not in names:
                jar.writestr(alias, content)
            elif jar.read(alias) != content:
                raise ValueError("Conflicting shader resource alias: " + alias)


def bundle(jar, output, tag, repository):
    identifier = version_id(tag)
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Expected owner/repository")
    head = git("rev-parse", "HEAD")
    timestamp = git("show", "-s", "--format=%cI", "HEAD")
    # Exclude the current release tag while finding the preceding version.
    tags = git("tag", "--merged", "HEAD", "--sort=-version:refname").splitlines()
    previous = next((t for t in tags if VERSION.fullmatch(t) and t != tag), None)
    revision = previous + "..HEAD" if previous else "HEAD"
    subjects = git("log", "--no-merges", "--format=%s", revision).splitlines()
    manifest = launcher_manifest(load_metadata(), identifier, timestamp)
    output = Path(output)
    output.mkdir(parents=True, exist_ok=False)
    folder = output / identifier
    folder.mkdir()
    prepare_jar(jar, folder / (identifier + ".jar"))
    (folder / (identifier + ".json")).write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    notes = changelog(subjects, repository, head, previous)
    (output / "CHANGELOG.md").write_text(notes, encoding="utf-8")
    (folder / "CHANGELOG.md").write_text(notes, encoding="utf-8")
    archive = output / (identifier + "-bundle.zip")
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as zipped:
        for path in sorted(folder.iterdir()):
            zipped.write(path, path.relative_to(output).as_posix())
    checksummed = [*sorted(folder.iterdir()), archive]
    # Keep the checksum interchange format LF-only on Windows as well.
    (output / "SHA256SUMS.txt").write_bytes("".join(
        hashlib.sha256(p.read_bytes()).hexdigest() + "  " + p.relative_to(output).as_posix() + "\n"
        for p in checksummed).encode("utf-8"))
    summary = {"version": identifier, "commit": head, "previousTag": previous,
               "minecraftMetadataSha1": METADATA_SHA1,
               "jar": (folder / (identifier + ".jar")).as_posix(),
               "archive": archive.as_posix()}
    (output / "build-info.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, default=Path("target/neutron-release.jar"))
    parser.add_argument("--output", type=Path, default=Path("target/release-bundle"))
    parser.add_argument("--tag", required=True)
    parser.add_argument("--repository", default="YoungJibbit95/Neutron-Client")
    args = parser.parse_args()
    print(json.dumps(bundle(args.jar, args.output, args.tag, args.repository)))
