#!/bin/bash

[ -d "/etc/federation/generated-conf" ] || {
  echo "federation generated conf dir not exists, exit."
  exit 1
}

cd /etc/federation/generated-conf

[ -f "application.properties" ] || {
  echo "application.properties not exists, exit."
  exit 1
}

echo "line break"
echo -e "\n" >> application.properties

echo "generate secret properties"
[ X"${FEDERATION_SERVICE_DATASOURCE_USERNAME}" == X"" ] || {
  echo "find FEDERATION_SERVICE_DATASOURCE_USERNAME properties, generate it."
  sed -i '/spring.datasource.username/d' application.properties
  echo "spring.datasource.username=${FEDERATION_SERVICE_DATASOURCE_USERNAME}" >> application.properties
}

[ X"${FEDERATION_SERVICE_DATASOURCE_PASSWORD}" == X"" ] || {
  echo "find FEDERATION_SERVICE_DATASOURCE_PASSWORD properties, generate it."
  sed -i '/spring.datasource.password/d' application.properties
  echo "spring.datasource.password=${FEDERATION_SERVICE_DATASOURCE_PASSWORD}" >> application.properties
}

[ X"${FEDERATION_SERVICE_SSL_KEY_STORE_PASSWORD}" == X"" ] || {
  echo "find FEDERATION_SERVICE_SSL_KEY_STORE_PASSWORD properties, generate it."
  sed -i '/server.ssl.key-store-password/d' application.properties
  echo "server.ssl.key-store-password=${FEDERATION_SERVICE_SSL_KEY_STORE_PASSWORD}" >> application.properties
}

[ X"${FEDERATION_SERVICE_RESET_PASSWORD_ALLOWED}" == X"" ] || {
  echo "find FEDERATION_SERVICE_RESET_PASSWORD_ALLOWED properties, generate it."
  sed -i '/guardian.federation.service.resetPasswordAllowed/d' application.properties
  echo "guardian.federation.service.resetPasswordAllowed=${FEDERATION_SERVICE_RESET_PASSWORD_ALLOWED}" >> application.properties
}

[ X"${FEDERATION_SERVICE_USER_ADMIN_PASSWORD}" == X"" ] || {
  echo "find FEDERATION_SERVICE_USER_ADMIN_PASSWORD properties, generate it."
  sed -i '/guardian.federation.service.user.admin.password/d' application.properties
  echo "guardian.federation.service.user.admin.password=${FEDERATION_SERVICE_USER_ADMIN_PASSWORD}" >> application.properties
}

[ X"${FEDERATION_SERVICE_USER_ADMIN_USE_PLAIN_PASSWORD}" == X"" ] || {
  echo "find FEDERATION_SERVICE_USER_ADMIN_USE_PLAIN_PASSWORD properties, generate it."
  sed -i '/guardian.federation.service.user.admin.usePlainPassword/d' application.properties
  echo "guardian.federation.service.user.admin.usePlainPassword=${FEDERATION_SERVICE_USER_ADMIN_USE_PLAIN_PASSWORD}" >> application.properties
}
