#!/usr/bin/env bash
# Instala o Axo no celular e (re)concede as permissoes que a Xiaomi zera a cada update.
# uso: ./instalar.sh  (ou ANDROID_SERIAL=ip:porta ./instalar.sh)
set -euo pipefail
cd "$(dirname "$0")"
A=${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb
export ANDROID_SERIAL=${ANDROID_SERIAL:-$(./conectar.sh)}
P=com.axolutions.launcher
# na primeira vez, guarda como o aparelho estava pra poder desfazer
if [ ! -f estado-original.txt ]; then
  {
    echo "# estado antes do axo-launcher ($(date))"
    echo "home=$($A shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1)"
    for k in accessibility_display_daltonizer_enabled accessibility_display_daltonizer enabled_accessibility_services; do
      echo "$k=$($A shell settings get secure $k)"
    done
    echo "force_fsg_nav_bar=$($A shell settings get global force_fsg_nav_bar)"
  } > estado-original.txt
fi
$A install -r build/axo-launcher.apk | tail -1
$A shell pm grant $P android.permission.WRITE_SECURE_SETTINGS
$A shell pm grant $P android.permission.READ_CALENDAR
$A shell pm grant $P android.permission.WRITE_CALENDAR
$A shell pm grant $P android.permission.READ_CONTACTS
$A shell pm grant $P android.permission.CALL_PHONE
$A shell pm grant $P android.permission.INTERACT_ACROSS_USERS
$A shell appops set $P SYSTEM_ALERT_WINDOW allow
$A shell appops set $P GET_USAGE_STATS allow
$A shell cmd role add-role-holder android.app.role.HOME $P >/dev/null
# servico de gestos: ele mesmo esconde a barra quando liga e devolve os botoes se cair
SVC=$P/$P.GestureService
CUR=$($A shell settings get secure enabled_accessibility_services | tr -d '\r')
case "$CUR" in
  *"$SVC"*) ;;
  null|"") $A shell settings put secure enabled_accessibility_services "$SVC" ;;
  *) $A shell settings put secure enabled_accessibility_services "$CUR:$SVC" ;;
esac
$A shell settings put secure accessibility_enabled 1
echo "ok"
