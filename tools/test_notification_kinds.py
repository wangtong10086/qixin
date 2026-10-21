"""Device regression: service updates cannot flood messages; tasks stay separate."""
import json, shlex, time
from device import root
PKG='io.github.wangtong10086.qixin.probe'
def rpc(**request):
    return json.loads(root('/data/adb/qixin/qixin rpc '+shlex.quote(json.dumps(request))))
def notify(kind, identifier, sequence=0):
    root(f'am broadcast --include-stopped-packages -n {PKG}/.ProbeReceiver --es kind {kind} --ei id {identifier} --ei sequence {sequence}')
notify("cancel-all",0)
for _ in range(10):
    if not rpc(command="states",package=PKG)["states"]:break
    time.sleep(1)
assert not rpc(command="states",package=PKG)["states"], "Stale probe state before test"
start=int(time.time()*1000)
try:
    for i in range(12): notify('service',40,i)
    notify('progress',41,50)
    notify('message',42)
    for _ in range(15):
        messages=rpc(command='messages',package=PKG,since=start)['events']
        states=rpc(command='states',package=PKG)['states']
        if len(messages)==1 and {s['kind'] for s in states}=={'service','task'}:break
        time.sleep(1)
    assert len(messages)==1, f'Expected one message, got {len(messages)}'
    assert len(states)==2 and {s['kind'] for s in states}=={'service','task'}, [(s['kind'],s['stage']) for s in states]
    print('PASS: 12 service updates -> one state; progress -> task; message -> one history entry')
finally:
    for identifier in [40,41,42]:notify('cancel',identifier)
for _ in range(10):
    if not rpc(command='states',package=PKG)['states']:break
    time.sleep(1)
assert not rpc(command='states',package=PKG)['states'], 'Removed notifications left stale state'
print('PASS: notification cancellation clears state')
