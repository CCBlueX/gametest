#!/bin/bash
# Healthy when the server answers a status ping: handshake (protocol 0, next state 1) and status request.
exec 3<>/dev/tcp/127.0.0.1/25565 || exit 1
printf '\x0f\x00\x00\x09localhost\x63\xdd\x01\x01\x00' >&3
[ "$(head --bytes=1 <&3 | wc --bytes)" -eq 1 ]
