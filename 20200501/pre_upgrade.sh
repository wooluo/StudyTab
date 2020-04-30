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

cd $DATA_DIR
mkdir upgrade_backup
[ -d backup ] && cp -r backup/ upgrade_backup/
[ -d conf ] && cp -r conf/ upgrade_backup/
[ -d log ] && cp -r log/ upgrade_backup/
[ -d partitions ] && cp -r partitions/ upgrade_backup/
[ -d run ] && cp -r run/ upgrade_backup/


#Update password policy
echo "Starting Upgrade for password policy"
cd $DATA_DIR
cd partitions

ADD_FILES="schema/ou=schema/cn=adsconfig/ou=attributetypes/m-oid=1.3.6.1.4.1.18060.0.4.1.2.939.ldif
schema/ou=schema/cn=adsconfig/ou=attributetypes/m-oid=1.3.6.1.4.1.18060.0.4.1.2.940.ldif
schema/ou=schema/cn=pwdpolicy/ou=attributetypes/m-oid=1.3.6.1.4.1.42.2.27.8.1.32.ldif
schema/ou=schema/cn=pwdpolicy/ou=attributetypes/m-oid=1.3.6.1.4.1.42.2.27.8.1.33.ldif"

UPDATE_FILE1=schema/ou=schema/cn=pwdpolicy/ou=objectclasses/m-oid=1.3.6.1.4.1.42.2.27.8.2.1.ldif
UPDATE_FILE2=schema/ou=schema/cn=adsconfig/ou=objectclasses/m-oid=1.3.6.1.4.1.18060.0.4.1.3.900.ldif

jarSource=/usr/lib/guardian-apacheds/lib/api-ldap-schema-data-1.0.0-RC1-guardian-3.1.3.jar

set -x
#Add files
for FILE in $ADD_FILES
do
  if [ ! -f "$FILE" ]; then
    jar xvf $jarSource $FILE
    echo "Adding $FILE"
  fi
done

#Change files
if [ `grep -c "pwdMinClasses" $UPDATE_FILE1` -eq '0' ]; then
    sed -i '$a m-may: pwdMinClasses\' $UPDATE_FILE1 
fi

if [ `grep -c "pwdInHistoryDuration" $UPDATE_FILE1` -eq '0' ]; then
    sed -i '$a m-may: pwdInHistoryDuration\' $UPDATE_FILE1
    
fi


if [ `grep -c "pwdMinClasses" $UPDATE_FILE2` -eq '0' ]; then

    sed -i '$i\m-may: ads-pwdMinClasses\' $UPDATE_FILE2
    
fi

if [ `grep -c "pwdInHistoryDuration" $UPDATE_FILE2` -eq '0' ]; then

    sed -i '$i\m-may: ads-pwdInHistoryDuration\' $UPDATE_FILE2
fi
set +x

