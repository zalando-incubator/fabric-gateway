# Fabric Gateway

Fabric API Gateway is an API Gateway built on [Skipper](https://github.com/zalando/skipper). Skipper is a HTTP router which has many features which are applied on a route-by-route basis, where each route is configured by a single Ingress. Fabric API Gateway generates these ingresses to support authentication, rate-limiting and more from a single [OpenAPI](https://swagger.io/specification/)-style [Custom Resource Definition](https://kubernetes.io/docs/tasks/access-kubernetes-api/custom-resources/custom-resource-definitions/).

* [Getting started](fabric-gateway-getting-started.md)
* [Features](fabric-gateway-features.md)
