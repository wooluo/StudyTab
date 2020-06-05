#!/bin/bash

[ -d "$FEDERATION_CONFIG_DIR" ] || {
  echo "federation conf dir not exists, exit."
  exit 1
}

[ -f "$FEDERATION_CONFIG_DIR/federation-env.sh" ] || {
  echo "federation-env.sh not exists, exit."
  exit 1
}

# shellcheck disable=SC1090
source "${FEDERATION_CONFIG_DIR}"/federation-env.sh

SCRIPTS_DIR=/usr/lib/federation/scripts

# link /etc/hosts to /etc/transwarp/hosts
echo 'umount /etc/hosts'
umount /etc/hosts
echo 'rm -rf /etc/hosts'
rm -rf /etc/hosts
echo 'ln -s /etc/transwarp/conf/hosts /etc/'
ln -s /etc/transwarp/conf/hosts /etc/

[ -d "/etc/federation/generated-conf" ] && {
  rm -rf "/etc/federation/generated-conf"
}

mkdir -p "/etc/federation/generated-conf"
cp "$FEDERATION_CONFIG_DIR"/* "/etc/federation/generated-conf"
[ -x "$SCRIPTS_DIR/generateSecretProperties.sh" ] && {
  $SCRIPTS_DIR/generateSecretProperties.sh
}
sleep 3s
$SCRIPTS_DIR/bootstrap.sh

DEBUG=${DEBUG:-0}
[ $DEBUG -eq 1 ] && {
  echo "Waiting for debug before exit"
  while true
  do
    sleep 10
  done
}