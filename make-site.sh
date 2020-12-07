#!/usr/bin/env bash

release_version=master-4

cd docs-raw/site
mkdocs build -d ../../docs

cd ../..
combined_install='docs/assets/basic-install.yaml'
cat deploy/operator/apply/01_FabricGatewayCRD.yaml > ""${combined_install}""
echo -e '\n---\n' >> "${combined_install}"
cat deploy/operator/apply/02_GatewayCRDSchemaValidation.yaml >> "${combined_install}"
echo -e '\n---\n' >> "${combined_install}"
cat deploy/operator/apply/03_MetacontrollerGatewaySynchHook.yaml >> "${combined_install}"
echo -e '\n---\n' >> "${combined_install}"
cat deploy/operator/apply/08_OperatorService.yaml >> "${combined_install}"
echo -e '\n---\n' >> "${combined_install}"
cat deploy/operator/apply/07_OperatorDeployment.yaml >> "${combined_install}"
sed -i 's#{{{IMAGE}}}#registry.opensource.zalan.do/fabric/gateway-k8s-operator#' "${combined_install}"
sed -i "s/{{{VERSION}}}/${release_version}/" "${combined_install}"
sed -i "s/{{{ CRD_PLURAL }}}/fabricgateways/" "${combined_install}"
sed -i "s/{{{ CRD_KIND }}}/FabricGateway/" "${combined_install}"
sed -i "s/{{{ CRD_SINGULAR }}}/fabricgateway/" "${combined_install}"
sed -i "s/{{{ CRD_SHORT_NAME }}}/fg/" "${combined_install}"
sed -i "s/{{{ COMPOSITE_CONTROLLER_NAME }}}/gateway-operator/" "${combined_install}"
sed -i "s/{{{ VERSIONED_HOSTS_BASE_DOMAIN }}}/example.com/" "${combined_install}"
