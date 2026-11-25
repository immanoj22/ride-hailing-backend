#!/usr/bin/env bash
# RideHail Java dev helper. Runs the 8 services directly on the host against localhost infra.
#
#   ./dev.sh dbs           create the 7 per-service Postgres databases (idempotent)
#   ./dev.sh build         mvn clean install for the whole reactor
#   ./dev.sh start [svc..] start all services (or just the named ones) in the background
#   ./dev.sh stop [svc..]  stop all (or named) services
#   ./dev.sh status        show which services are listening
#   ./dev.sh logs <svc>    tail a service's log
#   ./dev.sh run <svc>     run one service in the FOREGROUND (Ctrl-C to stop)
#
# Services: common-service user-service driver-service ride-service
#           notification-service admin-service helperdesk-service socket-service
set -uo pipefail
cd "$(dirname "$0")"
ROOT="$(pwd)"

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
LOGDIR="$ROOT/.dev-logs"; PIDDIR="$ROOT/.dev-pids"; mkdir -p "$LOGDIR" "$PIDDIR"

# name -> "PORT DBNAME"  (DBNAME "-" means the service has no database)
declare -A SVC=(
  [common-service]="3004 -"
  [user-service]="3002 ridehail_user"
  [driver-service]="3001 ridehail_driver"
  [ride-service]="3006 ridehail_ride"
  [notification-service]="3003 ridehail_notification"
  [admin-service]="3005 ridehail_admin"
  [helperdesk-service]="3009 ridehail_helperdesk"
  [socket-service]="8000 ridehail_socket"
)
ALL="common-service user-service driver-service ride-service notification-service admin-service helperdesk-service socket-service"

load_env() { set -a; [ -f local.env ] && source local.env; set +a; }

cmd_dbs() {
  load_env
  for db in ridehail_user ridehail_driver ridehail_admin ridehail_ride ridehail_notification ridehail_helperdesk ridehail_socket; do
    PGPASSWORD="${DB_PASSWORD:-postgres}" psql -h "${DB_HOST:-localhost}" -U "${DB_USER:-postgres}" -tc \
      "SELECT 1 FROM pg_database WHERE datname='$db'" | grep -q 1 \
      || PGPASSWORD="${DB_PASSWORD:-postgres}" psql -h "${DB_HOST:-localhost}" -U "${DB_USER:-postgres}" -c "CREATE DATABASE $db"
    echo "  db ready: $db"
  done
}

cmd_build() { "$JAVA_BIN" -version; mvn clean install -DskipTests; }

start_one() {
  local svc=$1; read -r port db <<<"${SVC[$svc]}"
  if lsof -i ":$port" -sTCP:LISTEN -t >/dev/null 2>&1; then echo "  $svc already on :$port"; return; fi
  load_env
  local env_args=(PORT="$port")
  [ "$db" != "-" ] && env_args+=(DB_NAME="$db")
  # setsid + nohup so the service survives this shell exiting
  setsid nohup env "${env_args[@]}" "$JAVA_BIN" -jar "$ROOT/$svc/target/$svc.jar" \
    > "$LOGDIR/$svc.log" 2>&1 < /dev/null &
  echo $! > "$PIDDIR/$svc.pid"
  echo "  starting $svc on :$port (log: .dev-logs/$svc.log)"
}

cmd_start() {
  local list="${*:-$ALL}"
  for svc in $list; do
    [ -f "$svc/target/$svc.jar" ] || { echo "  !! $svc/target/$svc.jar missing - run ./dev.sh build first"; continue; }
    start_one "$svc"
  done
  echo "waiting for startup..."; sleep 8; cmd_status
}

stop_one() {
  local svc=$1 pid
  if [ -f "$PIDDIR/$svc.pid" ]; then
    pid="$(cat "$PIDDIR/$svc.pid")"
    kill -TERM "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
    echo "  stopped $svc"
    rm -f "$PIDDIR/$svc.pid"
  else
    read -r port _ <<<"${SVC[$svc]}"
    local lp; lp="$(lsof -i ":$port" -sTCP:LISTEN -t 2>/dev/null || true)"
    [ -n "$lp" ] && kill $lp 2>/dev/null && echo "  stopped $svc (by port)" || true
  fi
}
cmd_stop() { for svc in ${*:-$ALL}; do stop_one "$svc"; done; }

cmd_status() {
  for svc in $ALL; do
    read -r port _ <<<"${SVC[$svc]}"
    if lsof -i ":$port" -sTCP:LISTEN -t >/dev/null 2>&1; then echo "  UP    $svc  http://localhost:$port"
    else echo "  down  $svc  (:$port)"; fi
  done
}

cmd_logs() { tail -f "$LOGDIR/$1.log"; }

cmd_run() {
  local svc=$1; read -r port db <<<"${SVC[$svc]}"
  load_env
  local env_args=(PORT="$port")
  [ "$db" != "-" ] && env_args+=(DB_NAME="$db")
  cd "$ROOT/$svc" && exec env "${env_args[@]}" mvn -q spring-boot:run
}

case "${1:-}" in
  dbs)    cmd_dbs ;;
  build)  cmd_build ;;
  start)  shift; cmd_start "$@" ;;
  stop)   shift; cmd_stop "$@" ;;
  status) cmd_status ;;
  logs)   cmd_logs "$2" ;;
  run)    cmd_run "$2" ;;
  *) grep '^#' "$0" | sed 's/^# \{0,1\}//' | head -20 ;;
esac
