# Hide your WireGuard traffic

ObfuscateWG makes WireGuard traffic undetectable (or barely detectable) for internet providers and other men-in-the-middle.

It may prevent unwanted tracking and illegal blocking of your WireGuard connections.

## Concept

The core idea behind ObfuscateWG is that **any protocol can be detected with some accuracy**.

This is the case because detectability of a protocol depends on the signatures of other protocols used within the same
channel.
For instance, if your machine only serves plain HTTP traffic and a fully-encrypted VPN traffic that looks totally
random for an outsider, one may just assume that all non-HTTP traffic is the VPN traffic.
And the criteria may always change.

In order to solve this problem, ObfuscateWG does not implement a specific protocol, but creates a framework which can
import protocol implementations as plugins.
The user decides which protocol to use and it is possible to quickly switch protocols if needed without installing,
deploying or buying a new VPN. 

At the same time, implementing and supporting a fully custom solution is out of the scope of this project,
so it relies on WireGuard, which does all the heavy lifting and adds an additional layer of security.
This project is designed to be a thin layer on top of a widely supported existing ecosystem.

## Network architecture

```text
┌────────────────────────────────┬───────────────────────┬────────────────────────────────┐
│     Client private network     ¦        Internet       ¦    Server private network      │
│                                ¦                       ¦                                │
│         Raw WG traffic         ¦ Obfuscated WG traffic ¦         Raw WG traffic         │
│ [WG client]────────[OWG]═══════╪═══════════════════════╪═══════[OWG]────────[WG server] │
│                      V         ¦                       ¦         V                      │
│              (Shared protocol) ¦                       ¦ (Shared protocol)              │
└────────────────────────────────┴───────────────────────┴────────────────────────────────┘
```

The setup typically requires two ObfuscateWG instances:
1. Obfuscator - accepts connection (or multiple connections) from WireGuard client(s) and connects to the deobfuscator;
2. Deobfuscator - accepts connection (or multiple connections) from obfuscator(s) and connects to the WireGuard server. 

Both instances must use the same protocol implementation,
and the protocol typically uses a key shared via external methods.
[Protocol implementation example](https://github.com/AlexSischin/obfuscate-wg-p-pcc20).

## Application architecture

```text
┌───────────────────────────────────────┐
│          [Protocol api]               │
│          /            \               │
│     [Core]------------[Protocol impl] │
│     /    \                            │
│ [CLI]    [GUI]                        │
└───────────────────────────────────────┘
```

1. Protocol api - top-level abstraction that defines protocol interface.
2. Core - backend that handles networking, configuration and Protocol impl loading at runtime.
3. CLI - supports CLI args, JVM params and environment variables.
4. GUI - native app that supports graphical input and stores settings in file system.
5. Protocol impl - external Protocol api implementation delivered as a JAR file.
[Protocol implementation example](https://github.com/AlexSischin/obfuscate-wg-p-pcc20).

## Test

```shell
./mvnw clean test
```

## Install protocol API to .m2

```shell
./mvnw clean install -N
./mvnw clean install -pl protocol
```

## Build & run CLI

From the project root:

```shell
./mvnw clean package -DskipTests=true -pl cli -am
```

The output JAR will be at `cli/target/obfuscate-wg-cli-*.*.*.jar`.

You may run it like this:

```shell
java -jar cli/target/obfuscate-wg-cli-*.*.*.jar -h
```

Additionally you may build a docker image:

```shell
docker build -t obfuscatewg -f cli/Dockerfile .
```

## Build & run GUI

From the project root:

```shell
./mvnw clean package -DskipTests=true -pl gui -am
```

The output JAR will be at `gui/target/obfuscate-wg-gui-*.*.*.jar`.

You may run it like this:

```shell
java -jar gui/target/obfuscate-wg-gui-*.*.*.jar
```

Additionally you may package a native installer. The process is different for different platforms.

**Debian**:

```shell
VERSION=$(./mvnw -q -f gui/pom.xml help:evaluate -Dexpression=project.version -DforceStdout)
rm -rf gui/target/installer
jpackage \
    --type deb \
    --input gui/target \
    --main-jar obfuscate-wg-gui-${VERSION}.jar \
    --dest gui/target/installer \
    --name ObfuscateWG \
    --app-version ${VERSION} \
    --vendor "Alexei Sischin" \
    --copyright "© 2026 Alexei Sischin. Licensed under the Apache License 2.0." \
    --description "WireGuard proxy for packet obfuscation" \
    --icon gui/src/main/resources/icon/icon.png \
    --linux-package-name obfuscatewg \
    --linux-app-release ${VERSION} \
    --linux-deb-maintainer alexsischin@gmail.com \
    --linux-menu-group "Utility;" \
    --linux-app-category "Utility" \
    --linux-shortcut
```

**Windows**:

```shell
$Version = .\mvnw -q -f gui\pom.xml help:evaluate -Dexpression='project.version' -DforceStdout
Remove-Item -Recurse -Force "gui\target\installer" -ErrorAction SilentlyContinue
jpackage `
    --type msi `
    --input gui/target `
    --main-jar obfuscate-wg-gui-$Version.jar `
    --dest gui/target/installer `
    --name ObfuscateWG `
    --app-version $Version `
    --vendor "Alexei Sischin" `
    --copyright "© 2026 Alexei Sischin. Licensed under the Apache License 2.0." `
    --description "WireGuard proxy for packet obfuscation" `
    --icon gui/src/main/resources/icon/icon.ico `
    --win-menu-group "ObfuscateWG" `
    --win-dir-chooser `
    --win-menu `
    --win-shortcut-prompt `
    --win-shortcut
```

The output installer will be located at `gui/target/installer/*`. 

