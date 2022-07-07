ARG NGINX_TAG=1.21.3
FROM nginx:${NGINX_TAG}

# this will need to change to apk commands when using alpine
RUN apt-get update && apt-get install curl -y
