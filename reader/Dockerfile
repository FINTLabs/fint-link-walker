FROM gradle:9.5.0-jdk25 AS builder
USER root
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon :reader:bootJar -x test

FROM gcr.io/distroless/java25
ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
COPY --from=builder /workspace/reader/build/libs/app.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
