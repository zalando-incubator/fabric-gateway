# Manual Test Plan for Gateway Operator
In accordance with the principles of continuous delivery, we strive to cover off the majority of tests for the Gateway product with an automated test suite. These tests are executed on CDP for each push to a branch which has an open PR for it. These tests cover three different areas, unit testing, integration testing and system tests. Due to how we integrate with metacontroller in the architecture of the Gateway operator, these system tests are quite important. Unfortunately due to the nature of how we use CDP to provision resources on K8s, we cannot test the failure scenarios in an automated sense. What follows is an outline of the manual tests that should be performed to validate Gateway changes that may have an effect on the integration contract between Gateway Operator and Metacontroller.

## Results
Last Test Execution: 08/Jan/2020

|Test|Result|
| --- | --- |
|[Schema Validation 1](#no-path-validation)|Pass|
|[Schema Validation 2](#empty-path-validation)|Pass|
|[Schema Validation 3](#empty-operation-for-path-validation)|Pass|
|[Admission Controller 1](#path-wildcard-in-middle-of-path)|Pass|
|[Admission Controller 2](#single-service-provider-defined)|Pass|
|[StackSets 1](#stackset-polling)|Pass|
|[StackSets 2](#testing-load-balancing)|Pass|

## Scenarios
All resources required for these tests can be found in the [resources](resources) folder...

### Admission Controller / Schema Validation
The Gateway Operator uses an admission controller webhook to reject FabricGateway resource Create/Update operations if the resource does not pass a number of defined checks

#### No Path Validation
 1. Attempt to create a resource with no path key:    
   `zkubectl apply -f resources/invalidResourceWithNoPathKey.yaml`
 1. You receive a message stating:    
   `spec.paths in body is required`  
 1. To check that no resource has been created, run the below and ensure that `no resources found` is returned:    
   `zkubectl get TestFabricGateway invalid-resource`

#### Empty Path Validation
 1. Attempt to create a resource with an empty object for the path key:    
   `zkubectl apply -f resources/invalidResourceWithEmptyPathObject.yaml`
 1. You receive a message stating:    
   `spec.paths in body should have at least 1 properties`  
 1. To check that no resource has been created, run the below and ensure that `no resources found` is returned:    
   `zkubectl get TestFabricGateway invalid-resource`

#### Empty Operation for Path Validation
 1. Attempt to create a resource with no operation defined for a path:    
   `zkubectl apply -f resources/invalidResourcePathWithEmptyOperation.yaml`
 1. You receive a message stating:    
   `spec.paths./api in body should have at least 1 properties`  
 1. To check that no resource has been created, run the below and ensure that `no resources found` is returned:    
   `zkubectl get TestFabricGateway invalid-resource`  

#### Path Wildcard in middle of path
 1. Attempt to create a resource with an invalidly placed wildcard:    
   `zkubectl apply -f resources/invalidResourcePathWithWildcardInMiddle.yaml`
 1. You receive a message stating:    
   `A Path can only contain ** as the last element in the path`  
 1. To check that no resource has been created, run the below and ensure that `no resources found` is returned:    
   `zkubectl get TestFabricGateway invalid-resource`

#### Single Service Provider Defined
 1. Attempt to create a resource with two service providers defined:    
   `zkubectl apply -f resources/invalidResourceMultipleServiceProvidersDefined.yaml`
 1. You receive a message stating:    
   `You cannot define services with the "x-fabric-service" key and also set external management using "x-external-service-provider"`
 1. Attempt to create a resource with no service providers defined:    
    `zkubectl apply -f resources/invalidResourceNoServiceProvidersDefined.yaml`
 1. You receive a message stating:
    `You must have 1 of the "x-fabric-service" or "x-external-service-provider" keys defined`   
 1. To check that no resource has been created, run the below and ensure that `no resources found` is returned:    
   `zkubectl get TestFabricGateway invalid-resource`

### StackSet Integration
`StackSets` are the first external service manager for a `TestFabricGateway`. They integrate by setting the appropriate metadata on a `TestFabricGateway` resource.

#### StackSet Polling
 1. Attempt to create a resource with `x-external-service-provider` key defined:    
   `zkubectl apply -f resources/validResourceWithServicesManagedByStackSet.yaml`
 1. The operation should complete successfully with the below message:    
   `fabricgateway.zalando.org/managed-by-stackset-1 created`
 1. There should be no ingresses created for the resource yet, describing the resource with below command should have an `INGRESS_COUNT` of 0:    
   `zkubectl get TestFabricGateway managed-by-stackset-1`
 1. Create the associated stackset with the below command.    
   `zkubectl apply -f resources/stackSetup.yaml`
 1. There should be now be ingresses created for the resource, describe the resource with below command and check we have an `INGRESS_COUNT` of 3:    
   `zkubectl get TestFabricGateway managed-by-stackset-1`
 1. Ensure that the `zalando.org/backend-weights: '{"ss-stack-v1":100}'` annotation is on the created ingress by executing the below:    
   `zkubectl get ingress managed-by-stackset-1-get-api-all -o yaml`
 1. Tear Down    
   ```
  zkubectl delete -f resources/validResourceWithServicesManagedByStackSet.yaml    
  zkubectl delete -f resources/stackSetup.yaml
   ```

#### Testing Load Balancing
  1. Attempt to create a resource managed by stacksets:    
   ```
   zkubectl apply -f resources/stackSetup.yaml    
   zkubectl apply -f resources/validResourceWithServicesManagedByStackSet.yaml    
   zkubectl apply -f resources/stackUpdate.yaml    
   ```    
  1. Ensure that the current `INGRESS_COUNT` is 3    
    `zkubectl get TestFabricGateway managed-by-stackset-1`
  1. Ensure that both stacks appear in the weights annotation: `zalando.org/backend-weights: '{"ss-stack-v1":100, "ss-stack-v2":0}'`:    
     `zkubectl get ingress managed-by-stackset-1-get-api-all -o yaml`
  1. Move some traffic over to the new stack using the below command    
    `zkubectl traffic ss-stack ss-stack-v2 50`
  1. Make sure the weights annotation has updated on the ingress: `zalando.org/backend-weights: '{"ss-stack-v1":50, "ss-stack-v2":50}'`:    
    `zkubectl get ingress managed-by-stackset-1-get-api-all -o yaml`
  1. Fire some curl request like below and check that the response is roughly even between "Stack V1" and "Stack V2"`:    
    `curl -i -H "Authorization: Bearer $(ztoken)" https://test.playground.zalan.do/api`    
  1. Tear Down    
   ```
   zkubectl delete -f resources/validResourceWithServicesManagedByStackSet.yaml    
   zkubectl delete -f resources/stackUpdate.yaml
   ```