#!/bin/bash

TXSQL_DB_PASSWORD=111
TXSQL_MYSQL_CRED="db.properties"
echo "line break"
echo -e "\n" >> application.properties

if [ -n "$TXSQL_DB_PASSWORD" ]; then
    TXSQL_MYSQL_ADMIN_PASS=$TXSQL_DB_PASSWORD
  else
    TXSQL_MYSQL_ADMIN_PASS=222
  fi

echo -e "$TXSQL_MYSQL_ADMIN_PASS" >> application.properties
