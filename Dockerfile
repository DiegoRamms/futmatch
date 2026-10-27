FROM gradle:8.14.4-jdk17-alpine AS build
RUN apk update \
    && apk upgrade --no-cache ca-certificates \
    && update-ca-certificates
WORKDIR /home/app
COPY . .
RUN gradle installDist --no-daemon

FROM eclipse-temurin:17-jre-alpine
RUN apk update \
    && apk upgrade --no-cache ca-certificates \
    && update-ca-certificates \
    && addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app
WORKDIR /app
COPY --from=build --chown=10001:10001 /home/app/build/install/futmatch /app
ENV PORT=8080
EXPOSE 8080
# Force production mode explicitly
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dio.ktor.development=false"
USER 10001:10001
CMD ["./bin/futmatch"]
