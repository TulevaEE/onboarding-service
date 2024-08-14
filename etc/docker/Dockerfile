FROM eclipse-temurin:21
VOLUME /tmp

RUN apt update && apt install curl jq openssl -y

COPY dependency/import-certs.sh /import-certs.sh
RUN ["chmod", "+x", "/import-certs.sh"]

# This file is referenced from packaging.gradle.kts
COPY rds-ca-eu-central-1-bundle-2024.pem rds.pem
RUN ["/import-certs.sh", "rds.pem"]
# RUN openssl x509 -in rds.pem -inform pem -out rds.der -outform der
# RUN echo yes | keytool -importcert -alias rdsroot -cacerts -storepass changeit -file rds.der

COPY dependency/BOOT-INF/lib /app/lib
COPY dependency/META-INF /app/META-INF
COPY dependency/BOOT-INF/classes /app
COPY dependency/entrypoint.sh /entrypoint.sh
RUN ["chmod", "+x", "/entrypoint.sh"]

HEALTHCHECK --interval=5s --timeout=3s --retries=3 --start-period=30s \
 CMD curl --silent --fail --request GET http://localhost:5000/actuator/health \
                 | jq --exit-status '.status == "UP"' || exit 1

EXPOSE 5000
ENTRYPOINT ["/entrypoint.sh"]
