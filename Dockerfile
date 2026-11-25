# Shared multi-stage Dockerfile for every RideHail Java microservice.
# Build with: docker build --build-arg SERVICE_MODULE=user-service -t ridehail/user-service .
# (context must be the java-services/ directory so the whole Maven reactor is visible)

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy every module's pom.xml first so Maven can resolve the reactor and cache dependencies
# in their own layer, independent of source-code changes.
COPY pom.xml .
COPY ridehail-common/pom.xml ridehail-common/pom.xml
COPY common-service/pom.xml common-service/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY driver-service/pom.xml driver-service/pom.xml
COPY ride-service/pom.xml ride-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY admin-service/pom.xml admin-service/pom.xml
COPY helperdesk-service/pom.xml helperdesk-service/pom.xml
COPY socket-service/pom.xml socket-service/pom.xml
RUN mvn -q -B -pl ridehail-common install -DskipTests || true

COPY . .

ARG SERVICE_MODULE
RUN mvn -q -B -pl ${SERVICE_MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
ARG SERVICE_MODULE
ENV SERVICE_JAR=${SERVICE_MODULE}.jar
WORKDIR /app
COPY --from=build /workspace/${SERVICE_MODULE}/target/${SERVICE_MODULE}.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

