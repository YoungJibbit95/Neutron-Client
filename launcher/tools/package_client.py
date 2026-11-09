"""Package verified Maven outputs; preserve the client UI and legacy path spelling."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import zipfile

from client_contract import ENTRY_POINT, digest, write_json

# Loose Windows resources ignore directory case; JAR resource lookup does not.
# Keep this compatibility alias inside the distribution, without editing GUI code.
RESOURCE_ALIASES = {
    "assets/minecraft/Tenacity/shaders/vertex.vsh": "assets/minecraft/Tenacity/Shaders/vertex.vsh"
}


def add_resource_aliases(jar_path):
    with zipfile.ZipFile(jar_path, "a", compression=zipfile.ZIP_DEFLATED) as jar:
        names = set(jar.namelist())
        for alias, original in RESOURCE_ALIASES.items():
            content = jar.read(original)
            if alias in names:
                if jar.read(alias) != content:
                    raise ValueError("Conflicting resource alias: " + alias)
            else:
                jar.writestr(alias, content)


def package(workspace, destination):
    workspace = Path(workspace).resolve(strict=True)
    destination = Path(destination).resolve()
    if destination.exists():
        raise ValueError("Choose a new distribution directory; existing builds are never replaced.")
    source = workspace / "source"
    snapshot = json.loads((workspace / "source-snapshot.json").read_text(encoding="utf-8"))
    if digest(source / "pom.xml") != snapshot["files"]["pom.xml"]:
        raise ValueError("Build POM changed after snapshot.")
    if "BUILD SUCCESS" not in (workspace / "maven-output.txt").read_text(encoding="utf-8", errors="replace"):
        raise ValueError("Maven build has no success record.")
    jar_source = source / "target/neutron-client.jar"
    with zipfile.ZipFile(jar_source) as jar:
        names = set(jar.namelist())
        if "me/neutron/launcher/ClientBootstrap.class" not in names or "Start.class" in names:
            raise ValueError("Production entry point missing or test entry point packaged.")
    destination.mkdir(parents=True)
    jar_target = destination / "neutron-client.jar"
    shutil.copyfile(jar_source, jar_target)
    add_resource_aliases(jar_target)
    libraries = source / "target/launcher-libraries"
    for path in sorted(libraries.rglob("*.jar")):
        relative = path.relative_to(libraries).as_posix()
        if relative.startswith(("org/lwjgl/lwjgl/", "org/lwjgl/util/")):
            continue  # Official OS-specific bindings are supplied by runtime-manifest.json.
        if "junit" in path.parts or "junit-" in path.name:
            raise ValueError("Test dependency in runtime classpath.")
        target = destination / "libraries" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
    head, dirty = snapshot["gitHead"], snapshot["dirty"]
    jars = [jar_target, *sorted((destination / "libraries").rglob("*.jar"))]
    manifest = {"schemaVersion": 1, "entryPoint": ENTRY_POINT, "minecraftVersion": "1.8.9",
                "assetIndex": "1.8", "java": {"major": 8, "architecture": "x64"},
                "version": "Development-" + head[:7] + ("-local" if dirty else ""),
                "builtAtUtc": datetime.now(timezone.utc).isoformat(),
                "source": {"kind": "local-snapshot", "gitHead": head, "dirty": dirty, "snapshotSha256": snapshot["sha256"]},
                "packaging": {"toolSha256": digest(__file__), "resourceAliases": RESOURCE_ALIASES},
                "classpath": [{"path": p.relative_to(destination).as_posix(), "sha256": digest(p)} for p in jars],
                "requiredPaths": ["gameDir", "assetsDir", "nativesDir"],
                "jvmArgumentTemplates": ["-Djava.library.path={nativesDir}", "-Djava.io.tmpdir={gameDir}/launcher-tmp"],
                "gameArgumentTemplates": ["--gameDir", "{gameDir}", "--assetsDir", "{assetsDir}", "--nativesDir", "{nativesDir}", "--assetIndex", "1.8", "--version", "{version}"],
                "jvmArgumentsByPlatform": {"macos-x64": ["-XstartOnFirstThread"]},
                "platformSupport": {p: "requires-real-client-start" for p in ("windows-x64", "linux-x64", "macos-x64")}}
    write_json(destination / "launch-manifest.json", manifest)
    print(json.dumps({"distribution": str(destination), "sourceSnapshot": snapshot["sha256"], "classpathEntries": len(jars), "resourceAliases": len(RESOURCE_ALIASES)}), flush=True)
    return destination


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", required=True, type=Path)
    parser.add_argument("--destination", required=True, type=Path)
    args = parser.parse_args()
    package(args.workspace, args.destination)
