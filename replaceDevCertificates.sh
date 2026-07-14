#!/bin/bash

ROOT=runtime/src/main/resources

export TMP_DIR=target
export CA_DIR=$TMP_DIR/ca
export TLS_DIR=$TMP_DIR/tls13

export CLIENT_P12=$TLS_DIR/client.p12
export SERVER_P12=$TLS_DIR/server.p12
export TRUST_STORE_JKS=$TLS_DIR/trustedCerts.jks

rm -Rf $TMP_DIR
sh createDevCertificates.sh || exit 1

cp $CLIENT_P12 $ROOT/tls13/
cp $SERVER_P12 $ROOT/tls13/
cp $TRUST_STORE_JKS $ROOT/tls13/