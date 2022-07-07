ARG MAVEN_TAG=3.8-openjdk-17
FROM maven:${MAVEN_TAG} as maven-build

# files in /usr/share/maven/ref are copied into the maven config directory on run (not build).
WORKDIR /usr/share/maven/ref/

# set /usr/share/maven/ref as local repo, as expected by maven image
ADD files/settings-nexus-local.xml .
# does not set a local repo, used when mounting home
ADD files/settings-nexus.xml .
