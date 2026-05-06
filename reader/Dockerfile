FROM ghcr.io/graalvm/graalvm-community:21-ol9 AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon :reader:nativeCompile -x test

# libz isn't in distroless/cc; pull from a Debian 12 image (ABI-compatible base).
FROM debian:12-slim AS libs

FROM gcr.io/distroless/cc-debian12
EXPOSE 8080
COPY --from=libs /usr/lib/x86_64-linux-gnu/libz.so.1* /usr/lib/x86_64-linux-gnu/
COPY --from=builder /workspace/reader/build/native/nativeCompile/reader /app/reader
ENTRYPOINT ["/app/reader"]
