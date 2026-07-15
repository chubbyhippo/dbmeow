#!/bin/sh
# setup.sh — build and install the dbmeow Eclipse bundle. POSIX sh.
#
#   ./setup.sh              build + test the core, build the Eclipse bundle,
#                           then install it into every detected DBeaver
#   ./setup.sh --core-only  build + test only the headless core (no Eclipse
#                           target-platform download)
#   ./setup.sh --build-only build the core and bundle, install nothing
#   ./setup.sh --skip-build install the already-built bundle
#   ./setup.sh --list       show detected DBeaver installations and exit
#   ./setup.sh --dbeaver-dir DIR   install into DIR instead of auto-detecting
#   ./setup.sh -h           show this help and exit
#
# The core builds and tests headless (Maven, the shared meow behavior suite).
# The plugin/ bundle is built with Tycho, which downloads the Eclipse target
# platform from p2 on first run (hundreds of MB) — see plugin/BUILD.md.
# DBeaver never reconciles dropins/, so installing means copying the jar
# into plugins/ and registering it in configuration/org.eclipse.equinox
# .simpleconfigurator/bundles.info. Restart DBeaver, open a SQL editor, and
# you are in NORMAL mode.
#
# SPDX-License-Identifier: GPL-3.0-or-later

set -eu

here=$(cd "$(dirname "$0")" && pwd)
cd "$here"

bsn=io.github.chubbyhippo.dbmeow

core_only=0 do_build=1 do_install=1 list_only=0
explicit_dir=""

while [ $# -gt 0 ]; do
    case "$1" in
        --core-only)   core_only=1 ;;
        --build-only)  do_install=0 ;;
        --skip-build)  do_build=0 ;;
        --list)        list_only=1 ;;
        --dbeaver-dir) shift; explicit_dir="${1:?--dbeaver-dir needs a path}" ;;
        -h|--help)     sed -n '2,20p' "$0"; exit 0 ;;
        *) echo "unknown option: $1 (try --help)" >&2; exit 2 ;;
    esac
    shift
done

info() { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mwarn:\033[0m %s\n' "$*" >&2; }

nl='
'

is_dbeaver_dir() {
    [ -f "$1/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info" ]
}

detect_dbeaver_dirs() {
    for d in /usr/share/dbeaver* /opt/dbeaver* "/Applications/DBeaver.app/Contents/Eclipse"; do
        is_dbeaver_dir "$d" && printf '%s\n' "$d"
    done
    if grep -qi microsoft /proc/version 2>/dev/null; then
        for d in /mnt/c/Users/*/scoop/apps/dbeaver/current "/mnt/c/Program Files/DBeaver"*; do
            is_dbeaver_dir "$d" && printf '%s\n' "$d"
        done
    fi
    return 0
}

if [ -n "$explicit_dir" ]; then
    targets=$explicit_dir
else
    targets=$(detect_dbeaver_dirs | sort -u)
fi

if [ "$list_only" -eq 1 ]; then
    info "detected DBeaver installations:"
    if [ -n "$targets" ]; then
        printf '%s\n' "$targets" | sed 's/^/  /'
    else
        echo "  (none found)"
    fi
    exit 0
fi

# mise pins java 21 + maven; prefer the pins, fall back to a PATH mvn without mise.
if command -v mise >/dev/null 2>&1; then MVN="mise exec -- mvn"; else MVN="mvn"; fi

# Behind a TLS-inspecting proxy, Maven needs the system trust store (the p2
# target-platform download in particular). Respect a caller's MAVEN_OPTS.
export MAVEN_OPTS="${MAVEN_OPTS:--Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts}"

if [ "$do_build" -eq 1 ]; then
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
fi

jar=$(ls plugin/target/$bsn-*.jar 2>/dev/null | head -1 || true)
[ -n "$jar" ] || { echo "no plugin/target/$bsn-*.jar — run without --skip-build" >&2; exit 1; }

version=$(unzip -p "$jar" META-INF/MANIFEST.MF | tr -d '\r' | sed -n 's/^Bundle-Version: *//p' | head -1)
[ -n "$version" ] || { echo "no Bundle-Version in $jar" >&2; exit 1; }

if [ "$do_install" -eq 0 ]; then
    info "done — bundle at $jar (Bundle-Version $version)"
    exit 0
fi

install_into() {
    dir=$1
    if ! is_dbeaver_dir "$dir"; then
        warn "$dir does not look like a DBeaver installation (no simpleconfigurator bundles.info)"
        return 0
    fi
    set +f
    stale_removed=1
    rm -f "$dir/plugins/${bsn}_"*.jar "$dir/dropins/$bsn"*.jar 2>/dev/null || stale_removed=0
    set -f
    if [ "$stale_removed" -eq 0 ]; then
        warn "$dir: a loaded dbmeow jar is locked — close DBeaver and rerun"
        return 0
    fi
    if ! cp "$jar" "$dir/plugins/${bsn}_$version.jar"; then
        warn "$dir: cannot write into plugins/ — close DBeaver and rerun"
        return 0
    fi
    bi="$dir/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
    tmp="$bi.dbmeow"
    grep -v "^$bsn," "$bi" > "$tmp" || true
    [ -n "$(tail -c1 "$tmp")" ] && echo >> "$tmp"
    printf '%s\n' "$bsn,$version,plugins/${bsn}_$version.jar,4,false" >> "$tmp"
    mv "$tmp" "$bi"
    info "installed into $dir"
    installed=$((installed + 1))
}

installed=0
if [ -z "$targets" ]; then
    warn "no DBeaver installation detected."
    warn "install manually: copy $jar to <dbeaver>/plugins/${bsn}_$version.jar and add"
    warn "  $bsn,$version,plugins/${bsn}_$version.jar,4,false"
    warn "to <dbeaver>/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
fi
old_ifs=$IFS
IFS=$nl
set -f
for dir in $targets; do
    install_into "$dir"
done
set +f
IFS=$old_ifs

echo
info "done."
if [ "$installed" -gt 0 ]; then
    echo "  * restart DBeaver to load the bundle"
    echo "  * open a SQL editor and you are in NORMAL mode; SPC ? shows the cheatsheet"
fi
