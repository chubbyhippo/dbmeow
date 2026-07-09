#!/bin/sh
# setup.sh — build the dbmeow core + Eclipse bundle from source. POSIX sh.
#
#   ./setup.sh              build + test the core, build the Eclipse bundle,
#                           then print the DBeaver dropins install step
#   ./setup.sh --core-only  build + test only the headless core (no Eclipse
#                           target-platform download)
#   ./setup.sh -h           show this help and exit
#
# The core builds and tests headless (Maven, the shared meow behavior suite).
# The plugin/ bundle is built with Tycho, which downloads the Eclipse target
# platform from p2 on first run (hundreds of MB) — see plugin/BUILD.md. The
# adapter is runtime-unverified: drop the jar into DBeaver's dropins/ and
# restart, then open a SQL editor and you are in NORMAL mode.
#
# SPDX-License-Identifier: GPL-3.0-or-later

set -eu

here=$(cd "$(dirname "$0")" && pwd)
cd "$here"

core_only=0
while [ $# -gt 0 ]; do
    case "$1" in
        --core-only) core_only=1 ;;
        -h|--help)   sed -n '2,15p' "$0"; exit 0 ;;
        *) echo "unknown option: $1 (try --help)" >&2; exit 2 ;;
    esac
    shift
done

info() { printf '\033[1;32m==>\033[0m %s\n' "$*"; }

# mise pins java 21 + maven; fall back to it when a PATH mvn is missing or older.
if command -v mvn >/dev/null 2>&1; then MVN="mvn"; else MVN="mise exec -- mvn"; fi

# Behind a TLS-inspecting proxy, Maven needs the system trust store (the p2
# target-platform download in particular). Respect a caller's MAVEN_OPTS.
export MAVEN_OPTS="${MAVEN_OPTS:--Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts}"

info "building + testing the core (headless meow behavior suite)"
$MVN -pl core test

if [ "$core_only" -eq 1 ]; then
    info "core built and tested; skipping the Eclipse bundle (--core-only)"
    exit 0
fi

info "packaging the core jar and staging it on the bundle's Bundle-ClassPath"
$MVN -pl core package -DskipTests
mkdir -p plugin/lib
cp core/target/dbmeow-core-*.jar plugin/lib/dbmeow-core.jar

info "building the Eclipse bundle (downloads the target platform on first run)"
( cd plugin && $MVN package )

jar=$(ls plugin/target/dbmeow-plugin-*.jar 2>/dev/null | head -1 || true)
info "done — drop this bundle into DBeaver's dropins/ and restart:"
printf '    %s\n' "${jar:-plugin/target/dbmeow-plugin-<version>.jar}"
