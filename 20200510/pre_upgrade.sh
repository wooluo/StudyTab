#!/bin/bash

[ x"$CONF_DIR" = x"" ] && {
  echo "CONF_DIR is not set!"
  exit 1
}
[ -d "$CONF_DIR" ] || {
  echo "$CONF_DIR does not exist!"
  exit 1
}
cd $CONF_DIR
[ -f guardian-ds.properties ] || {
  echo "guardian-ds.properties does not exist in $CONF_DIR!"
  exit 1
}
DATA_DIR=`sed '/^guardian.ds.database.dir=/!d;s/.*=//' guardian-ds.properties`
[ x"$DATA_DIR" = x"" ] && {
  echo "guardian.ds.database.dir is not found in guardian-ds.properties!"
  exit 1
}

# update password policy
echo "Starting Upgrade for password policy"
ADD_PWD_FILES="schema/ou=schema/cn=adsconfig/ou=attributetypes/m-oid=1.3.6.1.4.1.18060.0.4.1.2.939.ldif
schema/ou=schema/cn=adsconfig/ou=attributetypes/m-oid=1.3.6.1.4.1.18060.0.4.1.2.940.ldif
schema/ou=schema/cn=pwdpolicy/ou=attributetypes/m-oid=1.3.6.1.4.1.42.2.27.8.1.32.ldif
schema/ou=schema/cn=pwdpolicy/ou=attributetypes/m-oid=1.3.6.1.4.1.42.2.27.8.1.33.ldif"

UPDATE_PWD_FILES="schema/ou=schema/cn=pwdpolicy/ou=objectclasses/m-oid=1.3.6.1.4.1.42.2.27.8.2.1.ldif
schema/ou=schema/cn=adsconfig/ou=objectclasses/m-oid=1.3.6.1.4.1.18060.0.4.1.3.900.ldif"

UPDATE_PWD_CONF_FILE=conf/ou=config/ads-directoryserviceid=default/ou=interceptors/ads-interceptorid=authenticationinterceptor/ou=passwordpolicies/ads-pwdid=default.ldif

PWD_JAR_SOURCE=$(find /usr/lib/guardian-apacheds/lib -name "api-ldap-schema-data*.jar")

if [ `grep -c "pwdMinClasses" $UPDATE_PWD_CONF_FILE` -eq '0' ]; then
    sed -i '$i\ads-pwdMinClasses: 0\' $UPDATE_PWD_CONF_FILE
fi

if [ `grep -c "pwdInHistoryDuration" $UPDATE_PWD_CONF_FILE` -eq '0' ]; then
    sed -i '$i\ads-pwdInHistoryDuration :0\' $UPDATE_PWD_CONF_FILE
fi

cd $DATA_DIR/partitions

set -x
# add files for password policy
for FILE in $ADD_PWD_FILES
do
  if [ ! -f "$FILE" ]; then
    jar xvf $PWD_JAR_SOURCE $FILE
  fi
done

# change files for password policy
for FILE in $UPDATE_PWD_FILES
do
  if [ `egrep -c "pwdMinClasses|pwdInHistoryDuration" $FILE` -eq '0' ]; then
    jar xvf $PWD_JAR_SOURCE $FILE
  fi
done
set +x

cd $DATA_DIR
mkdir upgrade_backup
[ -d backup ] && cp -r backup/ upgrade_backup/
[ -d conf ] && cp -r conf/ upgrade_backup/
[ -d log ] && cp -r log/ upgrade_backup/
[ -d partitions ] && cp -r partitions/ upgrade_backup/
[ -d run ] && cp -r run/ upgrade_backup/
