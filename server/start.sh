#!/bin/sh
# /config holds the files from server/config, /plugins the Gradle output of the plugin.
set -eu

cp -r /config/. .
# A copy, so that a rebuild cannot change the jar under the running server
mkdir -p plugins
cp /plugins/gametest-plugin.jar plugins/gametest-plugin.jar

exec java -Xmx"${MEMORY:-3G}" \
    -XX:+EnableDynamicAgentLoading \
    -Dcom.mojang.eula.agree=true \
    -Dpaper.disableStartupVersionCheck=true \
    -Dterminal.jline=false \
    -Dterminal.ansi=false \
    -jar /opt/gametest/paper.jar --nogui \
    --add-plugin /opt/gametest/pluginhooker.jar \
    --add-plugin /opt/gametest/grimac.jar \
    --add-plugin /opt/gametest/intave.jar
