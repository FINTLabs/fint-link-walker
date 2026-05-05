FROM gradle:8.13-jdk21 AS builder
USER root
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon :reader:bootJar -x test

FROM gcr.io/distroless/java21
ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError"
EXPOSE 8081
COPY --from=builder /workspace/reader/build/libs/app.jar /app/app.jar
CMD ["/app/app.jar"]
