#!/bin/sh
# Update gradle/verification-metadata.xml after a dependency changed (a new
# Orchid, a new Android Gradle plugin, a Dependabot pull request), and show
# what was added, for review before it is committed.
#
#     tools/update-verification-metadata.sh            # the tasks CI and releases run
#     tools/update-verification-metadata.sh lintRelease  # and more tasks
#     REUSE_CACHE=1 tools/update-verification-metadata.sh  # faster, see below
#
# Gradle adds a checksum for every new artifact, and a trusted key for every
# new signing key: it trusts what it downloads now.  The review is what makes
# the file worth having: a new key, or a new group, should be one that the
# dependency change explains.  Commit the result on its own, so that the
# diff can be read.
set -eu

cd "$(dirname "$0")/.."
META=gradle/verification-metadata.xml
KEYS=gradle/verification-keyring.keys
# Orchid is published by its maintainer, signed with this key.
ORCHID_KEY=643CD2E8DDCB00F8F158121BDDAE12626E141035

tasks="testDebugUnitTest lintDebug assembleDebug assembleRelease $*"

# As on CI: an empty Gradle home, so that nothing is taken from the cache
# without being downloaded (and so recorded), and --rerun-tasks, so that
# every task runs and resolves what it needs: aapt2, for one, is fetched
# by the tasks that run it, which an up-to-date build skips.  This
# downloads everything, which takes a few minutes; REUSE_CACHE=1 keeps
# the usual Gradle home, and may then miss artifacts.
if [ -z "${REUSE_CACHE:-}" ]; then
    GRADLE_USER_HOME=$(mktemp -d)
    export GRADLE_USER_HOME
fi

before=$(mktemp)
trap 'rm -f "$before"; [ -z "${REUSE_CACHE:-}" ] && rm -rf "$GRADLE_USER_HOME"' EXIT
cp "$META" "$before"

# The keys are exported as text only ($KEYS), which a diff can show; not
# also as the binary verification-keyring.gpg.
if ! grep -q '<keyring-format>' "$META"; then
    sed -i 's|^\( *\)<verify-signatures>true</verify-signatures>|&\n\1<keyring-format>armored</keyring-format>|' "$META"
fi

# pgp,sha256: a checksum even for signed artifacts, so that one whose key
# cannot be fetched still verifies.  --export-keys writes the public keys
# to $KEYS, so that builds (CI, F-Droid) need no key server.  --continue:
# a failing task (a lint error, a test) must not stop the tasks after it
# from resolving their dependencies, or the metadata would miss them.
status=0
./gradlew --no-daemon --continue --rerun-tasks --write-verification-metadata pgp,sha256 --export-keys $tasks || status=$?
rm -f gradle/verification-keyring.gpg

# aapt2: the Android Gradle plugin resolves it in a configuration of its
# own while a task runs, and Gradle does not record it, so the build that
# needs it fails verification (also on CI).  Add its checksums here, for
# the jars of Linux, Windows and macOS (Android Studio on any of them),
# taken from Google's Maven repository, and check the Linux one against
# the copy that the build above downloaded.
GOOGLE=https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2
for dir in "${GRADLE_USER_HOME:-$HOME/.gradle}"/caches/modules-2/files-2.1/com.android.tools.build/aapt2/*/; do
    [ -d "$dir" ] || continue
    v=$(basename "$dir")
    if grep -q "name=\"aapt2\" version=\"$v\"" "$META"; then
        continue
    fi
    echo "== Adding aapt2 $v"
    tmp=$(mktemp -d)
    for f in "aapt2-$v.pom" "aapt2-$v-linux.jar" "aapt2-$v-windows.jar" "aapt2-$v-osx.jar"; do
        curl -sfL -o "$tmp/$f" "$GOOGLE/$v/$f" || { echo "ERROR: cannot download $f" >&2; exit 1; }
    done
    cached=$(find "$dir" -name "aapt2-$v-linux.jar" | head -1)
    if [ -n "$cached" ] && [ "$(sha256sum < "$cached")" != "$(sha256sum < "$tmp/aapt2-$v-linux.jar")" ]; then
        echo "ERROR: aapt2-$v-linux.jar differs between two downloads; do not trust either." >&2
        exit 1
    fi
    python3 -I - "$META" "$v" "$tmp" <<'PY'
import hashlib, os, sys
meta, v, tmp = sys.argv[1:]
lines = ['      <component group="com.android.tools.build" name="aapt2" version="%s">' % v]
for f in sorted(os.listdir(tmp)):
    digest = hashlib.sha256(open(os.path.join(tmp, f), "rb").read()).hexdigest()
    lines += ['         <artifact name="%s">' % f,
              '            <sha256 value="%s" origin="Google Maven, by tools/update-verification-metadata.sh"/>' % digest,
              '         </artifact>']
lines.append('      </component>')
s = open(meta, encoding="utf-8").read()
if "</components>" not in s:
    sys.exit("no </components> in " + meta)
s = s.replace("   </components>", "\n".join(lines) + "\n   </components>", 1)
open(meta, "w", encoding="utf-8").write(s)
PY
    rm -rf "$tmp"
done

echo
echo "== New or changed trusted keys (check each against the dependency it is for)"
diff "$before" "$META" | grep '^>.*trusted-key\|^>.*<trusting' || echo "(none)"
echo
echo "== New components"
diff "$before" "$META" | sed -n 's/^> *<component group="\([^"]*\)" name="\([^"]*\)" version="\([^"]*\)".*/\1:\2:\3/p' || true
echo

# Orchid must stay trusted by its maintainer's key only, for any version.
if ! grep -q "<trusted-key id=\"$ORCHID_KEY\" group=\"org.mhatta\" name=\"orchid\"/>" "$META"; then
    echo "ERROR: $META no longer trusts org.mhatta:orchid by key $ORCHID_KEY alone." >&2
    exit 1
fi
if grep -n 'org[.]mhatta' "$META" | grep -v "$ORCHID_KEY"; then
    echo "ERROR: another entry for org.mhatta (above): Orchid should be verified by its signature only." >&2
    exit 1
fi

# So that "git diff" shows the keyring when it is new.
if [ -f "$KEYS" ]; then
    git add -N "$KEYS" 2>/dev/null || true
fi
echo "Review:   git diff -- $META $KEYS"
if [ "$status" -ne 0 ]; then
    echo
    echo "The build failed (above): fix it and run this again before committing," >&2
    echo "since the tasks that failed may not have resolved all they need." >&2
    exit "$status"
fi
echo "Commit:   git commit -m 'Update the dependency verification metadata' -- $META $KEYS"
