FROM openjdk:11-jre-slim
VOLUME /tmp

RUN apt update && apt install curl jq -y

COPY dependency/BOOT-INF/lib /app/lib
COPY dependency/META-INF /app/META-INF
COPY dependency/BOOT-INF/classes /app
COPY dependency/entrypoint.sh /entrypoint.sh
RUN ["chmod", "+x", "/entrypoint.sh"]

HEALTHCHECK --interval=5s --timeout=3s --start-period=30s \
 CMD curl --silent --fail --request GET http://localhost:8080/actuator/health \
                 | jq --exit-status '.status == "UP"' || exit 1

EXPOSE 8080
ENTRYPOINT ["/entrypoint.sh"]
