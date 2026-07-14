# Stage 1: Build backend
FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Build frontend
FROM node:20-alpine AS frontend-build
WORKDIR /app
COPY web/package*.json ./
RUN npm ci
COPY web/ .
RUN npm run build

# Stage 3: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy backend JAR
COPY --from=backend-build /app/target/*.jar app.jar

# Copy frontend dist into Spring Boot static directory
COPY --from=frontend-build /app/dist/ /app/static/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
