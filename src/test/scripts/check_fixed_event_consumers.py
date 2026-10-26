"""After mvn clean test, rerun consumers against the current integrated classes. No source snapshots or checkouts."""
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
reports = sorted((ROOT / "target/surefire-reports").glob("TEST-*.xml"))
assert reports, "Run mvn clean test first"
properties = {p.attrib["name"]: p.attrib.get("value", "") for p in ET.parse(reports[0]).findall("./properties/property")}
classpath = properties["java.class.path"]
java = Path(os.environ["JAVA_HOME"]) / "bin/java.exe"
base = [str(java), "-Duser.home=" + str(ROOT / "target/test-home"), "-cp", classpath]
subprocess.run(base + ["org.junit.runner.JUnitCore",
    "me.youngjibbit.core.network.ScaffoldPipelineIntegrationTest",
    "me.youngjibbit.core.world.ScaffoldPlacementTest",
    "me.youngjibbit.module.impl.render.SessionStatisticsEventIntegrationTest",
    "me.youngjibbit.module.impl.render.SessionInfoLifecycleTest"], cwd=ROOT / "target", check=True)
subprocess.run(base + ["me.youngjibbit.module.impl.render.SessionStatisticsCheck"], cwd=ROOT / "target", check=True)
