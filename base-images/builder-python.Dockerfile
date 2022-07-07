# TODO:
# - Upgrade to recent, secure version of Python that is still compatible
FROM python:3.7-stretch

RUN apt-get update && apt-get install -y poppler-utils