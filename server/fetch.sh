#!/bin/sh
# Downloads one pinned jar and checks it against the checksum its source publishes.
#   fetch.sh fill <project> <version> <build> <target>
#   fetch.sh modrinth <project> <loader> <version> <target>
#   fetch.sh github <owner/repository> <tag> <asset> <target>
set -eu

AGENT="CCBlueX/gametest (https://github.com/CCBlueX/gametest)"

download() {
    curl --fail --silent --show-error --location --user-agent "$AGENT" "$@"
}

case "$1" in
    fill)
        build=$(download "https://fill.papermc.io/v3/projects/$2/versions/$3/builds/$4")
        download --output "$5" "$(echo "$build" | jq -r '.downloads["server:default"].url')"
        echo "$(echo "$build" | jq -r '.downloads["server:default"].checksums.sha256')  $5" | sha256sum -c -
        ;;
    modrinth)
        # One version number can stand for several files, one per loader
        file=$(download --get --data-urlencode "loaders=[\"$3\"]" "https://api.modrinth.com/v2/project/$2/version" \
            | jq --arg version "$4" 'map(select(.version_number == $version))[0].files[] | select(.primary)')
        download --output "$5" "$(echo "$file" | jq -r '.url')"
        echo "$(echo "$file" | jq -r '.hashes.sha512')  $5" | sha512sum -c -
        ;;
    github)
        asset=$(download "https://api.github.com/repos/$2/releases/tags/$3" \
            | jq --arg name "$4" '.assets[] | select(.name == $name)')
        download --output "$5" "$(echo "$asset" | jq -r '.browser_download_url')"
        echo "$(echo "$asset" | jq -r '.digest | ltrimstr("sha256:")')  $5" | sha256sum -c -
        ;;
    *)
        echo "unknown source $1" >&2
        exit 1
        ;;
esac
