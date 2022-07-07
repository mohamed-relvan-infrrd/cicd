# Building on Alpine Linux instead of Debian; base image is 16MB!
ARG TAG=3.6.9
FROM python:${TAG}

RUN mkdir -p /root/.ssh
ADD id_rsa /root/.ssh/id_rsa
RUN chmod 700 /root/.ssh/id_rsa
RUN echo "Host github.com\n\tStrictHostKeyChecking no\n" >> /root/.ssh/config
