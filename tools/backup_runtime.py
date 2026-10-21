"""Take a stopped, verified XMSF snapshot; resume the existing transport afterwards."""
from pathlib import Path
import hashlib,json,shlex,time
from device import adb,root,archive
project=Path(__file__).resolve().parents[1]
out=project/'private/backups'/('xmsf-'+time.strftime('%Y%m%d-%H%M%S'))
out.mkdir(parents=True,mode=0o700)
package='com.xiaomi.xmsf'
base=root('pm path '+package).strip().splitlines()[0].removeprefix('package:')
(out/'installed.apk').write_bytes(adb('exec-out','su -c '+shlex.quote('cat '+shlex.quote(base)),binary=True))
for label,command in {
 'package':'dumpsys package '+package,
 'appops':'cmd appops get '+package,
 'notifications':'dumpsys notification --noredact',
 'idle':'dumpsys deviceidle whitelist',
 'qixin':'/data/adb/qixin/qixin rpc \'{"command":"config.get"}\'',
 'qixu':'/data/adb/fuxicontrol/fuxictl rpc \'{"command":"config.get"}\'',
}.items(): (out/(label+'.txt')).write_text(root(command))
root('am force-stop '+package)
try:
 paths=['/data/user/0/'+package,'/data/user_de/0/'+package,'/data/adb/modules/xmsf_systemizer','/data/adb/lspd/config']
 (out/'snapshot.tar.gz').write_bytes(archive(paths))
 # Stopped SQLite snapshot includes WAL; backup API produces a standalone test fixture.
 target='/data/local/tmp/qixin-xmsf-db'
 root('rm -f '+target)
 root('sqlite3 /data/user/0/'+package+'/databases/db '+shlex.quote('.backup '+target))
 (out/'db').write_bytes(adb('exec-out','su -c '+shlex.quote('cat '+target),binary=True))
 root('rm '+target)
finally:
 root('am startservice --user 0 -n com.xiaomi.xmsf/com.xiaomi.push.service.XMPushService')
manifest={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in out.iterdir() if p.is_file()}
(out/'sha256.json').write_text(json.dumps(manifest,indent=2)+'\n')
(project/'private/runtime-backup-path').write_text(str(out)+'\n')
print('Verified snapshot:',out)
