FROM registry.opensource.zalan.do/library/openjdk-11-jre-slim:latest

LABEL Description="Gateway Operator. K8s operstor to orchestrate desired Ingress resources"
LABEL maintainer="Team fabric <team-fabric@zalando.ie>"

WORKDIR /

COPY target/scala-2.12/*.jar /gateway-operator.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/gateway-operator.jar"]