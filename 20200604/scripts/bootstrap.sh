#!/bin/bash

function wait_db() {
set +x
TXSQL_READY=false
txsqlServerArr=(${TXSQL_SERVERS//,/ })
for i in `seq 1 10`
do
  echo "Sleep 10s before the $i time try."
  sleep 10s
  for server in ${txsqlServerArr[@]}
  do
    [[ $server =~ ^.+:[0-9]+$ ]] || (echo "Invalid host port format, skip it" && continue)
    echo "Wait 60s for MySQL server $server is ready"
    dockerize -wait tcp://$server -wait-retry-interval 5s -timeout 60s
    if [ $? != 0 ]; then
      echo "MySQL server $server didn't respond in 60s. Try next one."
    else
      hostPortArr=(${server//:/ })
      echo "Create database $MYSQL_DATABASE "
      mysql -h ${hostPortArr[0]} -P ${hostPortArr[1]} \
        -u${FEDERATION_SERVICE_DATASOURCE_USERNAME} -p${FEDERATION_SERVICE_DATASOURCE_PASSWORD} \
        -e "CREATE DATABASE IF NOT EXISTS $MYSQL_DATABASE CHARSET utf8;" \
        && TXSQL_READY=true && echo "The $i time try of $server succeeded." && break
      echo "The $i time try of $server failed."
    fi
  done
  if [ x"$TXSQL_READY" = x"true" ]; then
    break
  fi
done
set -x
if [ x"$TXSQL_READY" = x"false" ]; then
  echo "Txsql servers failed to respond in 60s. Abort starting."
  exit 1
fi
}

set -x

if [ x"${FEDERATION_SERVICE_DATASOURCE_URL}" = x"" ]; then
  export FEDERATION_SERVICE_DATASOURCE_URL="jdbc:mysql://${TXSQL_SERVERS}/${MYSQL_DATABASE}?allowMultiQueries=true&useUnicode=true&characterEncoding=utf8&autoReconnect=true&failOverReadOnly=false"
fi

# create database if not exists

wait_db

set -e

if [ x${JAVA_HOME} != x"" ]; then
  JAVA=${JAVA_HOME}/bin/java
else
  JAVA=java
fi

# confd files imported from base image were removed in the processing of building image
echo "Generate configurations"
confd -onetime

FINAL_CONF_DIR=${FEDERATION_CONFIG_DIR}
[ -d "/etc/federation/generated-conf" ] && {
  FINAL_CONF_DIR=/etc/federation/generated-conf
}
# start guardian federation
CLASSPATH=${FINAL_CONF_DIR}
MAIN_CLASS=io.transwarp.guardian.federation.GuardianFederationBootApplication

set +x
for jar in `find /usr/lib/federation -name "*.jar"`
do
   CLASSPATH=$CLASSPATH:$jar
done

set -x

$JAVA $JAVA_OPTS $FEDERATION_OPTS -cp $CLASSPATH $MAIN_CLASS