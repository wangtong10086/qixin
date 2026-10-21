"""Move the existing Vector scope to the integrated runtime; reboot to load it.

Back up the legacy APK/data and Vector database before calling this tool.
This changes only the two push modules, never PackageManager signature checks.
"""
import argparse,shlex
from device import root
parser=argparse.ArgumentParser()
parser.add_argument('direction',choices=['integrated','legacy'])
args=parser.parse_args()
db='/data/adb/lspd/config/modules_config.db'
old='io.github.magisk317.mipush';new='com.xiaomi.xmsf'
target=new if args.direction=='integrated' else old
path=root('pm path '+target).strip().removeprefix('package:')
if not path.startswith('/'):raise RuntimeError('Target package is not installed')
def literal(v):return "'"+v.replace("'","''")+"'"
sql=f'''BEGIN IMMEDIATE;
INSERT OR IGNORE INTO modules(module_pkg_name,apk_path,enabled) VALUES({literal(target)},{literal(path)},0);
UPDATE modules SET apk_path={literal(path)} WHERE module_pkg_name={literal(target)};
INSERT OR IGNORE INTO scope(mid,app_pkg_name,user_id)
 SELECT (SELECT mid FROM modules WHERE module_pkg_name='{new}'),app_pkg_name,user_id
 FROM scope WHERE mid=(SELECT mid FROM modules WHERE module_pkg_name='{old}');
UPDATE modules SET enabled=CASE WHEN module_pkg_name={literal(target)} THEN 1 ELSE 0 END
 WHERE module_pkg_name IN ('{old}','{new}');
COMMIT;'''
root('sqlite3 '+db+' '+shlex.quote(sql))
print('Scope copied; enabled '+target+'. Reboot required.')
