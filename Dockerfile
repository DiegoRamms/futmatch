FROM gradle:8.10.2-jdk17-alpine AS build
WORKDIR /home/app
COPY . .
RUN gradle installDist --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /home/app/build/install/futmatch /app
ENV PORT=8080
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
CMD ["sh", "-c", "./bin/futmatch"]