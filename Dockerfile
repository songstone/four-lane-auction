# syntax=docker/dockerfile:1

FROM node:24-bookworm-slim@sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205 AS frontend-build
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm node --version && npm --version && npm ci
COPY frontend/ ./
RUN npm run lint && npm run typecheck && npm run build

FROM amazoncorretto:21-al2023@sha256:1a307926702559b0086a5c4d339eaafa9c992b3e8ba54201ac8ebbde31d22f0b AS backend-build
WORKDIR /build/backend
COPY backend/ ./
# Only the container build receives static assets; bootRun and Vite stay separate.
COPY --from=frontend-build /build/frontend/dist/ app/src/main/resources/static/
RUN --mount=type=cache,target=/root/.gradle \
    javac -version \
    && java -jar gradle/wrapper/gradle-wrapper.jar --no-daemon :app:bootJar --console=plain \
    && cp app/build/libs/*.jar /build/app.jar

FROM amazoncorretto:21-al2023-headless@sha256:212faf5f8f93d8f4854a3d89528834d11ad2d685de80721d4d24ac2fdd3e379e AS runtime
ARG VCS_REF
ARG IMAGE_VERSION
RUN test -n "$VCS_REF" && test -n "$IMAGE_VERSION" \
    && command -v curl >/dev/null \
    && mkdir -p /app/data/images && chown 10001:10001 /app /app/data /app/data/images
LABEL org.opencontainers.image.source="https://github.com/songstone/four-lane-auction" \
      org.opencontainers.image.revision="$VCS_REF" \
      org.opencontainers.image.version="$IMAGE_VERSION"
WORKDIR /app
COPY --from=backend-build --chown=10001:10001 /build/app.jar ./app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-Xms256m -Xmx768m -Duser.timezone=UTC"
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
    CMD curl --fail --silent --show-error --max-time 3 http://127.0.0.1:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
