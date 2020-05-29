#!/bin/bash

tmp_TOOLS_DIR="$( cd "$( dirname "$( readlink -f "${BASH_SOURCE[0]}" )" )" && pwd )"
if [ -f "$tmp_TOOLS_DIR/util.sh" ]; then
	. "$tmp_TOOLS_DIR/util.sh"
else
	echo "ERROR: File $tmp_TOOLS_DIR/util.sh not found. Please verify your txsql installation" 1>&2
	exit 1
fi

PHX_STATIC_VAR_INIT_OK="N"
PHX_RUNTIME_VAR_INIT_OK="N"

# contants
# zqdai the inconsistent naming is due to historical reasons
TXSQL_INIT_DONE_FLAG="init_done.flag"
TXSQL_MANUAL_MODE_FLAG="__MANUAL_MODE"

PHX_HOME=""
PHX_CONF_DIR=""
PHX_MYSQL_HOME=""
PHX_MYCNF=""
PHX_SQLPROXY_CNF=""
PHX_BINLOGSVR_CNF=""
PHX_MYSQL_CLIENT=""
PHX_MYSQL_DUMP=""
PHX_BINLOGSVR_TOOL=""

LOCALHOST_IP=""
MYSQL_LOCAL_PORT=""
MYSQL_SOCKET=""
SQLPROXY_RW_PORT=""
SQLPROXY_RO_PORT=""
PHX_BINLOGSVR_PORT=""
PHX_MASTER_IP=""

PHX_BINLOGSVR_FULL_PROC_NAME="phxbinlogsvr_phxrpc"
PHX_SQLPROXY_FULL_PROC_NAME="phxsqlproxy_phxrpc"
PHX_MYSQL_FULL_PROC_NAME="mysqld"

PHX_BINLOGSVR_FULL_PATH=""
PHX_SQLPROXY_FULL_PATH=""
PHX_MYSQL_FULL_PATH=""

#zqdai for docker, mysql has to use a password for root access
TXSQL_MYSQL_CRED=""
TXSQL_MYSQL_ADMIN="root"
TXSQL_MYSQL_ADMIN_PASS=""

TXSQL_DATA_DIR=""

debug_echo_all_var () {
	prt_var PHX_HOME
	prt_var PHX_CONF_DIR
	prt_var PHX_MYSQL_HOME
	prt_var PHX_MYCNF
	prt_var PHX_SQLPROXY_CNF
	prt_var PHX_BINLOGSVR_CNF
	prt_var PHX_MYSQL_CLIENT
	prt_var PHX_MYSQL_DUMP
	prt_var LOCALHOST_IP
	prt_var MYSQL_LOCAL_PORT
	prt_var MYSQL_SOCKET
	prt_var SQLPROXY_RW_PORT
	prt_var SQLPROXY_RO_PORT
	prt_var PHX_BINLOGSVR_FULL_PROC_NAME
	prt_var PHX_SQLPROXY_FULL_PROC_NAME
	prt_var PHX_MYSQL_FULL_PROC_NAME
	prt_var PHX_BINLOGSVR_FULL_PATH
	prt_var PHX_SQLPROXY_FULL_PATH
	prt_var PHX_MYSQL_FULL_PATH
	prt_var PHX_MASTER_IP
	prt_var TXSQL_DATA_DIR
}

#############################################################
#                                                           #
# Functions for initialization                              #
#                                                           #
#############################################################

