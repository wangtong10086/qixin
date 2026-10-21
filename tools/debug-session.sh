#!/system/bin/sh
set -eu
umask 077
BASE=/data/adb/qixin
STATE=$BASE/debug-session
case "${1:-}" in
 begin)
  if [ -d "$STATE" ]; then echo 'Debug session already active'; exit 1; fi
  previous=$(settings get global stay_on_while_plugged_in)
  case "$previous" in null|0|1|2|3|4|5|6|7) ;; *) echo 'Cannot read stay-awake setting' >&2; exit 1 ;; esac
  mkdir -m 700 "$STATE"
  printf '%s\n' "$previous" > "$STATE/previous"
  cat /proc/sys/kernel/random/uuid > "$STATE/id"
  settings put global stay_on_while_plugged_in 2
  nohup sh "$0" expire "$(cat "$STATE/id")" > "$STATE/watchdog.log" 2>&1 </dev/null &
  echo 'USB stay-awake enabled for at most 30 minutes; lock credentials unchanged'
  ;;
 expire)
  sleep 1800
  [ -f "$STATE/id" ] || exit 0
  [ "$(cat "$STATE/id")" = "$2" ] || exit 0
  sh "$0" end
  ;;
 end)
  [ -d "$STATE" ] || exit 0
  if [ "$(settings get global stay_on_while_plugged_in)" = 2 ]; then
   previous=$(cat "$STATE/previous")
   if [ "$previous" = null ]; then settings delete global stay_on_while_plugged_in; else settings put global stay_on_while_plugged_in "$previous"; fi
  fi
  rm -rf "$STATE"
  ;;
 *) echo 'Usage: debug-session.sh begin|end' >&2; exit 2 ;;
esac
