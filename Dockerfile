# Lightweight web server
FROM nginx:alpine

# Remove default content
RUN rm -rf /usr/share/nginx/html/*

# Copy offline repo
COPY offline-repo/ /usr/share/nginx/html/

# Expose port
EXPOSE 8181

# Configure nginx to use port 8081
RUN sed -i 's/listen       80;/listen 8181;/' /etc/nginx/conf.d/default.conf
