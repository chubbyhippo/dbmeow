# Building the dbmeow Eclipse bundle

The adapter is an OSGi bundle built with [Tycho](https://github.com/eclipse-tycho/tycho).
It embeds the tested core engine (`dbmeow-core`) on its `Bundle-ClassPath`
rather than recompiling it, so the bundle and the headless test suite share
exactly one copy of the meow logic.

## Steps

```sh
cd /mnt/c/play/configs-claude/dbmeow

# 1. build + test the core, producing the jar to embed
mise exec -- mvn -pl core package

# 2. stage the core jar where the manifest's Bundle-ClassPath expects it
mkdir -p plugin/lib
cp core/target/dbmeow-core-0.1.0-SNAPSHOT.jar plugin/lib/dbmeow-core.jar

# 3. build the bundle (resolves the Eclipse target platform from p2)
cd plugin
MAVEN_OPTS="-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts" \
  mise exec -- mvn package
```

The bundle lands at
`plugin/target/io.github.chubbyhippo.dbmeow-0.1.0-SNAPSHOT.jar` (the
artifactId must equal the Bundle-SymbolicName — Tycho's validate-id).
`../setup.sh` installs it: DBeaver never reconciles `dropins/`, so the
script copies the jar into
`plugins/io.github.chubbyhippo.dbmeow_<Bundle-Version>.jar` and registers
it in `configuration/org.eclipse.equinox.simpleconfigurator/bundles.info`
(a plain Eclipse IDE still accepts the jar via `dropins/`). Restart
DBeaver, open a SQL editor, and you are in NORMAL mode.

## Notes / status

- **Step 3 downloads the Eclipse 2024-03 target platform** (hundreds of MB)
  from `download.eclipse.org` the first time. Behind this machine's
  TLS-inspecting proxy that needs the `MAVEN_OPTS` trust-store shown above.
  First completed 2026-07-10: the platform resolved and the bundle built
  (~2 min once cached).
- The core (`../core`) is fully unit-tested headless (`mvn test`, 236 specs).
  The adapter classes use only APIs verified against the vrapper source
  (cited file:line in each class) but have **not** been exercised inside a
  running DBeaver — that needs the IDE. Treat the adapter as v1: compiles
  against the target platform (verified 2026-07-10), runtime-unverified.
- `lib/dbmeow-core.jar` is a build artifact and is git-ignored; step 2
  regenerates it.
- Formatting is Spotless (google-java-format, AOSP style — 4-space indent):
  `mvn spotless:apply` rewrites, and the check goal is bound to `verify` in
  both the root pom and this standalone one, so `mvn test` stays fast and
  `mvn verify`/`mvn package` (which runs verify's earlier phases only —
  the gate fires on `verify` and beyond) enforces the format.
- The `.dbmeowrc` defaults ride inside the embedded core jar (the core
  packages it as a classpath resource), so `~/.dbmeowrc` overriding works the
  same as the siblings with nothing extra to install.
