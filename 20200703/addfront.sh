 #!/usr/bin/env bash

curl -L -H "PRIVATE-TOKEN: ${FRONTEND_PRIVATE_TOKEN:-"Met9rNE5pcBBsvVmJ7vd"}" \
http://172.16.1.41:10080/api/v4/projects/4531/jobs/artifacts/master/download?job=postcommit -o frontend.zip
unzip frontend.zip

DEV_ROOT=`pwd`

RESOURCE_DIR=$DEV_ROOT/federation-service/src/main/resources
mkdir -p $RESOURCE_DIR/static
mv $RESOURCE_DIR/templates/index.html $RESOURCE_DIR/templates/index_bak.html
mv dist/guardian-federation-frontend/index.html $RESOURCE_DIR/templates/index.html
mv dist/guardian-federation-frontend/* $RESOURCE_DIR/static/
sed -i "s@<html lang=\"en\">@<html xmlns:th=\"http://www.thymeleaf.org\">@g" $RESOURCE_DIR/templates/index.html
sed -i "s@<base href=\"/\">@<base th:href=\"\${basePath}\">@g" $RESOURCE_DIR/templates/index.html