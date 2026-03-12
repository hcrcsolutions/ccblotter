FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn install -N -q
COPY am-common/ am-common/
COPY am-ivr/ am-ivr/
COPY ivr-server/ ivr-server/
RUN mvn package -pl am-common,am-ivr,ivr-server -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/ivr-server/target/ivr-server-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
