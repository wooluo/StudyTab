#!/bin/bash

if [ ! -e $GUARDIAN_CONF_DIR/guardian-ds.properties ]; then

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
echo "generate guardian-ds.properties"
cat > $SCRIPT_DIR/guardian-ds.properties <<EOF
guardian.ds.database.dir=${GUARDIAN_DATA_DIR:-"/var/lib/guardian-apacheds/data"}
guardian.ds.domain=${GUARDIAN_DS_DOMAIN:-"dc=tdh"}
guardian.ds.linux.id.start=5000
guardian.ds.ldap.host=${GUARDIAN_DS_LDAP_HOST:-"localhost"}
guardian.ds.ldap.port=${GUARDIAN_DS_LDAP_PORT:-"10389"}
guardian.ds.ldap.tls.enabled=${GUARDIAN_DS_LDAP_TLS_ENABLED:-"false"}
guardian.ds.ldap.key.store=${GUARDIAN_DS_LDAP_KEY_STORE:-"not_used"}
guardian.ds.ldap.key.store.pw=${GUARDIAN_DS_LDAP_KEY_STORE_PW:-"not_used"}

guardian.ds.realm=${GUARDIAN_DS_REALM:-"TDH"}
guardian.ds.kdc.port=${GUARDIAN_DS_KDC_PORT:-"1088"}
guardian.ds.kdc.clock.skew=300000
guardian.ds.kdc.ticket.lifetime=86400000
guardian.ds.kdc.renew.lifetime=604800000
guardian.ds.krb.keyring.enable=false
guardian.ds.kdc.encryption.types=aes128-cts-hmac-sha1-96, des-cbc-md5, des3-cbc-sha1-kd
EOF

if [[ $GUARDIAN_DS_ROOT_PASSWORD_STORE ]]; then
  cat >> $SCRIPT_DIR/guardian-ds.properties <<EOF
guardian.ds.root.password.store=$GUARDIAN_DS_ROOT_PASSWORD_STORE
EOF
else
  cat >> $SCRIPT_DIR/guardian-ds.properties <<EOF
guardian.ds.root.password=${GUARDIAN_DS_ROOT_PASSWORD:-"Transwarp!"}
EOF
fi

cat >> $SCRIPT_DIR/guardian-ds.properties <<EOF
guardian.ds.admin.username=admin
EOF

if [[ $GUARDIAN_DS_ADMIN_PASSWORD_STORE ]]; then
  cat >> $SCRIPT_DIR/guardian-ds.properties <<EOF
guardian.ds.admin.password.store=$GUARDIAN_DS_ADMIN_PASSWORD_STORE
EOF
else
  cat >> $SCRIPT_DIR/guardian-ds.properties <<EOF
guardian.ds.admin.password=${GUARDIAN_DS_ADMIN_PASSWORD:-"123"}
EOF
fi

cat >> $SCRIPT_DIR/guardian-ds.properties <<EOF
guardian.ds.ha.enabled=${GUARDIAN_DS_HA_ENABLED:-"false"}
# if true use hostname, else use ip address
guardian.ds.ha.use.hostname=true
guardian.ds.ha.znode.parent=guardian
guardian.ds.partition.sync.on.write=${GUARDIAN_DS_PARTITION_SYNC_ON_WRITE:-"false"}
EOF

if [[ x"${GUARDIAN_DS_HA_ENABLED}" == x"true" ]]; then
  cat >> $SCRIPT_DIR/guardian-ds.properties <<EOF
# master or slave
guardian.ds.ha.status=${GUARDIAN_DS_HA_STATUS:-"master"}
EOF
  if [[ x"${GUARDIAN_DS_HA_STATUS}" == x"slave" ]]; then
    cat >> $SCRIPT_DIR/guardian-ds.properties <<EOF
guardian.ds.ha.user.dn=${GUARDIAN_DS_HA_USER_DN:-"uid=admin,ou=system"}
EOF
    if [[ ${GUARDIAN_DS_HA_USER_PASSWORD_STORE} ]]; then
      cat >> ${SCRIPT_DIR}/guardian-ds.properties <<EOF
guardian.ds.ha.user.password.store=${GUARDIAN_DS_HA_USER_PASSWORD_STORE}
EOF
    else
      cat >> ${SCRIPT_DIR}/guardian-ds.properties <<EOF
guardian.ds.ha.user.password=${GUARDIAN_DS_HA_USER_PASSWORD:-"Transwarp!"}
EOF
    fi
    cat >> $SCRIPT_DIR/guardian-ds.properties <<EOF
guardian.ds.ha.master.host=${GUARDIAN_DS_HA_MASTER_HOST}
guardian.ds.ha.master.port=${GUARDIAN_DS_HA_MASTER_PORT}
guardian.ds.ha.master.tls.enabled=${GUARDIAN_DS_HA_MASTER_TLS_ENABLED:-"false"}
guardian.ds.ha.refresh.interval=60000
guardian.ds.ha.config.dn=ads-replConsumerId=replication,ou=system
guardian.ds.ha.healthy.check.interval=300000
guardian.ds.ha.healthy.check.timeout=5000
EOF
  fi
fi

cp -f $SCRIPT_DIR/guardian-ds.properties $GUARDIAN_CONF_DIR/guardian-ds.properties

fi

