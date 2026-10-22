#!/bin/bash
# Container healthcheck: GET the readiness probe on the management port through Bash's /dev/tcp, so the image needs
# no curl or wget. Exits 0 only when the status line is HTTP 200; a refused connection, any other status or no
# answer within 3 seconds exits 1. Optional argument: management port (default 8081).

PORT="${1:-8081}"

status_line=$(timeout 3 bash -c '
    exec 3<>"/dev/tcp/127.0.0.1/$1" || exit 1
    printf "GET /actuator/health/readiness HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n" >&3
    IFS= read -r line <&3 || exit 1
    printf "%s" "$line"
' healthcheck "$PORT") || exit 1

[[ "$status_line" =~ ^HTTP/1\.[01]\ 200 ]] || exit 1
exit 0
