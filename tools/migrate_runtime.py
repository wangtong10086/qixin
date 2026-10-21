"""Explicit two-boot signing-identity migration, with a symmetric backup rollback.

Run backup_runtime.py first. detach only removes the system overlay and reboots.
install verifies the reboot, removes the old user package, installs the selected
APK, restores the stopped snapshot and stages the new privileged system base.
No PackageManager files or signature checks are edited.
"""
import argparse,hashlib,json,shlex,tarfile
from pathlib import Path
from device import adb,root

project=Path(__file__).resolve().parents[1]
state_file=project/'private/runtime-migration.json'
parser=argparse.ArgumentParser()
parser.add_argument('action',choices=['detach','install','rollback-detach','rollback-install','status'])
parser.add_argument('--candidate',type=Path)
args=parser.parse_args()
q=shlex.quote
module='/data/adb/modules/xmsf_systemizer'
package='com.xiaomi.xmsf'
def save(state):
    state_file.write_text(json.dumps(state,indent=2)+'\n')
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def validate(backup):
    hashes=json.loads((backup/'sha256.json').read_text())
    for name,expected in hashes.items():
        if digest(backup/name)!=expected:raise RuntimeError('Backup checksum mismatch: '+name)
    with tarfile.open(backup/'snapshot.tar.gz','r:gz') as tar:
        names=tar.getnames()
        for required in ('data/user/0/com.xiaomi.xmsf/databases/db','data/adb/modules/xmsf_systemizer/system/priv-app/Xmsf/Xmsf.apk'):
            if required not in names:raise RuntimeError('Missing rollback member: '+required)
if args.action=='status':
    print(state_file.read_text() if state_file.exists() else 'Not started')
    raise SystemExit
if args.action=='detach':
    if state_file.exists():raise RuntimeError('Migration already has state; inspect before starting again')
    backup=Path((project/'private/runtime-backup-path').read_text().strip())
    validate(backup)
    if args.candidate is None:parser.error('--candidate required')
    candidate=args.candidate.resolve()
    state={'phase':'detaching','backup':str(backup),'candidate':str(candidate),'candidateSha256':digest(candidate),
           'serial':adb('get-serialno').strip(),'bootBefore':root('cat /proc/sys/kernel/random/boot_id').strip()}
    save(state)
    root('touch '+module+'/disable')
    adb('reboot')
    print('System overlay disabled; reboot requested. Old user APK remains installed until install phase.')
    raise SystemExit
state=json.loads(state_file.read_text())
if adb('get-serialno').strip()!=state['serial']:raise RuntimeError('ADB target changed')
backup=Path(state['backup']);validate(backup)
if args.action=='rollback-detach':
    state['phase']='rollback-detaching';state['bootBefore']=root('cat /proc/sys/kernel/random/boot_id').strip();save(state)
    root('touch '+module+'/disable')
    adb('reboot');print('Rollback detach reboot requested');raise SystemExit
rollback=args.action=='rollback-install'
if state['phase']!=('rollback-detaching' if rollback else 'detaching'):
    raise RuntimeError('Wrong phase: '+state['phase'])
if root('cat /proc/sys/kernel/random/boot_id').strip()==state['bootBefore']:
    raise RuntimeError('Overlay has not been unmounted by reboot')
if root('getprop sys.boot_completed').strip()!='1':raise RuntimeError('Android is not ready')
if root('test -e /system/priv-app/Xmsf/Xmsf.apk && echo present || true').strip():
    raise RuntimeError('Old system base is still mounted')
if 'RUNNING_UNLOCKED' not in root('dumpsys user'):
    raise RuntimeError('Unlock once before restoring credential-encrypted data')
apk=backup/'installed.apk' if rollback else Path(state['candidate'])
if not rollback and digest(apk)!=state['candidateSha256']:raise RuntimeError('Candidate changed')
state['phase']='rollback-installing' if rollback else 'installing';save(state)
root('am force-stop '+package)
if ('package:'+package) in root('cmd package list packages '+package).splitlines():
    result=adb('uninstall',package)
    if 'Success' not in result:raise RuntimeError(result)
if ('package:'+package) in root('cmd package list packages '+package).splitlines():raise RuntimeError('Old signing identity still installed')
result=adb('install','--no-incremental',str(apk))
if 'Success' not in result:raise RuntimeError(result)
root('am force-stop '+package)
stage='/data/local/tmp/qixin-runtime-restore'
root('mkdir -p '+stage+'; chmod 700 '+stage+'; chown shell:shell '+stage)
adb('push',str(backup/'snapshot.tar.gz'),stage+'/snapshot.tar.gz')
root('chmod 600 '+stage+'/snapshot.tar.gz')
# Android has created the new data roots. Restore data, not PackageManager state.
root('tar -xzf '+stage+'/snapshot.tar.gz -C / data/user/0/'+package+' data/user_de/0/'+package)
uid=root('cmd package list packages -U '+package).strip().split('uid:')[-1]
if not uid.isdigit():raise RuntimeError('Could not resolve new UID')
root('chown -R '+uid+':'+uid+' /data/user/0/'+package+' /data/user_de/0/'+package+'; restorecon -RF /data/user/0/'+package+' /data/user_de/0/'+package)
if rollback:
    root('tar -xzf '+stage+'/snapshot.tar.gz -C / data/adb/modules/xmsf_systemizer')
    root('rm -f /data/adb/qixin/runtime-migrated')
else:
    adb('push',str(apk),stage+'/Xmsf.apk')
    root('cp '+stage+'/Xmsf.apk '+module+'/system/priv-app/Xmsf/Xmsf.apk; chmod 644 '+module+'/system/priv-app/Xmsf/Xmsf.apk')
    root('touch /data/adb/qixin/runtime-migrated')
root('rm -f '+module+'/disable; rm -r '+stage)
state['phase']='rollback-rebooting' if rollback else 'candidate-rebooting';save(state)
adb('reboot')
print('Data restored to UID '+uid+'; reboot requested to load privileged runtime. Acceptance still required.')
