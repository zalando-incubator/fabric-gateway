# Gateway Perf Tests
To get a clear indication of what latency the different gateway features add to a request, there are a 
suite of tests which can be executed which will test the various gateway profiles. The tests are executed
using [Gatling](https://gatling.io/) and run on [CDP](https://dev.zalando.net/pipelines?deploymentUnit=gateway-perf-tests&teams=fabric).
The results are stored in S3 and are accessible via the links outlined below. If you want to change the test
run configuration, please see this [repo](https://github.bus.zalan.do/fabric/gateway-perf-tests)

## Scenarios
The current test scenarios are outlined below with links to their current results. Historic results can be viewed
by replacing the `latest` alias in the url with a CDP pipeline name. For example, the current url for
one of the scenarios is:    
`http://fabric-gateway-perf-results.s3-website.eu-central-1.amazonaws.com/latest/aws-classic-lb-perf-test/index.html`    
And a direct CDP linked url is:    
`http://fabric-gateway-perf-results.s3-website.eu-central-1.amazonaws.com/pr-1-67/aws-classic-lb-perf-test/index.html`    

### AWS Classic LB
This scenario avoids using [Skipper](https://opensource.zalando.com/skipper/) as an ingress in favour of using
an AWS classic LB to route the traffic across the apps. It's important to note here that there is no
gateway functionality provided here except for load balancing across the backends. This would mean that it was up 
to the application itself to provide any required gateway functionality, e.g. Auth/Auth, Rate Limiting, 
Whitelisting, etc...

[Latest Results](http://fabric-gateway-perf-results.s3-website.eu-central-1.amazonaws.com/latest/gateway-perf-aws-classic/index.html)

### Standalone Skipper
This scenario is using [Skipper](https://opensource.zalando.com/skipper/) as an ingress. It's important to note 
here that there is no gateway functionality provided here except for load balancing across the backends. 
This would mean that it was up to the application itself to provide any required gateway functionality, 
e.g. Auth/Auth, Rate Limiting, Whitelisting, etc...

[Latest Results](http://fabric-gateway-perf-results.s3-website.eu-central-1.amazonaws.com/latest/gateway-perf-skipper-standalone/index.html)

### Fabric Gateway
#### Auth Only
This scenario is using the [Gateway](https://fabric.docs.zalando.net/fabric-gateway/) to generate the
desired Skipper Ingress routes. This is a minimal gateway example and the only functionality that is applied
to the routes is validation of the token and scope checking.

[Latest Results](http://fabric-gateway-perf-results.s3-website.eu-central-1.amazonaws.com/latest/gw-perf-minimal/index.html)

#### Auth and Whitelisting
This scenario is again using the Gateway to generate the desired Skipper Ingress routes. This gateway example 
is validating that the service is both authenticated and whitelisted before passing the request through to the
backend service.

[Latest Results](http://fabric-gateway-perf-results.s3-website.eu-central-1.amazonaws.com/latest/gw-perf-whitelist/index.html)

#### Auth and Rate Limiting
This scenario is again using the Gateway to generate the desired Skipper Ingress routes. This gateway example 
is validating that the service is both authenticated and within its allowed rate limit before passing the 
request through to the backend service.

[Latest Results](http://fabric-gateway-perf-results.s3-website.eu-central-1.amazonaws.com/latest/gw-perf-ratelimit/index.html)
