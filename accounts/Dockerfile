FROM maven:3.9-amazoncorretto-25-alpine AS builder
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

FROM alpine:3.23
LABEL "org.opencontainers.image.authors"="ggoutos.com"
WORKDIR /app
COPY --from=builder /custom-jre /opt/jre
COPY --from=builder /app/target/*.jar app.jar
ENV PATH="/opt/jre/bin:$PATH"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]


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