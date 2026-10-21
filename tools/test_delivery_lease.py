"""Device-only local fixture. Does not send any cloud message."""
import json,shlex,time
from pathlib import Path
from device import root,adb
PKG='io.github.wangtong10086.qixin.probe'
def rpc(request):return json.loads(root('/data/adb/qixin/qixin rpc '+shlex.quote(json.dumps(request))))
def save(config):return rpc({'command':'config.replace','config':config,'expectedRevision':config['revision']})
def send(mid):return root('am broadcast --user 0 -a com.google.android.c2dm.intent.RECEIVE -n '+PKG+'/.ProbeReceiver --es google.message_id '+mid)
qixu=json.loads(root('/data/adb/fuxicontrol/fuxictl status'))
assert qixu['mode']=='enforce' and qixu['hooks'].get('startup')==True,'Qixu enforcement hook unavailable'
adb('shell','input','keyevent','KEYCODE_HOME')
before=rpc({'command':'config.get'});Path('private/lease-test-config-before.json').write_text(json.dumps(before))
original=before['apps'].get(PKG)
try:
 config=rpc({'command':'config.get'});config['takeover']['delivery']=True;config['apps'][PKG]={'deliveryLease':False};save(config)
 print('Waiting for negative-control configuration',flush=True);time.sleep(34)
 root('am kill '+PKG)
 send('qixin-lease-denied-'+str(time.time_ns()));time.sleep(3)
 pid=root('pidof '+PKG+' || true').strip();print('Without lease pid:',pid,flush=True);assert not pid,'Negative control was not blocked'
 start=int(time.time()*1000)
 config=rpc({'command':'config.get'});config['apps'][PKG]={'deliveryLease':True};save(config)
 print('Waiting for lease configuration',flush=True);time.sleep(34)
 send('qixin-lease-allowed-'+str(time.time_ns()));time.sleep(4)
 rows=rpc({'command':'events','package':PKG,'since':start})['events']
 print('With lease pid:',root('pidof '+PKG+' || true').strip(),flush=True)
 print(json.dumps(rows,ensure_ascii=False,indent=2),flush=True)
 assert any(e['stage']=='delivery-lease' and e['result']=='granted' for e in rows),'No lease granted'
 assert any(e['stage']=='notification' and e['result']=='accepted' for e in rows),'No notification after lease'
 Path('private/lease-test-result.json').write_text(json.dumps(rows))
finally:
 config=rpc({'command':'config.get'})
 if original is None:config['apps'].pop(PKG,None)
 else:config['apps'][PKG]=original
 config['takeover']['delivery']=before['takeover'].get('delivery',False)
 save(config)
 print('Test app configuration restored',flush=True)
