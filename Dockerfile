FROM nginx:1.27-alpine
 
# Remove default config
RUN rm /etc/nginx/conf.d/default.conf
 
# Copy custom nginx config
COPY nginx.conf /etc/nginx/nginx.conf
 
# Clean default html
RUN rm -rf /usr/share/nginx/html/*
 
# Copy offline repo
COPY build/offline-repo/ /usr/share/nginx/html/
 
EXPOSE 80
 
CMD ["nginx", "-g", "daemon off;"]