# Prerequisites
#	tmp_TOOLS_DIR has been set with the absolute path of the tools dir
# Output
#	On success, initialize the following var:
#	PHX_HOME
#	PHX_CONF_DIR
#	PHX_MYCNF
#	PHX_SQLPROXY_CNF
#	PHX_BINLOGSVR_CNF
#	PHX_MYSQL_HOME
#	PHX_MYSQL_CLIENT
#	PHX_MYSQL_DUMP
#	PHX_BINLOGSVR_TOOL
#	PHX_BINLOGSVR_FULL_PATH
#	PHX_SQLPROXY_FULL_PATH
#	PHX_MYSQL_FULL_PATH
init_phxsql_dirs () {
	PHX_HOME=$(dirname "$tmp_TOOLS_DIR")
	if [ ! -r "$PHX_HOME/etc" ]; then
		perror "ERROR: file $PHX_HOME/etc not found or permission denied"
		return 1
	fi

	PHX_CONF_DIR="$PHX_HOME/etc"
	PHX_MYCNF="$PHX_CONF_DIR/my.cnf"
	PHX_SQLPROXY_CNF="$PHX_CONF_DIR/phxsqlproxy.conf"
	PHX_BINLOGSVR_CNF="$PHX_CONF_DIR/phxbinlogsvr.conf"
	PHX_MYSQL_HOME="$PHX_HOME/percona.src"
	PHX_MYSQL_CLIENT="$PHX_MYSQL_HOME/bin/mysql"
	PHX_MYSQL_DUMP="$PHX_MYSQL_HOME/bin/mysqldump"
	PHX_BINLOGSVR_TOOL="$PHX_HOME/sbin/phxbinlogsvr_tools_phxrpc"
	PHX_BINLOGSVR_FULL_PATH="$PHX_HOME/sbin/phxbinlogsvr_phxrpc"
	PHX_SQLPROXY_FULL_PATH="$PHX_HOME/sbin/phxsqlproxy_phxrpc"
	PHX_MYSQL_FULL_PATH="$PHX_HOME/sbin/mysqld"

	TXSQL_MYSQL_CRED="$PHX_CONF_DIR/db.properties"

	# ensure all the dirs exist and are read-able
	if [ ! -r "$PHX_MYCNF" -o ! -r "$PHX_BINLOGSVR_CNF" -o ! -r "$PHX_SQLPROXY_CNF" -o ! -r "$PHX_MYSQL_HOME" ]; then
		return 1
	fi

	# ensure all the binaries are executable
	if [ ! -x "$PHX_MYSQL_DUMP" -o ! -x "$PHX_MYSQL_CLIENT" -o ! -x "$PHX_BINLOGSVR_TOOL"  -o ! -x "$PHX_BINLOGSVR_FULL_PATH" -o ! -x "$PHX_SQLPROXY_FULL_PATH"  -o ! -x "$PHX_MYSQL_FULL_PATH" ]; then
		return 1
	fi

	return 0
}

# Prerequisites
#	The following variables must have been set correctly:
#	PHX_SQLPROXY_CNF, PHX_MYCNF, PHX_BINLOGSVR_CNF
# Output
#	On success, the following variables will be initialized:
#	LOCALHOST_IP
#	SQLPROXY_RW_PORT, SQLPROXY_RO_PORT
#	MYSQL_LOCAL_PORT
#	MYSQL_SOCKET
#	TXSQL_DATA_DIR
#	PHX_BINLOGSVR_PORT
load_phxsql_static_conf() {
        if [ ! -r "$PHX_MYCNF" -o ! -r "$PHX_BINLOGSVR_CNF" -o ! -r "$PHX_SQLPROXY_CNF" ]; then
                return 1
        fi

	local phxTmpStr=$(grep -E "^IP" "$PHX_SQLPROXY_CNF" | sed 's/=/ /g')
	tmpExtractDigitsRe="([[:digit:]\.]+)"
	if [[ "$phxTmpStr" =~  $tmpExtractDigitsRe ]]; then
		LOCALHOST_IP=${BASH_REMATCH[1]}
	else
		perror "ERROR: Could not extract localhost IP from $PHX_SQLPROXY_CNF"
		return 1
	fi

	phxTmpStr=$(grep -E "^Port" "$PHX_SQLPROXY_CNF")
	if [[ "$phxTmpStr" =~ $tmpExtractDigitsRe ]]; then
		SQLPROXY_RW_PORT=${BASH_REMATCH[1]}
		SQLPROXY_RO_PORT=$(( SQLPROXY_RW_PORT + 1 ))
	else
		perror "ERROR: Could not extract txsql port information from $PHX_SQLPROXY_CNF"
		return 1
	fi

	#phxTmpStr=$(cat "$PHX_MYCNF" | awk  'BEGIN {mysqld=0} { if (mysqld == 1 && $0 ~"^port") { print $0 } else if ($0 ~ "^\[mysqld\]") { mysqld = 1 } else if ($0 ~ "^\[[a-z]+\]") { mysqld=0 } }' 2>/dev/null)
	phxTmpStr=$(grep -E "^port" "$PHX_MYCNF" | tail -n 1)
	if [[ "$phxTmpStr" =~ $tmpExtractDigitsRe ]]; then
		MYSQL_LOCAL_PORT=${BASH_REMATCH[1]}
	else
		perror "ERROR: Could not extract mysql port information from $PHX_MYCNF"
		return 1
	fi

	phxTmpStr=$(grep -E "^Port" "$PHX_BINLOGSVR_CNF")
	if [[ "$phxTmpStr" =~ $tmpExtractDigitsRe ]]; then
		PHX_BINLOGSVR_PORT=${BASH_REMATCH[1]}
	else
		perror "ERROR: Could not extract binlogsvr port information from $PHX_BINLOGSVR_CNF"
		return 1
	fi

	# zqdai get the txsql top data dir from mysql's data dir
	TXSQL_DATA_DIR=$(grep -E "^datadir =" $PHX_MYCNF | tail -n 1 | cut -d= -f2)
	phxTmpStr=$(dirname $TXSQL_DATA_DIR)
	TXSQL_DATA_DIR=$(dirname $phxTmpStr)
	if [ -z "$TXSQL_DATA_DIR" ]; then
		perror "ERROR: Could not figure out txsql data dir"
		return 1
	fi

	# zqdai get the txsql top data dir from mysql's data dir
	MYSQL_SOCKET=$(grep -E "^socket =" $PHX_MYCNF | tail -n 1 | cut -d= -f2 | sed 's/ //g')
	if [ -z "$MYSQL_SOCKET" ]; then
		perror "ERROR: Could not figure out mysql socket"
		return 1
	fi

	PHX_STATIC_VAR_INIT_OK="Y"
	return 0
}

