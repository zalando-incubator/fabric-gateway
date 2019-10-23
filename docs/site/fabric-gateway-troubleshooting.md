# Gateway Troubleshooting
## Investigation Workflow
Below is a detailed workflow for investigating issues when trying to access a service via the gateway. If you have
checked everything mentioned below and you still believe that you should be able to access the backend with your 
request then please contact [Team fabric](../contact). For a more detailed ingress related troubleshooting guide,
please see [here](https://cloud.docs.zalando.net/tutorials/understanding-ingress/)

<object data="../img/GatewayTroubleshooting.svg" type="image/svg+xml">
    ![SVG Image Test](img/GatewayTroubleshooting.svg)
</object>

### Check Scalyr Logs
If there is an issue with Skipper which has resulted in your request not being completed successfully, it will be logged
in [scalyr](https://eu.scalyr.com/events?filter=$application%20%3D%3D%20%22skipper-ingress%22&startTime=20%20min) as an error.
Examples of some common errors that you may see in the Skipper logs are outlined below:

  * Skipper Unable to connect to a service
    - `Failed to do backend roundtrip to http://10.3.191.60:80: dialing failed true: dial tcp 10.3.191.60:80: connect: connection refused`
    - `error while proxying, route kube_default__zissou_master_spp_product_search_perf_test_ui__spp_product_search_perf_test_ui_smart_product_platform_test_zalan_do____zissou_master_spp_product_search_perf_test_ui with backend http://10.2.152.18:8089, status code 502: dialing failed true: dial tcp 10.2.152.18:8089: i/o timeout`
    - `Failed to get an entry on to the stack to process Timeout: timeout for host spp-logistics-data-test.zalandoapis.com`
    - `net.Error during backend roundtrip to http://10.2.130.6:8080: timeout=false temporary=false: read tcp 10.2.0.0:54908->10.2.130.6:8080: read: connection reset by peer`
  * Skipper Failed to parse an ingress defined route
    - [`convertPathRule: Failed to get service default, nutella, 8080`](./#ingress-routes-not-generated)
    - `failed to process route (kubeew_fabric__fgtestapp_operator__fgtestapp_operator_smart_product_platform_test_zalan_do____fgtestapp_operator): predicate not found: 'BloopSourceFromLast'`
        * For issues like this, you should contact [Team fabric](../contact).

### Backend Service Availability
If you are seeing 502 responses for your requests, it is very likely that your service is either:
    
  1. unavailable to respond
  1. is responding too slowly to requests

Investigate Service response time metrics in Grafana and look for spikes. Scale your Service if it cannot handle the load.
You can check if your service pods were restarting during the logged period of `502`'s by checking their uptime with `zkubectl`.    
If there is a large number of restarts and a small age for the pods, then you may have issues with the application.
    ```
    zkubectl get pods
    
    NAME                                  READY     STATUS    RESTARTS   AGE
    fgtestapp-bdbd8945f-bpfk8             1/1       Running   0          8h
    fgtestapp-operator-688b698bdd-wkkmw   1/1       Running   0          4h35m
    gateway-operator-588f6bff96-9n5tj     1/1       Running   0          3d19h
    metacontroller-0                      1/1       Running   0          10d
    slo-operator-56f7c98668-867wl         1/1       Running   0          8h
    wl-fgtestapp-7b659b99f9-w7l6h         1/1       Running   0          8h
    ```

### Describe Gateway
To view all the created gateway resources in a cluster: 
   ```
   zkubectl get FabricGateway
   
   NAME              INGRESS_COUNT
   fabric-demo-app   54
   ```
An `ingress_count` greater than 2 (i.e. more than the two default routes `http_reject` and `default_reject` which will be provided for every gateway) 
indicates that some routes have been created as ingresses. 
List the names of the created ingresses:
    ```
    zkubectl describe FabricGateway fabric-demo-app
    
    Name:         fabric-demo-app
    Namespace:    default
    Labels:       <none>
    Annotations:  kubectl.kubernetes.io/last-applied-configuration={"apiVersion":"zalando.org/v1","kind":"FabricGateway","metadata":{"annotations":{},"name":"fabric-demo-app","namespace":"default"},"spec":{"paths":...
    API Version:  zalando.org/v1
    Kind:         FabricGateway
    Metadata:
      Creation Timestamp:  2019-05-28T10:46:53Z
      Generation:          147
      Resource Version:    237669251
      Self Link:           /apis/zalando.org/v1/namespaces/default/fabricgateways/fabric-demo-app
      UID:                 e7b246ef-8135-11e9-8688-06a4dad07bd4
    Spec:
      Paths:
        / API / Contributors:
          Get:
            X - Fabric - Privileges:
              fabric-demo-app.read
            X - Fabric - Ratelimits:
              Default - Rate:  10000          
        / API / Contributors /{ Id }:
          Get:
            X - Fabric - Privileges:
              fabric-demo-app.read
            X - Fabric - Ratelimits:
              Default - Rate:  10000          
      X - Fabric - Admins:       # *1 Defining the administrators of the service. These users will have special unrestricted access to the service
        jbloggs        
      X - Fabric - Service:
        Host:          fabric-demo-app.smart-product-platform-test.zalan.do
        Service Name:  fabric-demo-app
        Service Port:  http
    Status:
      Num _ Owned _ Ingress:  54
      Observed Generation:    146
      Owned _ Ingress _ Names:                
        fabric-demo-app-reject-http-route
        fabric-demo-app-default-403-route                
        fabric-demo-app-get-api-contributors-id-rl-all        
        fabric-demo-app-get-api-contributors-id-admins        
        fabric-demo-app-get-api-contributors-admins                
        fabric-demo-app-get-api-contributors-rl-all        
    Events:  <none>
    ```
We can see the below from the output of the describe operation. 

  * There are two routes defined in [OAS](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md#pathsObject) format. 
  * There is one admin allowed to access these routes without scope verification.
    - **N.B.** If there are scopes defined on a route, the only employee tokens that are able to access the route will be those    
       whose id is defined in the admin list at *1
  * You will also be able to see if [whitelisting](../fabric-gateway/#whitelisting) has been enabled either globally or for a specific route
     by issing the describe command.

It is important to note here, that the `owned_ingress_names` are the names of the corresponding `ingress` resources which
have been created by this gateway. You can inspect these `ingresses` individually to get more detail around the skipper
[predicate](https://opensource.zalando.com/skipper/reference/predicates/) and [filter](https://opensource.zalando.com/skipper/reference/filters/) 
chains that have been defined for the route. The naming convention for these `ingresses` is:    
`<gateway name>-<http verb>-<path>-<feature: (admin|service) route>`

### Describe Ingress
List all ingress resources in a cluster:
    ```
    zkubectl get ingress
    
    NAME                                                                   HOSTS                                                                                               ADDRESS                                                            PORTS     AGE
    fabric-demo-app-reject-http-route                                     fgtestapp.smart-product-platform-test.zalan.do,alt-fgtestapp.smart-product-platform-test.zalan.do   aws-2618-lb-slxfv2yl5rgn-812157914.eu-central-1.elb.amazonaws.com   80        12d
    fabric-demo-app-default-403-route                                     fgtestapp.smart-product-platform-test.zalan.do,alt-fgtestapp.smart-product-platform-test.zalan.do   aws-2618-lb-slxfv2yl5rgn-812157914.eu-central-1.elb.amazonaws.com   80        4d23h
    fabric-demo-app-get-api-contributors-id-rl-all                        fgtestapp.smart-product-platform-test.zalan.do,alt-fgtestapp.smart-product-platform-test.zalan.do   aws-2618-lb-slxfv2yl5rgn-812157914.eu-central-1.elb.amazonaws.com   80        4d23h
    fabric-demo-app-get-api-contributors-id-admins                        fgtestapp.smart-product-platform-test.zalan.do,alt-fgtestapp.smart-product-platform-test.zalan.do   aws-2618-lb-slxfv2yl5rgn-812157914.eu-central-1.elb.amazonaws.com   80        4d23h
    fabric-demo-app-get-api-contributors-admins                           fgtestapp.smart-product-platform-test.zalan.do,alt-fgtestapp.smart-product-platform-test.zalan.do   aws-2618-lb-slxfv2yl5rgn-812157914.eu-central-1.elb.amazonaws.com   80        4d23h
    fabric-demo-app-get-api-contributors-rl-all                           fgtestapp.smart-product-platform-test.zalan.do,alt-fgtestapp.smart-product-platform-test.zalan.do   aws-2618-lb-slxfv2yl5rgn-812157914.eu-central-1.elb.amazonaws.com   80        4d23h
    ...
    ```

Describe a single ingress:
    ```
    zkubectl get ingress fabric-demo-app-get-api-contributors-id-rl-all -o yaml
    
    apiVersion: extensions/v1beta1
    kind: Ingress
    metadata:
      annotations:        
        zalando.org/skipper-filter: enableAccessLog(4, 5) -> oauthTokeninfoAllScope("uid",
          "fabric-demo-app.write") -> flowId("reuse") -> forwardToken("X-TokenInfo-Forward")
        zalando.org/skipper-predicate: Header("X-Forwarded-Proto", "https") && Path("/resources/:id/sub-resources/:id")
          && Method("PUT")
      creationTimestamp: 2019-05-30T14:55:45Z
      generation: 1
      labels:
        controller-uid: f0b2e65d-7d3f-11e9-912e-0a13213f0924
      name: fabric-demo-app-get-api-contributors-id-rl-all
      namespace: default
      ownerReferences:
      - apiVersion: zalando.org/v1
        blockOwnerDeletion: true
        controller: true
        kind: FabricGateway
        name: fabric-demo-app
        uid: f0b2e65d-7d3f-11e9-912e-0a13213f0924
      resourceVersion: "233186583"
      selfLink: /apis/extensions/v1beta1/namespaces/fabric/ingresses/fgtestapp-put-resources-id-sub-resources-id-all
      uid: 00f5daed-82eb-11e9-a459-0222f74ed888
    spec:
      rules:
      - host: fgtestapp.smart-product-platform-test.zalan.do
        http:
          paths:
          - backend:
              serviceName: fgtestapp
              servicePort: http
      - host: alt-fgtestapp.smart-product-platform-test.zalan.do
        http:
          paths:
          - backend:
              serviceName: fgtestapp
              servicePort: http
    status:
      loadBalancer:
        ingress:
        - hostname: aws-2618-lb-slxfv2yl5rgn-812157914.eu-central-1.elb.amazonaws.com
    ```
From the above example we can see that an ingress resource was created which should map to a route in Skipper with the
following predicate/filter chain:    
```
Header("X-Forwarded-Proto", "https") && Path("/resources/:id/sub-resources/:id") && Method("PUT")enableAccessLog(4, 5) -> oauthTokeninfoAllScope("uid", "fabric-demo-app.write") -> flowId("reuse") -> forwardToken("X-TokenInfo-Forward")
```

You can consult the Skipper documentation for further information on the functionality of the individual 
[predicates](https://opensource.zalando.com/skipper/reference/predicates/) and [filters](https://opensource.zalando.com/skipper/reference/filters/)
but basically, the predicates match a request and then the filters perform some operation on the matched request.

### Ingress Routes Not Generated
Check that you have a named port in your service definition, see [Service Ports](#service-ports)

### Receiving 401s
There is only one instance where the gateway will return a `401` HTTP response code. That's if the OAuth Bearer token 
that you provided with the request failed the [Skipper token authentication](https://cloud.docs.zalando.net/howtos/ingress/#tokeninfo-filters-in-ingress)
request. If this is the case then you will need to generate a new token using the [`ztoken`](https://cloud.docs.zalando.net/howtos/authenticate-requests/#getting-a-token-for-testing-or-development)
tool, or if this is a service token, check your application to ensure that the token cycling is working correctly.

### Receiving 403s
- Make sure the scopes in the token you are using match the scopes you have set in the gateway. 
You can use [https://jwt.io](https://jwt.io) to verify this. For example if your gateway endpoint requires scope 
`myapp.read`, then the `https://identity.zalando.com/privileges` key should have a list containing 
`com.zalando::myapp.read`. 
- Only `Service` tokens can contain scopes. If you are trying connect with an `Employee` token and the path
is scope protected, then you must be a member of the admin list to bypass the scope validation.
- Check your service uid is whitelisted. You can check your token's service uid by using 
[https://jwt.io](https://jwt.io) and looking at the `sub` field. Your token uid is prefixed with "stups_" for 
a service uid.

### Receiving 404s
- Make sure the path you are hitting exists in your gateway definition.
- 404 is the default response from Skipper when a route doesn't exist, so make sure that the gateway resource exists
and has associated ingress resources as per [here](./#describe-gateway).
- It's possible that a gateway resource exists but the routes weren't created in Skipper. The most common cause of this
is if the K8s service is not being properly targeted. Check that the `servicePort` defined in your gateway resource file
matches the name defined in the [K8s service](https://kubernetes.io/docs/concepts/services-networking/service/#multi-port-services).
If the port is not named in the gateway resource, then the assumed name of the port in the service is `http`

### Receiving 400s
- Using the gateway ensure that requests coming to your app have used https. As TLS termination happens at the Amazon Load 
Balancer, we achieve this by checking for the existance of the header `X-Forwarded-Proto` with a https value. If you are 
receiving a 400 response indicating that only HTTPS is supported, this is likely the cause.
- Any other 400 response is likely coming direct from the target service and should be investigated with the service
owner.