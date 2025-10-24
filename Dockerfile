FROM eclipse-temurin:25-jdk-jammy AS builder

WORKDIR /app

COPY .mvn/ .mvn
COPY mvnw pom.xml ./

RUN ./mvnw dependency:go-offline -B

COPY src ./src

RUN ./mvnw package -DskipTests -B


FROM eclipse-temurin:25-jre-jammy AS final

WORKDIR /app

# Install 'curl' in this minimal image for the healthcheck to work.
# Also clean up the apt-get cache afterward to keep the image small.
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

