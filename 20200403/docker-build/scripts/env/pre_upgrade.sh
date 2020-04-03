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
set -x
echo "Starting Upgrade for password policy"
cd $DATA_DIR
LDIF_DIR=partitions/schema/ou=schema

file1=$LDIF_DIR/cn=adsconfig/ou=attributetypes/m-oid=1.3.6.1.4.1.18060.0.4.1.2.939.ldif
file2=$LDIF_DIR/cn=adsconfig/ou=attributetypes/m-oid=1.3.6.1.4.1.18060.0.4.1.2.940.ldif
file3=$LDIF_DIR/cn=pwdpolicy/ou=attributetypes/m-oid=1.3.6.1.4.1.42.2.27.8.1.32.ldif
file4=$LDIF_DIR/cn=pwdpolicy/ou=attributetypes/m-oid=1.3.6.1.4.1.42.2.27.8.1.33.ldif
file5=$LDIF_DIR/cn=pwdpolicy/ou=objectclasses/m-oid=1.3.6.1.4.1.42.2.27.8.2.1.ldif
file6=$LDIF_DIR/cn=adsconfig/ou=objectclasses/m-oid=1.3.6.1.4.1.18060.0.4.1.3.900.ldif

#Add files
if [ ! -f "$file1" ]; then
  cat > $file1 <<EOF
version: 1
dn: m-oid=1.3.6.1.4.1.18060.0.4.1.2.939,ou=attributeTypes,cn=adsconfig,ou=schema
m-singlevalue: TRUE
m-oid: 1.3.6.1.4.1.18060.0.4.1.2.939
m-syntax: 1.3.6.1.4.1.1466.115.121.1.27
objectclass: top
objectclass: metaTop
objectclass: metaAttributeType
m-name: ads-pwdMinClasses
creatorsname: uid=admin,ou=system
m-equality: integerMatch
m-length: 0
EOF
echo "Added ads-pwdMinClasses ldif file"
fi


if [ ! -f "$file2" ]; then
  cat > $file2 <<EOF
version: 1
dn: m-oid=1.3.6.1.4.1.18060.0.4.1.2.940,ou=attributeTypes,cn=adsconfig,ou=schema
m-singlevalue: TRUE
m-oid: 1.3.6.1.4.1.18060.0.4.1.2.940
m-syntax: 1.3.6.1.4.1.1466.115.121.1.27
objectclass: top
objectclass: metaTop
objectclass: metaAttributeType
m-name: ads-pwdInHistoryDuration
creatorsname: uid=admin,ou=system
m-equality: integerMatch
m-length: 0
EOF
echo "Added ads-pwdInHistoryDuration ldif file"
fi


if [ ! -f "$file3" ]; then
  cat > $file3 <<EOF
version: 1
dn: m-oid=1.3.6.1.4.1.42.2.27.8.1.32,ou=attributeTypes,cn=pwdpolicy,ou=schema
m-singlevalue: TRUE
m-oid: 1.3.6.1.4.1.42.2.27.8.1.32
m-syntax: 1.3.6.1.4.1.1466.115.121.1.27
objectclass: metaTop
objectclass: metaAttributeType
objectclass: top
m-name: pwdMinClasses
creatorsname: uid=admin,ou=system
m-equality: integerMatch
m-length: 0
EOF
echo "Added pwdMinClasses ldif file"
fi


if [ ! -f "$file4" ]; then
  cat > $file4 <<EOF
version: 1
dn: m-oid=1.3.6.1.4.1.42.2.27.8.1.33,ou=attributeTypes,cn=pwdpolicy,ou=schema
m-singlevalue: TRUE
m-oid: 1.3.6.1.4.1.42.2.27.8.1.33
m-syntax: 1.3.6.1.4.1.1466.115.121.1.27
objectclass: metaTop
objectclass: metaAttributeType
objectclass: top
m-name: pwdInHistoryDuration
creatorsname: uid=admin,ou=system
m-equality: integerMatch
m-length: 0
EOF
echo "Added pwdInHistoryDuration ldif file"
fi

#Change files
if [ `grep -c "pwdMinClasses" $file5` -eq '1' ]; then

    echo "Found pwdMinClasses line"
else
    echo "No pwdMinClasses line, adding config in Apacheds"
    sed -i '$a m-may: pwdMinClasses\' $file5
fi

if [ `grep -c "pwdInHistoryDuration" $file5` -eq '1' ]; then

    echo "Found pwdInHistoryDuration line"
else
    echo "NO pwdInHistoryDuration line, adding config in Apacheds"
    sed -i '$a m-may: pwdInHistoryDuration\' $file5
fi


if [ `grep -c "pwdMinClasses" $file6` -eq '1' ]; then

    echo "Found pwdMinClasses line"
else
    echo "No pwdMinClasses line, adding config in Apacheds"
    sed -i '$i\m-may: ads-pwdMinClasses\' $file6
fi

if [ `grep -c "pwdInHistoryDuration" $file6` -eq '1' ]; then

    echo "Found pwdInHistoryDuration line"
else
    echo "NO pwdInHistoryDuration line, adding config in Apacheds"
    sed -i '$i\m-may: ads-pwdInHistoryDuration\' $file6
fi

echo "Finishing Upgrade for password policy"