"""Restore the separately backed-up legacy push hook after a runtime rollback."""
from pathlib import Path
import hashlib,json,shlex,subprocess,sys
from device import root,adb
project=Path(__file__).resolve().parents[1]
backup=Path((project/'private/legacy-hook-backup-path').read_text().strip())
for name,digest in json.loads((backup/'sha256.json').read_text()).items():
    if hashlib.sha256((backup/name).read_bytes()).hexdigest()!=digest:raise RuntimeError('Backup checksum mismatch: '+name)
pkg='io.github.magisk317.mipush'
adb('install','--no-incremental','-r',str(backup/'module.apk'))
root('am force-stop '+pkg)
stage='/data/local/tmp/qixin-legacy-restore.tar.gz'
adb('push',str(backup/'data.tar.gz'),stage)
root('tar -xzf '+stage+' -C / data/user/0/'+pkg+' data/user_de/0/'+pkg)
uid=root('cmd package list packages -U '+pkg).strip().split('uid:')[-1]
if not uid.isdigit():raise RuntimeError('Cannot resolve restored UID')
root('chown -R '+uid+':'+uid+' /data/user/0/'+pkg+' /data/user_de/0/'+pkg+'; restorecon -RF /data/user/0/'+pkg+' /data/user_de/0/'+pkg+'; rm '+stage)
# Restore the saved scope even if rollback removed the integrated module entry.
path=root('pm path '+pkg).strip().removeprefix('package:')
sql="INSERT OR IGNORE INTO modules(module_pkg_name,apk_path,enabled) VALUES ('"+pkg+"','"+path+"',0);"
for row in json.loads((backup/'scope.json').read_text()):
    target=row['app_pkg_name'].replace("'","''")
    sql+="INSERT OR IGNORE INTO scope(mid,app_pkg_name,user_id) VALUES ((SELECT mid FROM modules WHERE module_pkg_name='"+pkg+"'),'"+target+"',"+str(int(row['user_id']))+");"
root('sqlite3 /data/adb/lspd/config/modules_config.db '+shlex.quote(sql))
subprocess.run([sys.executable,str(project/'tools/switch_runtime_hooks.py'),'legacy'],check=True)
print('Legacy hook restored. Reboot to load it.')
