"""Build an isolated local source snapshot, never the shared target/ directory."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

from client_contract import child_environment, digest, java_runtime, write_json
from package_client import package


def build(source, workspace, java_home, maven_home, repository=None, offline=False):
    source = Path(source).resolve(strict=True)
    workspace = Path(workspace).resolve()
    if workspace.exists() or workspace.is_relative_to(source):
        raise ValueError("Choose a new build workspace outside the shared repository.")
    java, _, java_properties = java_runtime(java_home)
    if not (Path(java_home) / "bin" / ("javac.exe" if java.name.endswith(".exe") else "javac")).is_file():
        raise ValueError("Build requires a Java 8 JDK, not just a JRE.")
    maven_home = Path(maven_home).resolve(strict=True)
    boot = list((maven_home / "boot").glob("plexus-classworlds-*.jar"))
    if len(boot) != 1 or not (maven_home / "bin/m2.conf").is_file():
        raise ValueError("Select a complete Maven 3 distribution explicitly.")
    selected = [source / "pom.xml", *sorted((source / "src").rglob("*"))]
    source_bytes = sum(p.stat().st_size for p in selected if p.is_file())
    ancestor = workspace.parent
    while not ancestor.exists():
        ancestor = ancestor.parent
    required = source_bytes * 3 + 1024 ** 3 + (2 * 1024 ** 3 if repository else 0)
    if shutil.disk_usage(ancestor).free < required:
        raise ValueError("Insufficient free disk space for isolated build/cache; choose another volume (need at least %d MiB)." % (required // 1024 ** 2))
    # Copy source only: never personal game data, existing target/, or Git credentials.
    workspace.mkdir(parents=True)
    staged = workspace / "source"
    staged.mkdir()
    fingerprints = {}
    for path in selected:
        if path.is_symlink():
            raise ValueError("Source snapshot must not contain symlinks.")
        if path.is_file():
            relative = path.relative_to(source)
            target = staged / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            before = digest(path)
            shutil.copyfile(path, target)
            if before != digest(path) or before != digest(target):
                raise ValueError("Source changed during snapshot: " + str(relative))
            fingerprints[relative.as_posix()] = before
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
    dirty = bool(subprocess.check_output(["git", "status", "--porcelain", "--", "pom.xml", "src"], cwd=source, text=True).strip())
    snapshot_hash = hashlib.sha256(json.dumps(fingerprints, sort_keys=True).encode()).hexdigest()
    write_json(workspace / "source-snapshot.json", {"gitHead": head, "dirty": dirty, "sha256": snapshot_hash, "files": fingerprints})
    temp = workspace / "temp"
    temp.mkdir()
    args = [str(java), "-Xmx1536M", "-Djava.io.tmpdir=" + str(temp), "-Dclassworlds.conf=" + str(maven_home / "bin/m2.conf"),
            "-Dmaven.home=" + str(maven_home), "-Dmaven.multiModuleProjectDirectory=" + str(staged),
            "-cp", str(boot[0]), "org.codehaus.plexus.classworlds.launcher.Launcher", "-B", "-ntp",
            "-Plauncher-dist"]
    if offline:
        args.append("-o")
    if repository:
        args.append("-Dmaven.repo.local=" + str(Path(repository).resolve()))
    args += ["clean", "package"]
    write_json(workspace / "build-command.json", {"arguments": args, "workingDirectory": str(staged), "javaVersion": java_properties["java.runtime.version"], "globalEnvironmentModified": False})
    environment = child_environment(java_home)
    environment.update(TEMP=str(temp), TMP=str(temp))
    with (workspace / "maven-output.txt").open("w", encoding="utf-8") as log:
        result = subprocess.run(args, cwd=staged, env=environment, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError("Maven failed (exit %d); see %s" % (result.returncode, workspace / "maven-output.txt"))
    return package(workspace, workspace / "distribution")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--workspace", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--maven-home", type=Path, required=True)
    parser.add_argument("--repository", type=Path)
    parser.add_argument("--offline", action="store_true")
    a = parser.parse_args()
    build(a.source, a.workspace, a.java_home, a.maven_home, a.repository, a.offline)
