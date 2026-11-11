"""Ensure the two Linux native-check runtimes, with one signed Ubuntu fallback.

Run through sudo on the Noble CI runner. Running the helper itself as root also
lets its temporary directory cleanup remove files that APT creates as root/_apt.
Importing and testing this module does not require Linux, root, or APT.
"""

import argparse
from contextlib import contextmanager
import os
from pathlib import Path
import subprocess
import sys
import tempfile


PACKAGES = {
    "legacy-ui": (
        "xvfb", "xauth", "x11-xserver-utils", "mesa-utils", "libgl1-mesa-dri",
        "libglx-mesa0", "libglu1-mesa", "libx11-6", "libxext6", "libxrandr2",
        "libxxf86vm1", "libxcursor1", "libxinerama1", "libxi6", "libxtst6",
        "libfontconfig1", "libfreetype6", "fonts-dejavu-core",
    ),
    "render-performance": (
        "xvfb", "xauth", "x11-xserver-utils", "mesa-utils", "libgl1-mesa-dri",
        "libxrandr2", "libxcursor1", "libxinerama1", "libxi6", "libxxf86vm1",
        "fonts-dejavu-core",
    ),
}
ARCHIVE_KEYRING = "/usr/share/keyrings/ubuntu-archive-keyring.gpg"
DIRECT_SOURCES = """Types: deb
URIs: https://archive.ubuntu.com/ubuntu/
Suites: noble noble-updates noble-backports
Components: main restricted universe multiverse
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg

Types: deb
URIs: https://security.ubuntu.com/ubuntu/
Suites: noble-security
Components: main restricted universe multiverse
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
"""
ACQUIRE_OPTIONS = (
    "-o", "Acquire::Retries=2",
    "-o", "Acquire::http::Timeout=20",
    "-o", "Acquire::https::Timeout=20",
)
UPDATE_TIMEOUT_SECONDS = 120
INSTALL_TIMEOUT_SECONDS = 240
QUERY_TIMEOUT_SECONDS = 15


def require_noble_root():
    if sys.platform != "linux" or getattr(os, "geteuid", lambda: -1)() != 0:
        raise RuntimeError("Run this helper with sudo on the Ubuntu 24.04 runner")
    release = {}
    for line in Path("/etc/os-release").read_text(encoding="utf-8").splitlines():
        name, separator, value = line.partition("=")
        if separator:
            release[name] = value.strip().strip('\"\'')
    if release.get("ID") != "ubuntu" or release.get("VERSION_CODENAME") != "noble":
        raise RuntimeError("The native runtime fallback requires Ubuntu Noble")
    if not Path(ARCHIVE_KEYRING).is_file():
        raise RuntimeError("The installed Ubuntu archive signing keyring is missing")


@contextmanager
def isolated_sources(temp_parent):
    # /tmp is traversable by _apt; do not inherit a possibly private TMPDIR.
    # The unique root-owned parent allows traversal but not directory listing.
    with tempfile.TemporaryDirectory(prefix="neutron-native-apt-", dir=temp_parent) as name:
        directory = Path(name)
        directory.chmod(0o711)
        source_parts = directory / "source-parts"
        lists = directory / "lists"
        for child in (source_parts, lists):
            child.mkdir()
            child.chmod(0o755)
        source_file = directory / "ubuntu.sources"
        with source_file.open("w", encoding="ascii", newline="\n") as stream:
            stream.write(DIRECT_SOURCES)
        source_file.chmod(0o644)
        yield (
            "-o", "Dir::Etc::SourceList=" + str(source_file),
            "-o", "Dir::Etc::SourceParts=" + str(source_parts),
            "-o", "Dir::State::Lists=" + str(lists),
        )


