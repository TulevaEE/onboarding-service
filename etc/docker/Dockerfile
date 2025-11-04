FROM eclipse-temurin:21
VOLUME /tmp

RUN apt update && apt install curl jq openssl unzip -y

# Install AWS CLI v2
RUN curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && \
    unzip awscliv2.zip && \
    ./aws/install && \
    rm -rf aws awscliv2.zip

# This file is referenced from packaging.gradle.kts
COPY rds-ca-2019-root.pem rds-2019.pem
COPY rds-ca-2024-root.pem rds-2024.pem

RUN openssl x509 -in rds-2019.pem -inform pem -out rds-2019.der -outform der
RUN echo yes | keytool -importcert -alias rdsroot-2019 -cacerts -storepass changeit -file rds-2019.der

RUN openssl x509 -in rds-2024.pem -inform pem -out rds-2024.der -outform der
RUN echo yes | keytool -importcert -alias rdsroot -cacerts -storepass changeit -file rds-2024.der

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
