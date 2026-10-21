"""Install observer without changing existing transport or management modules."""
from pathlib import Path
import hashlib
import subprocess
import re
import time
import zipfile
from device import adb, root, archive
project=Path(__file__).resolve().parents[1]
apk=project/'private/releases/qixin-release.apk'
cert=subprocess.check_output(['apksigner','verify','--print-certs',str(apk)],text=True)
fingerprint=re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',cert).group(1)
backup=project/'private/backups'/time.strftime('%Y%m%d-%H%M%S')
backup.mkdir(parents=True,mode=0o700)
paths=['/data/adb/modules/fcm_connectivity_guard','/data/adb/lspd/config']
if root('test -d /data/adb/qixin && echo yes || true').strip():paths.append('/data/adb/qixin')
blob=archive(paths);(backup/'before.tar.gz').write_bytes(blob)
(backup/'sha256.txt').write_text(hashlib.sha256(blob).hexdigest()+'  before.tar.gz\n')
adb('install','--no-incremental','-r',str(apk))
root('mkdir -p /data/adb/qixin; chmod 700 /data/adb/qixin')
if root('test -f /data/adb/qixin/stop.sh && echo yes || true').strip():root('sh /data/adb/qixin/stop.sh')
stage='/data/local/tmp/qixin-install'
root('mkdir -p '+stage+'; chown shell:shell '+stage+'; chmod 700 '+stage)
files={'controller.apk':apk,'libqixin.so':project/'app/src/main/jniLibs/arm64-v8a/libqixin.so'}
for name in ('start.sh','stop.sh','supervise.sh','apply_policy.sh'):files[name]=project/'tools'/name
for name,path in files.items():
    adb('push',str(path),stage+'/'+name)
    root('cp '+stage+'/'+name+' /data/adb/qixin/'+name)
root("printf '%s\\n' '"+fingerprint+"' > /data/adb/qixin/manager.sha256")
cli=project/'private/releases/qixin'
cli.write_text('#!/system/bin/sh\nCLASSPATH=/data/adb/qixin/controller.apk exec app_process /system/bin io.github.wangtong10086.qixin.service.Cli "$@"\n')
adb('push',str(cli),stage+'/qixin')
root('cp '+stage+'/qixin /data/adb/qixin/qixin; chmod 700 /data/adb/qixin/*.sh /data/adb/qixin/qixin; chmod 600 /data/adb/qixin/controller.apk /data/adb/qixin/manager.sha256; rm -r '+stage)
root('mkdir -p /data/adb/modules/qixin_controller')
for name in ('module.prop','service.sh','action.sh','sepolicy.rule'):
    adb('push',str(project/'magisk'/name),'/data/local/tmp/qixin-'+name)
    root('cp /data/local/tmp/qixin-'+name+' /data/adb/modules/qixin_controller/'+name+'; rm /data/local/tmp/qixin-'+name)
root('chmod 755 /data/adb/modules/qixin_controller/*.sh')
root('sh /data/adb/qixin/apply_policy.sh')
root('sh /data/adb/qixin/start.sh')
print(root('/data/adb/qixin/qixin status'))
print('Backup:',backup)