def run_apt(runner, arguments, timeout_seconds, source_options=()):
    # GNU timeout bounds the whole process group, including APT's download
    # children. A bare subprocess timeout would only kill the APT parent.
    command = [
        "/usr/bin/timeout", "--signal=TERM", "--kill-after=10s",
        str(timeout_seconds) + "s", "/usr/bin/apt-get",
        *ACQUIRE_OPTIONS, *source_options, *arguments,
    ]
    runner(command, check=True, env={**os.environ, "DEBIAN_FRONTEND": "noninteractive"})


def missing_packages(packages, *, runner=None):
    """Check configured native packages, without depending on APT or its indexes."""
    runner = subprocess.run if runner is None else runner
    options = dict(check=True, capture_output=True, text=True,
                   timeout=QUERY_TIMEOUT_SECONDS, env={**os.environ, "LC_ALL": "C"})
    architecture = runner(["/usr/bin/dpkg", "--print-architecture"], **options).stdout.strip()
    if not architecture or any(character.isspace() for character in architecture):
        raise RuntimeError("Cannot determine the native package architecture")
    # Query the whole installed database: querying an absent package by name
    # makes dpkg-query fail even when the database itself is healthy.
    result = runner([
        "/usr/bin/dpkg-query", "--show",
        "--showformat=${Package}\\t${Architecture}\\t${Status}\\n",
    ], **options)
    installed = set()
    for line in result.stdout.splitlines():
        fields = line.split("\t")
        if len(fields) != 3:
            raise RuntimeError("Cannot read the installed package database")
        package, package_architecture, status = fields
        # A foreign-architecture library, half-configured package, or package
        # requiring reinstallation must never satisfy a native runtime need.
        if package_architecture in (architecture, "all") and status in (
                "install ok installed", "hold ok installed"):
            installed.add(package)
    return tuple(package for package in packages if package not in installed)


def require_installed(packages):
    missing = missing_packages(packages)
    if missing:
        raise RuntimeError("Native runtime packages remain unavailable: " + ", ".join(missing))


def install_runtime(profile, *, runner=None, temp_parent=None):
    # Only fixed profiles are accepted; caller text never enters APT arguments.
    if profile not in PACKAGES:
        raise ValueError("Unknown native runtime profile")
    runner = subprocess.run if runner is None else runner
    temp_parent = Path("/tmp") if temp_parent is None else Path(temp_parent)
    missing = missing_packages(PACKAGES[profile])
    if not missing:
        print("Native runtime packages already installed for %s; no APT download needed."
              % profile, flush=True)
        return
    print("Installing missing native runtime packages: " + ", ".join(missing), flush=True)
    install_arguments = ("install", "--no-install-recommends", "-y", "--", *missing)
    try:
        run_apt(runner, ("update", "--error-on=any"), UPDATE_TIMEOUT_SECONDS)
        run_apt(runner, install_arguments, INSTALL_TIMEOUT_SECONDS)
    except subprocess.CalledProcessError as error:
        print("Runner APT sources failed (exit %s); trying direct signed Ubuntu sources once."
              % error.returncode, file=sys.stderr, flush=True)
    else:
        require_installed(PACKAGES[profile])
        return

    # Fresh lists prevent fallback from installing against stale mirror indexes.
    # Both commands receive the same isolated sources; default sources stay intact.
    with isolated_sources(temp_parent) as options:
        run_apt(runner, ("update", "--error-on=any"), UPDATE_TIMEOUT_SECONDS, options)
        run_apt(runner, install_arguments, INSTALL_TIMEOUT_SECONDS, options)
        require_installed(PACKAGES[profile])


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", choices=tuple(PACKAGES))
    args = parser.parse_args(argv)
    try:
        require_noble_root()
        install_runtime(args.profile)
    except subprocess.CalledProcessError as error:
        print("Native runtime installation failed (exit %s)." % error.returncode,
              file=sys.stderr)
        return error.returncode if 0 < error.returncode < 256 else 1
    except (OSError, RuntimeError, subprocess.TimeoutExpired) as error:
        print("Native runtime installation failed: %s" % error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
