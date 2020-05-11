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