ARG PROJECT_ARTIFACT_ID=guacamole-service

############ BUILD STAGE ############
FROM maven:3.9.4-eclipse-temurin-21-alpine AS build
WORKDIR /app

ARG PROJECT_ARTIFACT_ID
# Extra options
ARG MAVEN_CLI_OPTS=""

COPY pom.xml /app/pom.xml
COPY src /app/src

# Build JAR file
RUN mvn -ntp clean install -DskipTests -DskipChecks=true $MAVEN_CLI_OPTS && \
    cp /app/target/$PROJECT_ARTIFACT_ID-*.jar /app/$PROJECT_ARTIFACT_ID.jar

############ RUNNABLE STAGE ############
FROM eclipse-temurin:21-jre-noble AS runnable

WORKDIR /app

ARG PROJECT_ARTIFACT_ID

ENV PROJECT_ARTIFACT_ID=${PROJECT_ARTIFACT_ID}

COPY etc/$PROJECT_ARTIFACT_ID.properties /app/etc/$PROJECT_ARTIFACT_ID.properties
COPY entrypoint.sh /app/entrypoint.sh
COPY --from=build /app/$PROJECT_ARTIFACT_ID.jar ./

RUN apt-get update && \
    # Required to use nc command in the wait for it function, see entrypoint.sh
    apt-get install -y netcat-traditional && \
    # Make a file executable
    chmod a+x entrypoint.sh

EXPOSE 8087

ENTRYPOINT ["./entrypoint.sh"]
