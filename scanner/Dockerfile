FROM gradle:8.13-jdk21 AS builder
USER root
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon :scanner:bootJar -x test

FROM gcr.io/distroless/java21
ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError"
COPY --from=builder /workspace/scanner/build/libs/app.jar /app/app.jar
CMD ["/app/app.jar"]