load_mysql_password() {
	# zqdai if there is no credentials, we will assume root could access txsql without password
        if [ ! -f "$TXSQL_MYSQL_CRED" ]; then
		TXSQL_MYSQL_ADMIN_PASS=""
                return 0
	fi

        if [ ! -r "$TXSQL_MYSQL_CRED" ]; then
		perror "ERROR: I have no prvilege to read mysql credential file $TXSQL_MYSQL_CRED"
                return 1
	fi

	if [ $(grep -c password "$TXSQL_MYSQL_CRED") -eq '0' ]; then
    TXSQL_MYSQL_ADMIN_PASS=$TXSQL_ROOT_PASSWORD
  else
    TXSQL_MYSQL_ADMIN_PASS=$(grep password "$TXSQL_MYSQL_CRED" | cut -d'=' -f 2)
  fi
}

# Prerequisites
#	The following variables must have been set correctly:
#	PHX_BINLOGSVR_TOOL
# Output
#	Set PHX_MASTER_IP with the current master IP address
load_phxsql_runtime_info () {
	if [ ! -x "$PHX_BINLOGSVR_TOOL" ]; then
		perror "ERROR: Access to $PHX_BINLOGSVR_TOOL is denied"
		return 1
	fi
	# output format: get master 172.16.11.84 expire time 1478151187
	local phxTmpStr=$($PHX_BINLOGSVR_TOOL -f GetMasterInfoFromGlobal -h"$LOCALHOST_IP" -p"$PHX_BINLOGSVR_PORT")
	local masterIPExtractReg="([0-9]+.[0-9]+.[0-9]+.[0-9]+)"
	if [[ "$phxTmpStr" =~ $masterIPExtractReg ]]; then
		PHX_MASTER_IP=${BASH_REMATCH[1]}
	else
		#perror "ERROR: Could not read the master IP information"
		return 1
	fi
	PHX_RUNTIME_VAR_INIT_OK="Y"
	return 0
}

# MAIN
init_phx_env () {
	init_phxsql_dirs || return 1
	load_phxsql_static_conf || return 1
	load_mysql_password || return 1
	load_phxsql_runtime_info || return 1
	return 0
}

