FROM nginx:1.28-alpine

RUN apk add --no-cache nginx-mod-http-dav-ext openssl

COPY nginx.conf /etc/nginx/nginx.conf
COPY htpasswd /etc/nginx/.htpasswd

RUN mkdir -p /data/recordings && \
    chown -R nginx:nginx /data/recordings

RUN mkdir -p /etc/nginx/ssl && \
    openssl req -x509 -nodes -days 3650 \
      -newkey rsa:2048 \
      -keyout /etc/nginx/ssl/server.key \
      -out /etc/nginx/ssl/server.crt \
      -subj "/CN=localhost"

EXPOSE 443
