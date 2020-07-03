#!/usr/bin/env bash

DEV_ROOT=`pwd`

RESOURCE_DIR=$DEV_ROOT/federation-service/src/main/resources
rm -rf $RESOURCE_DIR/static
rm -rf $RESOURCE_DIR/templates/index.html
mv $RESOURCE_DIR/templates/index_bak.html $RESOURCE_DIR/templates/index.html
rm -rf frontend.zip
rm -rf dist/