check_phxenv_static_init () {
	if [ "$PHX_STATIC_VAR_INIT_OK" != "Y" ]; then
		perror "ERROR: txsql environment variables initialization failed"
		return 1
	fi
	return 0
}

check_phxenv_init () {
	if [ "$PHX_RUNTIME_VAR_INIT_OK" != "Y" ]; then
		perror "ERROR: txsql environment variables initialization failed"
		return 1
	fi
	return 0
}

#############################################################
#                                                           #
# Some utility functions                                    #
#                                                           #
#############################################################

# Return values for phx_is_role_running
PHX_RET_ROLE_RUNNING=0
PHX_RET_PORT_FREE=1
PHX_RET_PORT_BUSY=2
PHX_RET_ERR=3
#
# !!!!!!! WARNING !!!!!!!!
# Please note that this function's checking is really strict! The port will be listened only when
# the role is running "in a good status". Sometimes, if the role (say mysql) could not initailize
# correctly, the check will still fail since the port is not openned !!!!!!!!!
#
# Function
# 	Check if role $1 (must be one of mysql, phxsqlproxy, phxbinlogsvr) is running or not
# Returns
# 	PHX_RET_ROLE_RUNNING if it is listening on the specified port as specified in the config files
# 	PHX_RET_PORT_FREE if the port is not in use
# 	PHX_RET_PORT_BUSY if the port is used while the role is not running
#	PHX_RET_ERR other cases
phx_is_role_running() {
	if [ $# -ne 1 ]; then
		return PHX_RET_ERR
	fi

	local lProcPort=0
	local lProcName=""
	case $1 in
		phxbinlogsvr)
			lProcPort="$PHX_BINLOGSVR_PORT"
			lProcName="$PHX_BINLOGSVR_FULL_PROC_NAME"
			;;
		phxsqlproxy)
			lProcPort="$SQLPROXY_RW_PORT"
			lProcName="$PHX_SQLPROXY_FULL_PROC_NAME"
			;;
		mysql)
			lProcPort="$MYSQL_LOCAL_PORT"
			lProcName="$PHX_MYSQL_FULL_PROC_NAME"
			;;
		* )
			return $PHX_RET_ERR
			;;
	esac

	lsof -i:"$lProcPort" >/dev/null 2>&1
	if [ $? -ne 0 ]; then
		return $PHX_RET_PORT_FREE
	fi

	pidof "$lProcName" >/dev/null 2>&1
	if [ $? -eq 0 ]; then
		local lRolePid=$(pidof $lProcName)
		local lUseCnt=$(lsof -i:"$lProcPort" | grep -c "$lRolePid")
		[ "$lUseCnt" -ge 1 ] && return $PHX_RET_ROLE_RUNNING
	fi

	return $PHX_RET_PORT_BUSY
}

# Function
# 	A relatively "loose" check. Precise for most cases.
# Returns
# 	0 if running, 1 if not, 2 on error 
phx_is_role_running_by_name() {
	[ "$#" -ne 1 ]  && return 2

	local lProcBinPath=""
	case $1 in
		phxbinlogsvr)
			lProcBinPath="$PHX_BINLOGSVR_FULL_PATH"
			;;
		phxsqlproxy)
			lProcBinPath="$PHX_SQLPROXY_FULL_PATH"
			;;
		mysql)
			lProcBinPath="$PHX_MYSQL_FULL_PATH"
			;;
		*)
			return 2
			;;
	esac

	local lProcCnt=$(ps -ef | grep -v "grep" | grep -c "$lProcBinPath")
	if [ "$lProcCnt" -gt 0 ]; then
		return 0
	fi

	return 1
}

# Function
# 	Issue an query to a mysql server
# Usage
#	query_mysql <ip> <port> <sql>
# Precondition
#	root must be able to connect to the mysql server host
query_mysql () {
	if [ -z "${TXSQL_MYSQL_ADMIN_PASS}" ]; then
		echo "$3" | $PHX_MYSQL_CLIENT -uroot -h"$1" -P"$2" -s
	else
		echo "$3" | $PHX_MYSQL_CLIENT -uroot -h"$1" -P"$2" -p"${TXSQL_MYSQL_ADMIN_PASS}" -s
	fi
}

# do all the init work
init_phx_env

