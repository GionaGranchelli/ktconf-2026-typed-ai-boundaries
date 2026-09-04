# Build the Vue console and embed it in the Spring Boot jar.
FROM node:22-bookworm-slim AS frontend
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS backend-build
WORKDIR /workspace
COPY . .
COPY --from=frontend /workspace/app/src/main/resources/static/gtc/ app/src/main/resources/static/gtc/
RUN ./gradlew :app:bootJar --no-daemon --console=plain

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=backend-build /workspace/app/build/libs/ktconf-demo.jar ./ktconf-demo.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/ktconf-demo.jar"]
