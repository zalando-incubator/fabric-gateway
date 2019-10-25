# Getting Started

The Gateway depends on [metacontroller](https://metacontroller.app) and [Skipper Ingress](https://opensource.zalando.com/skipper/kubernetes/ingress-controller).  
Once you have installed both of those, the gateway can be installed by applying some yaml.

## Install Metacontroller

You can find instructions at [metacontroller](https://metacontroller.app/guide/install/)

## Install Skipper Ingress

You can find instructions at [Skipper Ingress](https://opensource.zalando.com/skipper/kubernetes/ingress-controller/#install-skipper-as-ingress-controller)

## Install API Gateway Operator

```sh
kubectl apply -f https://raw.githubusercontent.com/zalando-incubator/fabric-gateway/master/deploy/apply/01_FabricGatewayCRD.yaml
kubectl apply -f https://raw.githubusercontent.com/zalando-incubator/fabric-gateway/master/deploy/apply/02_GatewayCRDSchemaValidation.yaml
kubectl apply -f https://raw.githubusercontent.com/zalando-incubator/fabric-gateway/master/deploy/apply/03_MetacontrollerGatewaySynchHook.yaml
kubectl apply -f https://raw.githubusercontent.com/zalando-incubator/fabric-gateway/master/deploy/apply/07_OperatorDeployment.yaml
kubectl apply -f https://raw.githubusercontent.com/zalando-incubator/fabric-gateway/master/deploy/apply/08_OperatorService.yaml
```

## Worked Example

### Apply a Sample App

Apply the following yaml to get a sample echo app up and running:

```yaml
kind: Service
apiVersion: v1
metadata:
  name: my-first-gateway
spec:
  selector:
    app: my-first-gateway
  ports:
  - name: http
    port: 80
    targetPort: 5678
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-first-gateway
  labels:
    app: my-first-gateway
spec:
  replicas: 1
  selector:
    matchLabels:
      app: my-first-gateway
  template:
    metadata:
      labels:
        app: my-first-gateway
    spec:
      containers:
      - name: my-first-gateway
        image: hashicorp/http-echo:0.2.3
        args:
        - -text="some string"
        ports:
        - containerPort: 5678
```

### Apply a Sample Gateway

Once everything is set up, you can apply the gateway as follows:

```yaml
apiVersion: zalando.org/v1
kind: FabricGateway
metadata:
  name: my-first-gateway
spec:
  x-fabric-service:
    - host: my-first-gateway.<mydomain.org>
      serviceName: my-first-gateway
      servicePort: http
  paths:
    /test:
      get:
        x-fabric-privileges:
          - uid
```

Now run this to get an authentication failure:

```sh
curl -i "https://${USER}-gateway-test.playground.zalan.do/somepath"
```

If your token has the right scopes, the following will give a successful response:

```sh
curl -i -H "Authorization: Bearer ${TOKEN}" "https://${USER}-gateway-test.playground.zalan.do/somepath"
```
