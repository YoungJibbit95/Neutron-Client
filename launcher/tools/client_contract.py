"""Developer validation tools for the v1 client distribution (Python 3 stdlib)."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ENTRY_POINT = "me.neutron.launcher.ClientBootstrap"
PLATFORMS = ("windows-x64", "linux-x64", "macos-x64")


def digest(path, algorithm="sha256"):
    result = hashlib.new(algorithm)
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(64 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def write_json(path, value):
    Path(path).write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def child_environment(java_home):
    env = os.environ.copy()
    # Configure this child only. Avoid inherited JVM injection and Maven RC scripts.
    for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "MAVEN_OPTS", "MAVEN_ARGS"):
        env.pop(name, None)
    env["JAVA_HOME"] = str(java_home)
    env["MAVEN_SKIP_RC"] = "1"
    return env


def java_runtime(java_home):
    java_home = Path(java_home).resolve(strict=True)
    java = java_home / "bin" / ("java.exe" if os.name == "nt" else "java")
    result = subprocess.run([str(java), "-XshowSettings:properties", "-version"],
                            capture_output=True, text=True, env=child_environment(java_home), timeout=30)
    properties = dict(re.findall(r"^\s*([\w.]+) = (.+)$", result.stderr, re.MULTILINE))
    if result.returncode or properties.get("java.specification.version") != "1.8":
        raise ValueError("Select a Java 8 runtime explicitly.")
    if properties.get("sun.arch.data.model") != "64" or properties.get("os.arch") not in ("amd64", "x86_64"):
        raise ValueError("The client requires an x64 Java process (also on Apple Silicon).")
    os_name = properties.get("os.name", "")
    system = "windows" if os_name.startswith("Windows") else "macos" if os_name.startswith("Mac") else "linux" if os_name.startswith("Linux") else None
    if system is None:
        raise ValueError("Unsupported Java operating system.")
    return java, system + "-x64", properties


def relative_file(root, value):
    root = Path(root).resolve(strict=True)
    relative = Path(value)
    if relative.is_absolute() or ".." in relative.parts or "\\" in value or ":" in value:
        raise ValueError("Distribution paths must be safe relative POSIX paths.")
    target = (root / relative).resolve(strict=True)
    if not target.is_relative_to(root) or not target.is_file():
        raise ValueError("Distribution file escapes its root or is missing.")
    return target


def verified_files(root, records):
    paths = []
    for item in records:
        path = relative_file(root, item["path"])
        if digest(path) != item["sha256"]:
            raise ValueError("Distribution integrity check failed: " + item["path"])
        paths.append(path)
    return paths


def launch_command(distribution, runtime, java_home, game_dir, heap_mib=1024, validate_only=False):
    distribution = Path(distribution).resolve(strict=True)
    runtime = Path(runtime).resolve(strict=True)
    game_dir = Path(game_dir).resolve(strict=True)
    if not game_dir.is_dir():
        raise ValueError("Working directory must already exist.")
    manifest = json.loads((distribution / "launch-manifest.json").read_text(encoding="utf-8"))
    delivery = json.loads((runtime / "runtime-manifest.json").read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != 1 or delivery.get("schemaVersion") != 1:
        raise ValueError("Unsupported manifest version.")
    if manifest.get("entryPoint") != ENTRY_POINT or manifest.get("assetIndex") != "1.8":
        raise ValueError("Unsupported client entry point or asset index.")
    java, platform, _ = java_runtime(java_home)
    if delivery.get("platform") != platform:
        raise ValueError("Selected natives do not match the Java process platform.")
    if not 1024 <= heap_mib <= 4096:
        raise ValueError("Developer smoke-test heap must be between 1024 and 4096 MiB.")
    classpath = verified_files(runtime, delivery["javaLibraries"]) + verified_files(distribution, manifest["classpath"])
    verified_files(runtime, delivery["nativeFiles"])
    verified_files(runtime, [delivery["assetIndexFile"]])
    # Check every official asset before starting, including offline starts.
    assets = relative_file(runtime, delivery["assetIndexFile"]["path"]).parent.parent
    index = json.loads((assets / "indexes/1.8.json").read_text(encoding="utf-8"))
    for asset in index["objects"].values():
        h = asset["hash"]
        if not re.fullmatch(r"[0-9a-f]{40}", h):
            raise ValueError("Invalid asset digest.")
        path = assets / "objects" / h[:2] / h
        if not path.is_file() or path.stat().st_size != asset["size"] or digest(path, "sha1") != h:
            raise ValueError("Asset missing or corrupt: " + h)
    native_dir = (runtime / "natives").resolve(strict=True)
    temporary = game_dir / "launcher-tmp"
    temporary.mkdir(exist_ok=True)
    if not temporary.resolve().is_relative_to(game_dir):
        raise ValueError("Temporary directory escapes the isolated game directory.")
    args = [str(java), "-Xms256M", "-Xmx%dM" % heap_mib,
            "-Djava.io.tmpdir=" + str(temporary), "-Djava.library.path=" + str(native_dir)]
    if platform == "macos-x64":
        args.append("-XstartOnFirstThread")
    args += ["-cp", os.pathsep.join(str(p) for p in classpath), ENTRY_POINT,
             "--gameDir", str(game_dir), "--assetsDir", str(assets),
             "--nativesDir", str(native_dir), "--assetIndex", "1.8",
             "--version", manifest["version"], "--username", "LauncherSmoke",
             "--width", "960", "--height", "640"]
    if validate_only:
        args.append("--validateOnly")
    environment = child_environment(Path(java_home).resolve())
    environment.update(TEMP=str(temporary), TMP=str(temporary))
    return args, game_dir, environment
