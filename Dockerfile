FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
COPY shapes ./shapes
COPY vocabularies ./vocabularies
COPY fixtures ./fixtures
COPY repairs ./repairs
RUN mvn -B -ntp package

FROM eclipse-temurin:21-jre

WORKDIR /workspace
COPY --from=build /workspace/target/warsampo-linter.jar /opt/warsampo-linter/warsampo-linter.jar
COPY shapes /workspace/shapes
COPY vocabularies /workspace/vocabularies
COPY repairs /workspace/repairs

ENTRYPOINT ["java", "-jar", "/opt/warsampo-linter/warsampo-linter.jar"]
