"""Build the pinned Qixin runtime fork; never installs or replaces device packages."""
import os
import argparse
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--release", action="store_true")
args = parser.parse_args()

project = Path(__file__).resolve().parents[1]
keys = project / 'private/signing'
env = os.environ.copy()
env.update(
    JAVA_HOME=str(project / 'private/toolchains/jdk-27.0.1'),
    KEYSTORE_FILE=str(keys / 'qixin.p12'),
    STORE_PASSWORD=(keys / 'password').read_text().strip(),
    KEY_PASSWORD=(keys / 'password').read_text().strip(),
    KEY_ALIAS='qixin',
)
# Source archives have no git commit count; supply the pinned release's code.
subprocess.run([
    './gradlew', ':xmsf:assembleNormalRelease' if args.release else ':xmsf:assembleNormalDebug', '-PversionCode=19',
    '--console=plain', '--no-configuration-cache',
], cwd=project / 'runtime/upstream', env=env, check=True)
