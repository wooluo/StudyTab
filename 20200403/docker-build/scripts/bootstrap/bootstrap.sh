#!/bin/bash

set -x
set -e

trap 'kill $! & wait' TERM

mkdir -p $GUARDIAN_CONF_DIR

# init guardian-ds.properties
source $APACHEDS_HOME/scripts/init-apacheds-config.sh
source $APACHEDS_HOME/scripts/init-log4j-properties.sh

# start apacheds-backend
CLASSPATH=$GUARDIAN_CONF_INTERNAL_DIR:$GUARDIAN_CONF_DIR
MAIN_CLASS=io.transwarp.guardian.apacheds.ApacheDsServer

set +x
for jar in `find $APACHEDS_HOME -name "*.jar"`
do
   CLASSPATH=$CLASSPATH:$jar
done
set -x

[[ $GUARDIAN_LOG_DIR ]] && JAVA_OPTS="$JAVA_OPTS -Dguardian.log.dir=$GUARDIAN_LOG_DIR"
[[ $GUARDIAN_LOG_FILE ]] && JAVA_OPTS="$JAVA_OPTS -Dguardian.log.file=$GUARDIAN_LOG_FILE"
[[ $GUARDIAN_ROOT_LOGGER ]] && JAVA_OPTS="$JAVA_OPTS -Dguardian.root.logger=$GUARDIAN_ROOT_LOGGER"
[[ $GUARDIAN_APACHEDS_AUDIT_FILE ]] && JAVA_OPTS="$JAVA_OPTS -Dguardian.audit.file=$GUARDIAN_APACHEDS_AUDIT_FILE"

java $JAVA_OPTS $APACHEDS_OPTS -cp $CLASSPATH $MAIN_CLASS &
wait
