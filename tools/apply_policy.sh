#!/system/bin/sh
set -eu
/data/adb/magisk/busybox flock -x /data/adb/local-controller-policy.lock magiskpolicy --live 'attribute qixin_socket_type' \
    'type qixin_socket qixin_socket_type' \
    'typeattribute qixin_socket mlstrustedsubject' \
    'allow magisk qixin_socket unix_stream_socket *' \
    'allow system_server qixin_socket unix_stream_socket connectto' \
    'allow appdomain qixin_socket unix_stream_socket connectto'
