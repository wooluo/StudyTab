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
[ -d upgrade_backup ] || {
  echo "upgrade_backup directory is not found in $DATA_DIR, data has not been backed up properly!"
  exit 1
}
rm -rf backup/ conf/ log/ partitions/ run/
mv upgrade_backup/* .
rm -rf upgrade_backup


#Rollback password policy
echo "Starting rollback for password policy"
cd $DATA_DIR
LDIF_DIR=partitions/schema/ou=schema
FILES="$LDIF_DIR/cn=pwdpolicy/ou=attributetypes/m-oid=1.3.6.1.4.1.42.2.27.8.1.33.ldif $LDIF_DIR/cn=pwdpolicy/ou=attributetypes/m-oid=1.3.6.1.4.1.42.2.27.8.1.32.ldif $LDIF_DIR/cn=adsconfig/ou=attributetypes/m-oid=1.3.6.1.4.1.18060.0.4.1.2.940.ldif $LDIF_DIR/cn=adsconfig/ou=attributetypes/m-oid=1.3.6.1.4.1.18060.0.4.1.2.939.ldif"
ROLLBACK="$LDIF_DIR/cn=adsconfig/ou=objectclasses/m-oid=1.3.6.1.4.1.18060.0.4.1.3.900.ldif $LDIF_DIR/cn=pwdpolicy/ou=objectclasses/m-oid=1.3.6.1.4.1.42.2.27.8.2.1.ldif"

set -x
#modify in pre_upgrade
for FILE in $ROLLBACK
do
  if [ `grep -c "pwdMinClasses" $FILE` -eq '1' ]; then
    echo "Modify abundant ldif file: $FILE"
    sed  '/pwdMinClasses/d' $FILE -i
  fi


  if [ `grep -c "pwdInHistoryDuration" $FILE` -eq '1' ]; then
    echo "Modify abundant ldif file: $FILE"
    sed  '/pwdInHistoryDuration/d' $FILE -i
  fi
done

#Delete files
for FILE in $FILES
do
   if [ -f "$FILE" ]; then
     echo "Deleting abundant file: $FILE"
     rm $FILE
   fi
done
echo "Finishing rollback for password policy"
set +x