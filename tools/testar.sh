#!/usr/bin/env bash
# Testa o classificador do "+" no PC (rodar depois do ./build.sh). Frases opcionais como argumentos.
set -e
cd "$(dirname "$0")/.."
JAR=${ANDROID_HOME:-$HOME/Android/Sdk}/platforms/android-34/android.jar
mkdir -p build/teste
${JAVAC:-/usr/lib/jvm/java-8-openjdk-amd64/bin/javac} -source 8 -target 8 -nowarn -encoding UTF-8 -cp build/classes:$JAR -d build/teste tools/teste/Teste.java 2>&1 | grep -v '^Note' || true
java -cp build/teste:build/classes:$JAR com.axolutions.launcher.Teste "$@"
