#!/usr/bin/env bash
# Compila o APK sem Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
BT=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-34/android.jar
JDK=${JDK:-/usr/lib/jvm/java-17-openjdk-amd64/bin}
JAVAC=${JAVAC:-/usr/lib/jvm/java-8-openjdk-amd64/bin/javac}
rm -rf build && mkdir -p build/gen build/classes build/dex

# chave do Jev fica fora do codigo (secrets.properties, fora do git)
prop() { grep -E "^$1=" secrets.properties 2>/dev/null | cut -d= -f2- || true; }
mkdir -p build/gen/com/axolutions/launcher
cat > build/gen/com/axolutions/launcher/Secrets.java <<JAVA
package com.axolutions.launcher;
final class Secrets {
    static final String OPENROUTER_API_KEY = "$(prop OPENROUTER_API_KEY)";
    static final String CALENDAR_ACCOUNT = "$(prop CALENDAR_ACCOUNT)";
}
JAVA

$BT/aapt2 compile --dir res -o build/res.zip
$BT/aapt2 link -I "$JAR" --manifest AndroidManifest.xml -A assets \
  --java build/gen -o build/base.apk build/res.zip
$JAVAC -source 8 -target 8 -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -encoding UTF-8 -nowarn -Xlint:-options \
  -d build/classes $(find src build/gen -name '*.java')
$BT/d8 --lib "$JAR" --min-api 26 --release --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
$BT/zipalign -f -p 4 build/unsigned.apk build/aligned.apk

KS=$HOME/.android/axo-launcher.keystore
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  $JDK/keytool -genkeypair -keystore "$KS" -storepass axolauncher -keypass axolauncher -alias axo \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Axolutions" >/dev/null 2>&1
fi
$BT/apksigner sign --ks "$KS" --ks-pass pass:axolauncher --key-pass pass:axolauncher --out build/axo-launcher.apk build/aligned.apk
echo "ok: build/axo-launcher.apk ($(du -h build/axo-launcher.apk | cut -f1))"
