# Operating the Gateway
If you are manually managing gateway resources through `zkubectl` then you will be able to see which
ingress resources are owned by a gateway resource in the status field.
```bash
(stups) ➜  ~ zkubectl describe FabricGateway fabric-demo-app
Name:         fabric-demo-app
Namespace:    default
Kind:         FabricGateway
...
Spec:
  Paths:
    / API / Pull - Requests /*:
      Get:
        X - Fabric - Privileges:
          fabric-demo-app.read
      Head:
        X - Fabric - Privileges:
          fabric-demo-app.read
      Put:
        X - Fabric - Privileges:
          fabric-demo-app.write
    ...
  X - Fabric - Admins:
    jbloggs
  X - Fabric - Service:
    Host:          fabric-demo-app.smart-product-platform-test.zalan.do
    Service Name:  fabric-demo-app
Status:
  Num _ Owned _ Ingress:  106
  Observed Generation:    1
  Owned _ Ingress _ Names:
    fabric-demo-app-get-api-pull-requests-id-admin-jbloggs
    fabric-demo-app-head-api-repositories-id-all
    ...
Events:  <none>
```
From the above information, the Skipper details for the route can be grabbed by inspecting the specific ingress resource
```bash
(stups) ➜  ~ zkubectl describe ingress fabric-demo-app-head-api-repositories-id-all
Name:             fabric-demo-app-head-api-repositories-id-all
Namespace:        default
Address:          aws-2618-lb-slxfv2yl5rgn-812157914.eu-central-1.elb.amazonaws.com
Default backend:  default-http-backend:80 (<none>)
Rules:
  Host                                                  Path  Backends
  ----                                                  ----  --------
  fabric-demo-app.smart-product-platform-test.zalan.do
                                                           fabric-demo-app:http (<none>)
Annotations:
  zalando.org/skipper-predicate:                     Path("/api/repositories/*") && Method("HEAD")
  zalando.org/skipper-filter:                        oauthTokeninfoAllScope("uid", "fabric-demo-app.read") -> flowId("reuse") -> forwardToken("X-TokenInfo-Forward")`)
  ...
Events:                                              <none>
```

It is also possible to view the current `FabricGateway` resources for your service by using the [Kubernetes Web View](https://kube-web-view.zalando.net/clusters/smart-product-platform/namespaces/_all/fabricgateways?)

## Dashboards
There is a [Grafana dashboard](https://zmon.zalando.net/grafana/dashboard/db/fabric-gateway-operator) available to get 
an overview of metacontroller and the operator (further described down in the [Implementation Details](./#implementation-details) 
section). This gives important information about the number of gateway managed ingresses across the different clusters 
where the gateway is deployed.

## Implementation Details

This CRD looks after creating Skipper routes to provide the Gateway functionality. The Gateway uses an operator service
called [metacontroller](https://metacontroller.app/) to handle interactions with Kubernetes API, and a Fabric service
called [Gateway Operator](https://github.bus.zalan.do/fabric/gateway-operator) to convert the Gateway CRD definition
into the desired Ingress resources.

![Gateway Operator](img/FabricGateway.png)  
_**Above: Fabric Gateway Operator**_