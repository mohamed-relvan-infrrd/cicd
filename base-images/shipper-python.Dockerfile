# Building on Alpine Linux instead of Debian; base image is 16MB!
ARG TAG=3.7-alpine3.14
FROM python:${TAG}

RUN apk update && apk add poppler-utils