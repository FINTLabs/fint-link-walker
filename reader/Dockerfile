FROM ghcr.io/graalvm/graalvm-community:21-ol9 AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon :reader:build -Dquarkus.native.enabled=true -Dquarkus.package.jar.enabled=false -x test \
 && find /workspace/reader/build -maxdepth 1 -name "*-runner" -type f -exec cp {} /reader-binary \;

FROM debian:12-slim AS libs

FROM gcr.io/distroless/cc-debian12
EXPOSE 8080
COPY --from=libs /usr/lib/x86_64-linux-gnu/libz.so.1* /usr/lib/x86_64-linux-gnu/
COPY --from=builder /reader-binary /app/reader
ENTRYPOINT ["/app/reader"]
