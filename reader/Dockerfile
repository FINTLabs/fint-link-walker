FROM ghcr.io/graalvm/graalvm-community:21-ol9 AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon :reader:nativeCompile -x test

FROM gcr.io/distroless/cc-debian12
EXPOSE 8080
COPY --from=builder /workspace/reader/build/native/nativeCompile/reader /app/reader
ENTRYPOINT ["/app/reader"]
