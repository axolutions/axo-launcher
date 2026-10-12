#!/usr/bin/env bash
# Acha o celular pela rede (a porta da depuracao Wi-Fi muda) e conecta. Imprime o serial.
A=${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb
cur=$($A devices | awk 'NR>1 && $2=="device"{print $1; exit}')
if [ -n "$cur" ]; then echo "$cur"; exit 0; fi
for p in $($A mdns services | grep -oE "[0-9.]+:[0-9]+" | sort -u); do $A connect "$p" >/dev/null 2>&1; done
$A devices | awk 'NR>1 && $2=="device"{print $1; exit}'
