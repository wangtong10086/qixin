"""Real-device policy roundtrip and stale-write rejection on the probe only."""
import json,shlex,subprocess
from device import root
package='io.github.wangtong10086.qixin.probe'
def rpc(command,**args):
    return json.loads(root('/data/adb/qixin/qixin rpc '+shlex.quote(json.dumps(dict(command=command,package=package,**args)))))
original=rpc('runtime.application')
try:
    changed=rpc('runtime.application.set',expected=original,settings=dict(original,blocked=not original['blocked']))
    assert changed['blocked']!=original['blocked']
    assert rpc('runtime.application')['blocked']==changed['blocked']
    try:
        rpc('runtime.application.set',expected=original,settings=original)
    except subprocess.CalledProcessError:
        assert rpc('runtime.application')['blocked']==changed['blocked']
    else:raise AssertionError('Stale write was accepted')
finally:
    current=rpc('runtime.application')
    restored=rpc('runtime.application.set',expected=current,settings=original)
    for key in ('type','blocked','clickFallback','notificationOnRegister'):
        assert restored[key]==original[key],key
print('PASS: native policy read/write; stale write rejected; original settings restored')
