#!/system/bin/sh
while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 2; done
# Other root modules also replace live policy during boot. A busy policy read is transient.
exec >> /data/adb/qixin/boot.log 2>&1
attempt=0
until sh /data/adb/qixin/apply_policy.sh; do
    attempt=$((attempt+1))
    if [ "$attempt" -ge 6 ]; then echo 'SELinux policy setup failed; controller not started'; exit 1; fi
    sleep 5
done
sh /data/adb/qixin/start.sh
