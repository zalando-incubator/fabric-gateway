# TLS in Gateway Operator
There is an endpoint exposed by the operator called `validate` which is used as a pre-persist 
hook for `FabricGateway` resources in Kubernetes using the [admission controller pattern](https://v1-13.docs.kubernetes.io/docs/reference/access-authn-authz/extensible-admission-controllers/#configure-admission-webhooks-on-the-fly).
There is a strict requirement in Kubernetes that this communication happens over a TLS channel,
therefore the operator will bind to port `8080` for plaintext and port `8443` for TLS.


### Keystore generation notes
Keystore generation from Java Keytool as per this [Lightbend tutorial])(https://lightbend.github.io/ssl-config/CertificateGeneration.html)
The password used below should be the same as the password which is provided to the server to access the JKS
```
keytool -genkeypair -v \
  -alias FabricCA \
  -dname "CN=Fabric, OU=Infrastructure, O=Zalando, L=Dublin, ST=Dublin, C=IE" \
  -keystore fabricCA.jks \
  -keypass:env PW \
  -storepass:env PW \
  -keyalg RSA \
  -keysize 4096 \
  -ext KeyUsage:critical="keyCertSign" \
  -ext BasicConstraints:critical="ca:true" \
  -validity 9999 \
  -ext "SAN=dns:test-gateway-operator.fabric.svc.cluster.local"

keytool -export -v \
  -alias FabricCA \
  -file fabricCA.crt \
  -keypass:env PW \
  -storepass:env PW \
  -keystore fabricCA.jks \
  -rfc

keytool -genkeypair -v \
  -alias gateway-operator \
  -dname "CN=test-gateway-operator.fabric.svc, OU=Fabric, O=Zalando, L=Dublin, ST=Dublin, C=IE" \
  -keystore gateway-operator.jks \
  -keypass:env PW \
  -storepass:env PW \
  -keyalg RSA \
  -keysize 2048 \
  -validity 9999 \
  -ext "SAN=dns:test-gateway-operator.fabric.svc"

keytool -certreq -v \
  -alias gateway-operator \
  -keypass:env PW \
  -storepass:env PW \
  -keystore gateway-operator.jks \
  -file gateway-operator.csr

keytool -gencert -v \
  -alias FabricCA \
  -keypass:env PW \
  -storepass:env PW \
  -keystore fabricCA.jks \
  -infile gateway-operator.csr \
  -outfile gateway-operator.crt \
  -ext KeyUsage:critical="digitalSignature,keyEncipherment" \
  -ext EKU="serverAuth" \
  -validity 9999 \
  -rfc

keytool -importcert -alias FabricCA -file fabricCA.crt -keystore gateway-operator.jks -storetype JKS -storepass:env PW

keytool -importcert -alias gateway-operator -file gateway-operator.crt -keystore gateway-operator.jks -storetype JKS -storepass:env PW

keytool -list -v -keystore gateway-operator.jks -storepass:env PW

keytool -importkeystore -srckeystore gateway-operator.jks -destkeystore gateway-operator.jks -deststoretype pkcs12

cp gateway-operator.jks ../src/main/resources/ssl/gateway-operator.jks
```

### ValidatingWebhookConfiguration caBundle
Base64 encode the contents of the `gateway-operator.crt` file and use that for the caBundle in the `02_GatewayCRDSchemaValidation.yaml`
K8s resource using the below: 
```
sed -i -- "s/caBundle: .*/caBundle: $(base64 -in gateway-operator.crt)/g" ../deploy/operator/apply/02_GatewayCRDSchemaValidation.yaml
```
