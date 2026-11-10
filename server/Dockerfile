# Paper with the freely available anticheats and PluginHooker. The plugin and the configuration come in as volumes,
# see compose.yaml.

FROM alpine:3.22 AS fetch
RUN apk add --no-cache curl jq
WORKDIR /jars
COPY fetch.sh /usr/local/bin/fetch
# Bump the APIs in gradle/libs.versions.toml along with these
ARG PAPER_VERSION=26.3
ARG PAPER_BUILD=248
ARG GRIM_VERSION=2.3.75-e457d55
ARG INTAVE_VERSION=dev-4f18a06dce40
ARG PLUGINHOOKER_VERSION=2.1.0
RUN fetch fill paper "$PAPER_VERSION" "$PAPER_BUILD" paper.jar \
    && fetch modrinth grimac paper "$GRIM_VERSION" grimac.jar \
    && fetch modrinth intave paper "$INTAVE_VERSION" intave.jar \
    && fetch github DionaMC/PluginHooker "$PLUGINHOOKER_VERSION" "PluginHooker-$PLUGINHOOKER_VERSION.jar" pluginhooker.jar

# A JDK, as PluginHooker attaches an agent to the running server
FROM eclipse-temurin:25-jdk
RUN mkdir /data && chown 1000:1000 /data
COPY --chmod=755 healthcheck.sh /opt/gametest/healthcheck.sh
COPY --chmod=755 start.sh /opt/gametest/start.sh
COPY --from=fetch /jars/ /opt/gametest/
USER 1000:1000
WORKDIR /data
VOLUME /data
EXPOSE 25565
HEALTHCHECK --interval=10s --timeout=5s --start-period=3m --retries=3 CMD ["/opt/gametest/healthcheck.sh"]
ENTRYPOINT ["/opt/gametest/start.sh"]
