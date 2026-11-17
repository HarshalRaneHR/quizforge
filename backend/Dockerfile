# --- build stage ---
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline          # cached layer: only re-runs when pom.xml changes
COPY src ./src
RUN mvn -q package -DskipTests

# --- runtime stage (small JRE image, no Maven) ---
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
