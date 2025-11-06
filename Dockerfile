# Build Stage
FROM maven:3.8.6-jdk-8 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests


# Runtime Stage
# Alpine-based OpenJDK 8 images were removed from Docker Hub.
# Use the Temurin JRE 8 image instead to keep the runtime lean.
FROM eclipse-temurin:8-jre

# Set the working directory in the container
WORKDIR /app

# Copy the compiled Spring Boot application JAR file into the container at /app
COPY --from=build /app/target/*.jar my-spring-boot-app.jar

# Expose the port that the Spring Boot application will run on
EXPOSE 8080

# Specify the command to run on container startup
CMD ["java", "-jar", "/app/my-spring-boot-app.jar"]
