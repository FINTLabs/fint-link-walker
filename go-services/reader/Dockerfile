# syntax=docker/dockerfile:1.7
#
# Build context = repo root.
#   docker build -f go-services/reader/Dockerfile -t reader .
#
# Same shape as go-services/scanner/Dockerfile — see comments there for
# the why on each step.

FROM --platform=$BUILDPLATFORM golang:1.26-alpine AS builder

ARG VERSION=dev
ARG COMMIT=unknown
ARG BUILD_DATE=unknown
ARG TARGETOS
ARG TARGETARCH

WORKDIR /src

COPY --link go/go.mod go/go.sum* ./go/
COPY --link go-services/go.mod go-services/go.sum ./go-services/

WORKDIR /src/go-services
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

WORKDIR /src
COPY --link go/ ./go/
COPY --link go-services/ ./go-services/

WORKDIR /src/go-services
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 \
    GOOS=${TARGETOS:-linux} \
    GOARCH=${TARGETARCH:-amd64} \
    go build \
      -trimpath \
      -buildvcs=false \
      -ldflags="-s -w \
        -X main.version=${VERSION} \
        -X main.commit=${COMMIT} \
        -X main.buildDate=${BUILD_DATE}" \
      -o /out/reader ./reader/cmd/reader

FROM gcr.io/distroless/static-debian12:nonroot

ARG VERSION
ARG COMMIT
ARG BUILD_DATE

LABEL org.opencontainers.image.title="fint-link-walker-reader" \
      org.opencontainers.image.description="Serves scan reports the link-walker scanner publishes" \
      org.opencontainers.image.source="https://github.com/FINTLabs/fint-link-walker" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${COMMIT}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.licenses="UNLICENSED"

COPY --from=builder --link /out/reader /reader

EXPOSE 8080
USER nonroot:nonroot
ENTRYPOINT ["/reader"]
