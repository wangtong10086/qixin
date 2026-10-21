"""Read-only signature preflight. Never uninstalls XMSF or edits package state."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
from device import adb, root

parser=argparse.ArgumentParser()
parser.add_argument('candidate',type=Path)
args=parser.parse_args()
project=Path(__file__).resolve().parents[1]
output=project/'private/runtime-preflight'
output.mkdir(parents=True,exist_ok=True,mode=0o700)

def certificate(path):
    result=subprocess.run(['apksigner','verify','--print-certs',str(path)],check=True,capture_output=True,text=True)
    return re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)',result.stdout)

paths=root('pm path com.xiaomi.xmsf').splitlines()
base=next(p.removeprefix('package:') for p in paths if p.endswith('/base.apk') or p.endswith('/Xmsf.apk'))
installed=output/'installed.apk'
installed.write_bytes(adb('exec-out','su -c '+shlex.quote('cat '+shlex.quote(base)),binary=True))
old=certificate(installed)
new=certificate(args.candidate)
report={
    'package':'com.xiaomi.xmsf',
    'installedSigners':old,
    'candidateSigners':new,
    'candidateSha256':hashlib.sha256(args.candidate.read_bytes()).hexdigest(),
    'sameSigningIdentity':old==new,
    'migrationStatus':'requires-data-and-rollback-validation' if old==new else 'blocked-signature-mismatch',
    'installedChanged':False,
}
(output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
