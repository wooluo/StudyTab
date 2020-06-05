#!/bin/bash


FEDERATION_SERVICE_DATASOURCE_USERNAME=true

echo "line break"
echo -e "\n" >> application.properties

echo "generate secret properties"
[ X"${FEDERATION_SERVICE_DATASOURCE_USERNAME}" == X"" ] || {
  echo "find FEDERATION_SERVICE_DATASOURCE_USERNAME properties, generate it."
  sed -i '/spring.datasource.username/d' application.properties
  echo "spring.datasource.username=${FEDERATION_SERVICE_DATASOURCE_USERNAME}" >> application.properties
}

