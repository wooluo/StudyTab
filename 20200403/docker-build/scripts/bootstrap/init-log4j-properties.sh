#!/bin/bash

if [ ! -e $GUARDIAN_CONF_DIR/log4j.properties ]; then

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

cp -f $SCRIPT_DIR/../default-conf/log4j-default.properties $GUARDIAN_CONF_DIR/log4j.properties

fi

