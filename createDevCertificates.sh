#!/bin/bash

TMP_DIR=${TMP_DIR:=target}
CA_DIR=${CA_DIR:=$TMP_DIR/ca}
TLS_DIR=${TLS_DIR:=$TMP_DIR/tls13}

echo "Generating TLS certificates for STARTTLS... $TLS_DIR"

mkdir -p $TLS_DIR || exit 1
mkdir -p $CA_DIR || exit 1

CA_CRT=$CA_DIR/ca.crt
CA_KEY=$CA_DIR/ca.key
CLIENT_CSR=$TLS_DIR/client.csr
CLIENT_CRT=$TLS_DIR/client.crt
CLIENT_KEY=$TLS_DIR/client.key
CLIENT_P12=${CLIENT_P12:=$TLS_DIR/client.p12}

CLIENT_PASSWORD=clientpass
KEYSTORE_PASSWORD=changeit
TRUST_STORE_PASSWORD=truststore

VALID_DAYS=3660
BITS_LENGTH=2048

SERVER_CRT=$TLS_DIR/server.crt
SERVER_JKS=$TLS_DIR/server.jks
SERVER_KEY=$TLS_DIR/server.key
SERVER_P12=${SERVER_P12:=$TLS_DIR/server.p12}
SERVER_CSR=$TLS_DIR/server.csr
SAN_CNF=$TLS_DIR/san.cnf

TRUST_STORE_JKS=${TRUST_STORE_JKS:=$TLS_DIR/trustedCerts.jks}

CITY=Warszawa
COUNTRY=PL
PROVINCE=mazowieckie

# Step 1: Generate CA (self-signed)
echo "Creating CA..."
openssl genrsa -out $CA_KEY $BITS_LENGTH || exit 1
openssl req -new -x509 -days $VALID_DAYS -key $CA_KEY -out $CA_CRT -subj "/C=$COUNTRY/ST=$PROVINCE/L=$CITY/O=Test CA/CN=Test Root CA" || exit 1

# Step 2: Generate server key and CSR
echo "Creating server certificate..."
openssl genrsa -out $SERVER_KEY $BITS_LENGTH || exit 1

# Create config file for SAN
cat > $SAN_CNF <<EOF
[req]
default_bits = $BITS_LENGTH
prompt = no
default_md = sha256
distinguished_name = dn
req_extensions = v3_req

[dn]
C = $COUNTRY
ST = $PROVINCE
L = $CITY
O = Test
OU = IT Department
CN = localhost

[v3_req]
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
DNS.2 = pekko-smtp-server.github.com
IP.1 = 127.0.0.1
EOF

openssl req -new -key $SERVER_KEY -out $SERVER_CSR -config $SAN_CNF || exit 1

# Step 3: Sign server certificate with CA
openssl x509 -req -in $SERVER_CSR -CA $CA_CRT -CAkey $CA_KEY -CAcreateserial -out $SERVER_CRT -days $VALID_DAYS -sha256 -extensions v3_req -extfile $SAN_CNF || exit 1

# Step 4: Create PKCS12 for Java
echo "Creating PKCS12 keystore..."
openssl pkcs12 -export -in $SERVER_CRT -inkey $SERVER_KEY -out $SERVER_P12 -name "starttls-server" -passout pass:$KEYSTORE_PASSWORD || exit 1

# Step 5: Create JKS
echo "Creating JKS keystore..."
keytool -importkeystore -srckeystore $SERVER_P12 -srcstoretype pkcs12 -destkeystore $SERVER_JKS -deststoretype jks -srcstorepass $KEYSTORE_PASSWORD -deststorepass $TRUST_STORE_PASSWORD || exit 1

# Step 6: Create truststore (contains CA)
echo "Creating truststore..."
keytool -import -alias root-ca -keystore $TRUST_STORE_JKS -file $CA_CRT -trustcacerts -storepass $TRUST_STORE_PASSWORD -noprompt || exit 1

# Step 7: Generate client certificate
echo "Creating client certificate..."
openssl genrsa -out $CLIENT_KEY $BITS_LENGTH || exit 1
openssl req -new -key $CLIENT_KEY -out $CLIENT_CSR -subj "/C=$COUNTRY/ST=$PROVINCE/L=$CITY/O=Test/CN=ajozwik" || exit 1
openssl x509 -req -in $CLIENT_CSR -CA $CA_CRT -CAkey $CA_KEY -CAcreateserial -out $CLIENT_CRT -days $VALID_DAYS || exit 1
openssl pkcs12 -export -in $CLIENT_CRT -inkey $CLIENT_KEY -out $CLIENT_P12 -name "client1" -passout pass:$CLIENT_PASSWORD || exit 1

echo "Done! Generated files:"
echo "  $CA_CRT, $CA_KEY          - Certificate Authority"
echo "  $SERVER_CRT, $SERVER_KEY  - Server certificate"
echo "  $SERVER_JKS, $SERVER_P12  - Java keystores (password: $KEYSTORE_PASSWORD)"
echo "  $TRUST_STORE_JKS          - Truststore (password: $TRUST_STORE_PASSWORD)"
echo "  $CLIENT_CRT, $CLIENT_KEY  - Client certificate"
echo "  $CLIENT_P12              - Client keystore (password: $CLIENT_PASSWORD)"

# Clean up
rm -f $SERVER_CSR $CLIENT_CSR  $SAN_CNF


cd $TLS_DIR && chmod 600 *.key *.p12 *.jks

echo "Certificates generated successfully!"