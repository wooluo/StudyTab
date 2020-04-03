#!/bin/bash

source $GUARDIAN_CONF_DIR/guardian-env.sh

SCRIPTS_DIR=/usr/lib/guardian-apacheds/scripts

# link /etc/hosts to /etc/transwarp/hosts
echo 'umount /etc/hosts'
umount /etc/hosts
echo 'rm -rf /etc/hosts'
rm -rf /etc/hosts
echo 'ln -s /etc/transwarp/conf/hosts /etc/'
ln -s /etc/transwarp/conf/hosts /etc/

MASTER_OR_SLAVE=${GUARDIAN_DS_HA_STATUS:-"master"}

if [ "$MASTER_OR_SLAVE" = "master" ]; then
  $SCRIPTS_DIR/bootstrap.sh
else
  echo "Wait 30s for Master ApacheDS server $GUARDIAN_DS_HA_MASTER_HOST:$GUARDIAN_DS_HA_MASTER_PORT is ready"
  $SCRIPTS_DIR/wait-for-it.sh $GUARDIAN_DS_HA_MASTER_HOST:$GUARDIAN_DS_HA_MASTER_PORT -t 30

  if [ $? != 0 ]; then
    echo "Master ApacheDS server $GUARDIAN_DS_HA_MASTER_HOST:$GUARDIAN_DS_HA_MASTER_PORT is not ready, exit."
    exit 1
  fi

  $SCRIPTS_DIR/bootstrap.sh
fi

DEBUG=${DEBUG:-0}
[ $DEBUG -eq 1 ] && {
  echo "Waiting for debug before exit"
  while true
  do
    sleep 10
  done
}