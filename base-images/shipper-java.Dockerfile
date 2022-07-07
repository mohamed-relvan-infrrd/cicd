ARG JAVA_VERSION=16
ARG OPENJDK_TAG=${JAVA_VERSION}-jdk-slim

FROM openjdk:${OPENJDK_TAG}

# Add custom certs to CA store, etc
