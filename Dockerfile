FROM openjdk:21-jdk-slim

# Install curl for healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml first for better caching
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Download dependencies (this layer will be cached)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN ./mvnw clean package -DskipTests

# Create uploads directory with proper permissions
RUN mkdir -p uploads/photos uploads/videos uploads/thumbnails && \
    chmod 755 uploads uploads/photos uploads/videos uploads/thumbnails

# Expose port
EXPOSE 8080

# Set JVM options for container
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Run the application
CMD ["java", "-jar", "target/familynest-0.0.1-SNAPSHOT.jar"] 