FROM openjdk:11-jre-slim

LABEL Description="Gateway Operator. K8s operstor to orchestrate desired Ingress resources"
MAINTAINER Team fabric <team-fabric@zalando.ie>

WORKDIR /

COPY target/scala-2.12/*.jar /gateway-operator.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/gateway-operator.jar"]