# TODO: 
# - Upgrade to recent, secure JDK that is still compatible with the app
# - Look at using public openjdk image on Docker Hub
#   https://hub.docker.com/_/openjdk
FROM ubuntu
ADD https://download.java.net/java/GA/jdk12.0.2/e482c34c86bd4bf8b56c0b35558996b9/10/GPL/openjdk-12.0.2_linux-x64_bin.tar.gz /usr/local/lib
WORKDIR /usr/local/lib
RUN tar xvzf openjdk-12.0.2_linux-x64_bin.tar.gz 
RUN apt-get update && apt install jq -y && apt install curl -y
RUN rm -rf openjdk-12.0.2_linux-x64_bin.tar.gz
ENV JAVA_HOME=/usr/local/lib/jdk-12.0.2
ENV PATH=$PATH:$JAVA_HOME/bin