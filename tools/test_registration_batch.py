"""Device regression using only the disposable local probe; never batch real apps."""
import json,shlex,time
from pathlib import Path
from device import adb,root
PKG='io.github.wangtong10086.qixin.probe'
def rpc(**request):return json.loads(root('/data/adb/qixin/qixin rpc '+shlex.quote(json.dumps(request))))
def app():return next(x for x in rpc(command='apps')['apps'] if x['package']==PKG)
def config_patch(excluded):
 config=rpc(command='config.get');config['apps'][PKG]={'pushExcluded':excluded}
 rpc(command='config.replace',config=config,expectedRevision=config['revision'])
def run():
 rpc(command='registration.batch',package=PKG)
 for _ in range(20):
  state=rpc(command='registration.status')
  if not state['running']:return state['items']
  time.sleep(.5)
 raise AssertionError('Batch did not finish')
assert not adb('shell','pm','list','packages',PKG).strip(),'Preserve existing probe installation'
before=rpc(command='config.get')
try:
 print(adb('install','--no-incremental','-r','probe/build/outputs/apk/debug/probe-debug.apk'),flush=True)
 rpc(command='refresh')
 assert app()['pushEvidence'].get('mipush')
 assert run()[0]['state']=='manual_required','Stopped app must not wake'
 adb('shell','am','start','-W','-n',PKG+'/.ProbeActivity');adb('shell','input','keyevent','3')
 rpc(command='refresh')
 settings=rpc(command='runtime.application',package=PKG)
 allowed=dict(settings,type=2,blocked=False)
 rpc(command='runtime.application.set',package=PKG,expected=settings,settings=allowed)
 result=run();assert result[0]['state']=='request_sent',result
 time.sleep(1)
 receipt=root('cat /data/user/0/'+PKG+'/shared_prefs/registration-test.xml')
 assert 'name="requests" value="1"' in receipt,receipt
 assert 'name="payloadBytes" value="0"' not in receipt,receipt
 top=next(s for s in adb('shell','dumpsys','activity','activities').splitlines() if 'topResumedActivity=' in s)
 assert PKG not in top,top
 print('PASS: stopped app skipped; receiver got one payload; UI stayed on launcher',flush=True)
 config_patch(True);assert run()==[]
 assert not any(x['package']=='com.v2ray.ang' for x in rpc(command='apps')['apps'])
 print('PASS: excluded probe not queued; v2rayNG absent',flush=True)
 config_patch(False)
 root('am force-stop '+PKG);rpc(command='refresh');assert run()[0]['state']=='manual_required'
 assert 'name="requests" value="1"' in root('cat /data/user/0/'+PKG+'/shared_prefs/registration-test.xml')
 previous=rpc(command='registration.status')['id']
 plan=rpc(command='registration.batch',dryRun=True)['items']
 assert rpc(command='registration.status')['id']==previous,'Dry run must not start or replace a job'
 assert all(x['state']=='manual_required' for x in plan if x['channel']=='fcm')
 print('PASS: no forced wake and no external FCM registration claim',flush=True)
finally:
 current=rpc(command='config.get')
 if PKG in before['apps']:current['apps'][PKG]=before['apps'][PKG]
 else:current['apps'].pop(PKG,None)
 rpc(command='config.replace',config=current,expectedRevision=current['revision'])
 if adb('shell','pm','list','packages',PKG).strip():adb('uninstall',PKG)
 root("sqlite3 /data/user/0/com.xiaomi.xmsf/databases/db \"delete from REGISTERED_APPLICATION where pkg='"+PKG+"';\"")
 rpc(command='history.clear',package=PKG);rpc(command='refresh')
