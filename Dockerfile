FROM gradle:8.14.3-jdk21 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
RUN gradle --no-daemon dependencies > /dev/null || true
COPY src ./src
RUN gradle --no-daemon installDist

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/install/flagstone ./
COPY flags ./flags
ENV JAVA_OPTS="-Xms16m -Xmx96m -XX:MaxMetaspaceSize=96m -XX:+UseSerialGC"
EXPOSE 8080
CMD ["bin/flagstone"]
