"""Run or validate the production entry using an explicit distribution and runtime."""
import argparse
from pathlib import Path
import subprocess

from client_contract import launch_command


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--distribution", required=True, type=Path)
    parser.add_argument("--runtime", required=True, type=Path)
    parser.add_argument("--java-home", required=True, type=Path)
    parser.add_argument("--game-dir", required=True, type=Path)
    parser.add_argument("--heap-mib", type=int, default=1024)
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()
    command, cwd, env = launch_command(args.distribution, args.runtime, args.java_home,
                                       args.game_dir, args.heap_mib, args.validate_only)
    raise SystemExit(subprocess.call(command, cwd=cwd, env=env))
