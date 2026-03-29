# Build app with JDK and then run with a minimal JRE
FROM maven:3.9-amazoncorretto-25-alpine AS jvm-builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Build a minimal JRE with only the modules your app needs
RUN apk add --no-cache binutils && \
    $JAVA_HOME/bin/jlink \
      --module-path $JAVA_HOME/jmods \
      --add-modules ALL-MODULE-PATH \
      --strip-debug \
      --no-man-pages \
      --no-header-files \
      --compress=2 \
      --output /custom-jre

# Runtime stage with the custom JRE and the app jar
FROM alpine:3.23 AS jvm
LABEL "org.opencontainers.image.authors"="ggoutos.com"
WORKDIR /app
COPY --from=jvm-builder /custom-jre /opt/jre
COPY --from=jvm-builder /app/target/*.jar app.jar
ENV PATH="/opt/jre/bin:$PATH"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]


# ---- Build app as GraalVM native Image ----
FROM ghcr.io/graalvm/native-image-community:25 AS native-builder
# Install Maven
RUN microdnf install -y maven
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build the native executable
COPY src/ src/
RUN mvn -Pnative -DskipTests package

# ---- Runtime stage for GraalVM native Image ----
FROM debian:bookworm-slim AS native
LABEL "org.opencontainers.image.authors"="ggoutos.com"
WORKDIR /app
COPY --from=native-builder /app/target/accounts /app/accounts
EXPOSE 8080
ENTRYPOINT ["/app/accounts"]



##Start with a base image containing Java runtime
#FROM eclipse-temurin:21-jdk
#
## MAINTAINER instruction is deprecated in favor of using label
## MAINTAINER eazybytes.com
##Information around who maintains the image
#LABEL "org.opencontainers.image.authors"="eazybytes.com"
#
## Add the application's jar to the image
#COPY target/accounts-0.0.1-SNAPSHOT.jar accounts-0.0.1-SNAPSHOT.jar
#
## execute the application
#ENTRYPOINT ["java", "-jar", "accounts-0.0.1-SNAPSHOT.jar"]