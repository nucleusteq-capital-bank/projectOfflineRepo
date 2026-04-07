FROM nginx:alpine

# Copy the offline repo built locally by 'gradle buildOfflineRepo'
# Run 'gradle buildOfflineRepo' before building this image
COPY build/offline-repo/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
