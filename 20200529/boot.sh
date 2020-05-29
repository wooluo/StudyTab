#!/bin/bash

function echoWithTimestamp()
{
	local ts=$(date "+%Y%m%d %H:%M:%S")
	echo "$ts" "$1"
}

echoWithTimestamp "Info: starting TxSQL"

cp $CONF_DIR/db.properties /db.properties
echo "db.password=$TXSQL_ROOT_PASSWORD" >> /db.properties
DB_PASSWD_FILE=/db.properties
INSTALL_CONF="$CONF_DIR/install_conf.sh"
INIT_DONE_FLAG=""
MYSQL_DIR=/usr/bin/txsql/percona.src/bin
TOOLS_DIR=/usr/bin/txsql/tools
TXSQL_MANAGE_UTIL="$TOOLS_DIR/txsql.sh"

source "$TOOLS_DIR/util.sh"

[ -f /external/scripts/init.sh ] && {
  . /external/scripts/init.sh
  [ $? -ne 0 ] && echoWithTimestamp "Error: external init script returned non-zero exit code"
}

set -x

# zqdai get data dir and set up INIT_DONE_FLAG
tmp_DATA_DIR=$(grep "DATA_DIR" ${INSTALL_CONF} | cut -d'=' -f 2)
#remove all spaces in tmp_DATA_DIR
tmp_DATA_DIR=${tmp_DATA_DIR//[[:blank:]]/}
INIT_DONE_FLAG="${tmp_DATA_DIR}/init_done.flag"

if [ "$SMOKE_TEST" != "Y" ]; then
	echo 'umount /etc/hosts'
	umount /etc/hosts
	echo 'rm -rf /etc/hosts'
	rm -rf /etc/hosts
	echo 'ln -sf /etc/transwarp/conf/hosts /etc/'
	ln -s /etc/transwarp/conf/hosts /etc/
fi

source $CONF_DIR/txsql-env.sh
source "$INSTALL_CONF"

# resolveHosts can only be called after sourcing all the deploy configure files
resolveHosts
if [ $? -ne 0 ]; then
	echoWithTimestamp "Error: failed to resolve hosts"
	exit 1
fi
LOCAL_IP=$(CheckNodeIP)
if [[ ! $LOCAL_IP =~ $IP_REGEX ]]; then
	echoWithTimestamp "Error: failed to resolve LOCAL_IP"
	exit 1
fi

cp $DB_PASSWD_FILE /usr/bin/txsql/etc

cd /usr/bin/txsql/tools
# check whether we are deploying txsql or just restart it
DeployInProgress=N
tmpInitDone=$(${TXSQL_MANAGE_UTIL} check is_init_done)
if [ "$tmpInitDone" != "Y" ]; then
	DeployInProgress=Y
fi
nohup ./docker_main.sh "$@" 2>&1 >${LOG_DIR}/txsql.out &
PID=$!

for i in {1..600}; do
    if timeout 1 bash -c "echo > /dev/tcp/${LOCAL_IP}/${SQL_RW_PORT}"; then
	echoWithTimestamp "Info: port ${SQL_RW_PORT} is opened"
        break
    fi

    if [ "$i" = "600" ]; then
	echoWithTimestamp "Error: waiting timeout, port ${SQL_RW_PORT} is not accessible"
    fi

    sleep 1
done

# root password is initialized in two phases: first for txsql nodes and then for all other machines
IsInManualMode=$(${TXSQL_MANAGE_UTIL} check is_manual_mode)
RootPasswdInitPhase=0
MasterIP=

DBPASS=""
if [ $(grep -c password "$DB_PASSWD_FILE") -eq '0' ]; then
  DBPASS=$TXSQL_ROOT_PASSWORD
else
  DBPASS=`grep password "$DB_PASSWD_FILE" | cut -d'=' -f 2`
fi

set +x
if [ "$DBPASS" = "" ]; then
  echoWithTimestamp "Fatal Error: password for the root user of txsql is empty, please check your config"
  exit 1
fi
set -x
echoWithTimestamp "Info: initialize root password (phase 1)"
for i in {1..600}; do
  set -x
  if [ "$IsInManualMode" = "Y" ]; then
	  break
  fi

  if [ "$i" = "600" -a "$RootPasswdInitPhase" -lt 1 ]; then
      echoWithTimestamp "Fatal Error: root password initialization phase 1 failed"
      echoWithTimestamp "Fatal Error: waiting timeout, TxSQL is running with errors"
      break
  fi

  if [ ! -f "$INIT_DONE_FLAG" ]; then
      echoWithTimestamp "Info: initialization in process ($INIT_DONE_FLAG does not exist), waiting"
      sleep 1
      continue
  fi

  set +x
  echoWithTimestamp "Info: verifying root password..."
  if "$MYSQL_DIR"/mysql -h ${LOCAL_IP} -P${SQL_RW_PORT} -u root "-p$DBPASS" -e "SELECT 1 FROM dual"; then
    set -x
    RootPasswdInitPhase=1
    echoWithTimestamp "Info: root password initialization phase 1 ok"
    break
  fi

  # It is checked that DBPASS is not empty, so if we could log in using no password, then we must in the deploy process
  # Initialize the root password for txsql nodes
  if "$MYSQL_DIR"/mysql -h ${LOCAL_IP} -P${SQL_RW_PORT} -u root -e "SELECT 1 FROM dual"; then
    # zqdai only executes below logic on master node
    MasterIP=$("$TXSQL_MANAGE_UTIL" list master | egrep -o $IP_REGEX)
    if [[ ! "$MasterIP" =~ $IP_REGEX ]]; then
	    echoWithTimestamp "Info: this TxSQL node has been initialized, but master election is not done"
	    sleep 1
	    continue
    fi

    echoWithTimestamp "Info: master is $MasterIP"
    if [ ! "$MasterIP" = "${LOCAL_IP}" ]; then
	    # this is not master node
	    echoWithTimestamp "Info: I am not master. waiting for master to initialize root password"
	    sleep 1
	    continue
    fi

    echoWithTimestamp "Info: initializing database password (commands hidden) ..."
    # master node, change password
    # zqdai just need to provide the new password, admin names and old password would
    # use the default values: root and "", which are correct for us
    set +x
    ${TXSQL_MANAGE_UTIL} passwd -P "$DBPASS"
    if [ $? -ne 0 ]; then
        echoWithTimestamp "Error: initialize root password phase 1 failed, retrying"
        sleep 5
        continue
    fi
    set -x
    echoWithTimestamp "Info: root password initialization phase 1 ok"
    echoWithTimestamp "Info: sleeping 10 seconds to let the new password propagate to other nodes"
    sleep 10
    # also need to set it here since this may be the last iteration of this loop
    RootPasswdInitPhase=1
  fi

  sleep 1
done

# if we are in the deploy process, this is the master node and root password has been initailized successfully for phase 1
# then initialize root@'%' password. retry 3 times
if [ "$DeployInProgress" = "Y" -a "$MasterIP" = "${LOCAL_IP}" -a "$RootPasswdInitPhase" = "1" ]; then
    for i in 1 2 3
    do
        echoWithTimestamp "Info: initialize root password for other machines (commands hidden)"
	set +x
        "$MYSQL_DIR"/mysql -h ${LOCAL_IP} -P${SQL_RW_PORT} -u root -p"$DBPASS" -e "
SET PASSWORD FOR 'root'@'${LOCAL_IP}' = PASSWORD('$DBPASS');
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' IDENTIFIED BY '$DBPASS' WITH GRANT OPTION;
FLUSH PRIVILEGES;
"
        [ $? -eq 0 ] && RootPasswdInitPhase=2
	set -x
	if [ "$RootPasswdInitPhase" -eq 2  ]; then
            echoWithTimestamp "Info: initialize root password phase 2 ok"
	    break;
	else
            if [ "$i" -lt 3 ]; then
                echoWithTimestamp "Error: failed to initialize root password for other machines. Retry $i"
	    else
                echoWithTimestamp "Fatal Error: failed to initialize root password for other machines. Please change it manually!"
            fi
	fi
	sleep 2
    done
fi

set -x

if [ "$IsInManualMode" = "N" ]; then
    if [ "$DeployInProgress" = "Y" ]; then
        if [ "$RootPasswdInitPhase" = "2" ]; then
            echoWithTimestamp "Info: TxSQL is deployed successfully"
	else
            echoWithTimestamp "Error: TxSQL is running with errors"
        fi
    else
        if [ "$RootPasswdInitPhase" = "1" ]; then
            echoWithTimestamp "Info: TxSQL is started"
	else
            echoWithTimestamp "Error: TxSQL is running with errors"
        fi
    fi
else
    echoWithTimestamp "Info: TxSQL running in manual mode"
fi

wait $PID

