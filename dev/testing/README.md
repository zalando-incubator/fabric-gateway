# Manual Test Plan for Gateway Operator
In accordance with the principles of continuous delivery, we strive to cover off the majority of tests for the Gateway product with an automated test suite. These tests are executed on CDP for each push to a branch which has an open PR for it. These tests cover three different areas, unit testing, integration testing and system tests. Due to how we integrate with metacontroller in the architecture of the Gateway operator, these system tests are quite important. Unfortunately due to the nature of how we use CDP to provision resources on K8s, we cannot test the failure scenarios in an automated sense. What follows is an outline of the manual tests that should be performed to validate Gateway changes that may have an effect on the integration contract between Gateway Operator and Metacontroller.

## Results
Last Test Execution: 17/Sep/2019

|Test|Result|
| --- | --- |
|[Schema Validation 1](#no-path-validation)|Pass|
|[Schema Validation 2](#empty-path-validation)|Pass|
|[Schema Validation 3](#empty-operation-for-path-validation)|Pass|
|[Admission Controller 1](#path-wildcard-in-middle-of-path)|Pass|
|[Admission Controller 2](#single-service-provider-defined)|Pass|
|[StackSets 1](#metadata-propogation)|Pass|
|[StackSets 2](#removing-metadata-removes-ingress)|Pass|
|[StackSets 3](#gateway-resource-updates-preserve-metadata)|Pass|
|[StackSets 4](#updating-metadata-results-in-ingress-updates)|Pass|

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
   `zkubectl get FabricGateway invalid-resource`

#### Empty Path Validation
 1. Attempt to create a resource with an empty object for the path key:    
   `zkubectl apply -f resources/invalidResourceWithEmptyPathObject.yaml`
 1. You receive a message stating:    
   `spec.paths in body should have at least 1 properties`  
 1. To check that no resource has been created, run the below and ensure that `no resources found` is returned:    
   `zkubectl get FabricGateway invalid-resource`

#### Empty Operation for Path Validation
 1. Attempt to create a resource with no operation defined for a path:    
   `zkubectl apply -f resources/invalidResourcePathWithEmptyOperation.yaml`
 1. You receive a message stating:    
   `spec.paths./api in body should have at least 1 properties`  
 1. To check that no resource has been created, run the below and ensure that `no resources found` is returned:    
   `zkubectl get FabricGateway invalid-resource`  

#### Path Wildcard in middle of path
 1. Attempt to create a resource with an invalidly placed wildcard:    
   `zkubectl apply -f resources/invalidResourcePathWithWildcardInMiddle.yaml`
 1. You receive a message stating:    
   `A Path can only contain ** as the last element in the path`  
 1. To check that no resource has been created, run the below and ensure that `no resources found` is returned:    
   `zkubectl get FabricGateway invalid-resource`

#### Single Service Provider Defined
 1. Attempt to create a resource with two service providers defined:    
   `zkubectl apply -f resources/invalidResourceMultipleServiceProvidersDefined.yaml`
 1. You receive a message stating:    
   `You cannot define a service "x-fabric-service" and also set external management like "x-service-definition: stackset"`
 1. Attempt to create a resource with no service providers defined:    
    `zkubectl apply -f resources/invalidResourceNoServiceProvidersDefined.yaml`
 1. You receive a message stating:
    `You must have at least 1 "x-fabric-service" defined, or mark the gateway as "x-service-definition: stackset"`   
 1. To check that no resource has been created, run the below and ensure that `no resources found` is returned:    
   `zkubectl get FabricGateway invalid-resource`

### StackSet Integration
`StackSets` are the first external service manager for a `FabricGateway`. They integrate by setting the appropriate metadata on a `FabricGateway` resource.

#### Metadata Propogation
 1. Attempt to create a resource with `x-service-definition` set to `stackset`:    
   `zkubectl apply -f resources/validResourceWithServicesManagedByStackSet.yaml`
 1. The operation should complete successfully with the below message:    
   `fabricgateway.zalando.org/managed-by-stackset-1 created`
 1. There should be no ingresses created for the resource yet, describing the resource with below command should have an `INGRESS_COUNT` of 0:    
   `zkubectl get FabricGateway managed-by-stackset-1`
 1. We need to create two services and a deployment for the next part. Execute the below commands and ensure they all complete successfully.    
   `zkubectl apply -f resources/stackSetup.yaml`
 1. Start to edit the resource with the below command:    
   `zkubectl edit FabricGateway managed-by-stackset-1`    
    Add the contents of [this file](resources/stackset_metadata.json) under `metadata.annotations."zalando.org/ingress-override"` path in the YAML
 1. There should be now be ingresses created for the resource, describe the resource with below command and check we have an `INGRESS_COUNT` of 3:    
   `zkubectl get FabricGateway managed-by-stackset-1`
 1. Ensure that the `zalando.org/backend-weights` annotation is on the created ingress by executing the below:    
   `zkubectl get ingress managed-by-stackset-1-get-api-all -o yaml`
 1. Validate that the requests are being roughly balanced 4:1 to each service by issuing a number of requests and checking that the deplpoyment id periodically changes:    
   `curl -si -H "Authorization: Bearer $(ztoken)" https://fg-manual-tests.smart-product-platform-test.zalan.do/api | grep Deploymentidentifier`
 1. Tear Down    
   ```
  zkubectl delete -f resources/validResourceWithServicesManagedByStackSet.yaml    
  zkubectl delete -f resources/stackSetup.yaml
   ```

#### Removing Metadata Removes Ingress
  1. Attempt to create a resource managed by stacksets:    
   ```
   zkubectl apply -f resources/stackSetup.yaml    
   zkubectl apply -f resources/validResourceWithServicesManagedByStackSet.yaml
   ```
  1. Add the metadata annotation as per [this test](#metadata-propogation):    
  1. Ensure that the current `INGRESS_COUNT` is 3    
    `zkubectl get FabricGateway managed-by-stackset-1`
  1. Edit the `FabricGateway` resource again and remove the annotation added in the above step:    
    `zkubectl edit FabricGateway managed-by-stackset-1`
  1. Ensure that the current `INGRESS_COUNT` is 0    
    `zkubectl get FabricGateway managed-by-stackset-1`
  1. Check that the previously existing ingressii are gone:    
    `zkubectl get ingress managed-by-stackset-1-get-api-all`
  1. Tear Down    
   ```
   zkubectl delete -f resources/validResourceWithServicesManagedByStackSet.yaml    
   zkubectl delete -f resources/stackSetup.yaml
   ```

#### Gateway Resource Updates Preserve Metadata
  1. Attempt to create a resource managed by stacksets:    
   ```
   zkubectl apply -f resources/stackSetup.yaml    
   zkubectl apply -f resources/validResourceWithServicesManagedByStackSet.yaml
   ```
  1. Add the metadata annotation as per [this test](#metadata-propogation):    
  1. Ensure that the current `INGRESS_COUNT` is 3    
    `zkubectl get FabricGateway managed-by-stackset-1`
  1. Run the below to add an extra path to the `FabricGateway` resource:    
    `zkubectl apply -f resources/validUpdatedManagedResource.yaml`
  1. Ensure that the current `INGRESS_COUNT` is 4    
    `zkubectl get FabricGateway managed-by-stackset-1`
  1. Ensure that the `zalando.org/backend-weights` annotation is still on the original ingress:    
    `zkubectl get ingress managed-by-stackset-1-get-api-all -o yaml`
  1. Tear Down    
   ```
   zkubectl delete -f resources/validResourceWithServicesManagedByStackSet.yaml    
   zkubectl delete -f resources/stackSetup.yaml
   ```

#### Updating Metadata results in Ingress Updates
  1. Attempt to create a resource managed by stacksets:    
   ```
   zkubectl apply -f resources/stackSetup.yaml    
   zkubectl apply -f resources/validResourceWithServicesManagedByStackSet.yaml
   ```
  1. Add the metadata annotation as per [this test](#metadata-propogation):    
  1. Ensure that the correct `zalando.org/backend-weights` annotation is on the ingress:    
    `zkubectl get ingress managed-by-stackset-1-get-api-all -o yaml`
  1. Start to edit the resource with the below command:    
    `zkubectl edit FabricGateway managed-by-stackset-1`    
     Update the `zalando.org/ingress-override` annotation with the contents of [this file](resources/stackset_metadata_update.json)    
  1. Ensure that the correct `zalando.org/backend-weights` annotation is on the ingress:    
    `zkubectl get ingress managed-by-stackset-1-get-api-all -o yaml`  
  1. Tear Down    
   ```
   zkubectl delete -f resources/validResourceWithServicesManagedByStackSet.yaml    
   zkubectl delete -f resources/stackSetup.yaml
   ```