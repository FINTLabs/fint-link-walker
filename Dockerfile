FROM gradle:4.10.3-jdk8-alpine as builder
USER root
COPY . .
RUN gradle --no-daemon build

FROM gcr.io/distroless/java:8
ENV JAVA_TOOL_OPTIONS -XX:+ExitOnOutOfMemoryError
COPY --from=builder /home/gradle/build/libs/*.jar /data/app.jar
CMD ["/data/app.jar"]
