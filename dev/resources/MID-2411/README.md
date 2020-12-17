# Testing Related to ApiVersion bump
* Setup Playground as per master:
  * Deleted all the TestFabricGateways: `zk delete tfg --all`
  * Delete the Composite Controller `zk delete cc test-gateway-operator`
  * Deploy this [pipeline](https://dev.zalando.net/pipelines/l-zc6nwcft2ss1gog765tke7p3w/0/general)
  * Delete all the tfg again, just to keep things tidy `zk delete tfg --all`
* Create our test tfg: `zk apply -f tfg.yaml`
* Run the monitoring:
  * In one tab, execute the query script to ensure that we never lose connectivity: `./query.sh`
  * Monitor the ingress resources: `zk get ing -w`
* Deploy the new operator and Composite Controller from the [pipeline](https://dev.zalando.net/pipelines/l-wo4cp49z2a2kdr2vuoufef7a5/0/general)
* What you should see is the new resources `m-*` created, and the old resources remain
* Edit the Composite Controller to add back in the managed child:
  ```
  - apiVersion: networking.k8s.io/v1beta1
    resource: ingresses
    updateStrategy:
      method: InPlace
  ```
* Dance Happily

 
