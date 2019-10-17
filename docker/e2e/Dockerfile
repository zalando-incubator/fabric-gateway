FROM registry.opensource.zalan.do/stups/openjdk:latest

# Install SBT
RUN curl -sLO https://github.com/sbt/sbt/releases/download/v1.2.0/sbt-1.2.0.tgz
RUN tar xzf sbt-1.2.0.tgz -C /usr/local --strip-components=1

# Copy over project deps
COPY project /e2e/project/
COPY src /e2e/src
COPY build.sbt /e2e/build.sbt

# Set working directory
WORKDIR /e2e