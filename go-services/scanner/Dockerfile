# syntax=docker/dockerfile:1.7
#
# Build context = repo root.
#   docker build -f go-services/scanner/Dockerfile -t scanner .
#   docker buildx build --platform=linux/amd64,linux/arm64 \
#     -f go-services/scanner/Dockerfile -t scanner .
#
# go-services/go.mod has `replace github.com/FINTLabs/fint-model-go => ../go`,
# so the build needs both go/ and go-services/ directories.

# ─── Stage 1: builder ───────────────────────────────────────────────
# --platform=$BUILDPLATFORM pins the builder to the host's native arch,
# even when targeting another arch. Cross-compiling Go is fast; emulating
# the builder via QEMU is not.
FROM --platform=$BUILDPLATFORM golang:1.26-alpine AS builder

# Build-arg metadata used both for the binary and for image labels.
# Pass --build-arg VERSION=v1.2.3 in CI to embed a real version string.
ARG VERSION=dev
ARG COMMIT=unknown
ARG BUILD_DATE=unknown

# Cross-compile target. Docker BuildKit injects these for buildx.
ARG TARGETOS
ARG TARGETARCH

WORKDIR /src

# Module manifests first — this layer is cached as long as deps are
# unchanged, regardless of source edits.
COPY --link go/go.mod go/go.sum* ./go/
COPY --link go-services/go.mod go-services/go.sum ./go-services/

WORKDIR /src/go-services
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

# Source. Frequent changes here re-run only the build step.
WORKDIR /src
COPY --link go/ ./go/
COPY --link go-services/ ./go-services/

# Static, stripped, trimmed binary.
#   -trimpath        : remove host paths from binary
#   -buildvcs=false  : no VCS stamping (no .git in context, reproducible)
#   -ldflags '-s -w' : strip symbol + DWARF tables (~30% smaller)
#   -X main.version  : embed semver/build metadata, queryable at runtime
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
      -o /out/scanner ./scanner/cmd/scanner

# ─── Stage 2: runtime ───────────────────────────────────────────────
# distroless/static is fine for Go (CGO_ENABLED=0, statically linked).
# It includes CA certs, tzdata, /etc/passwd, no shell. ~2 MB.
FROM gcr.io/distroless/static-debian12:nonroot

ARG VERSION
ARG COMMIT
ARG BUILD_DATE

# OCI image annotations — picked up by registries, k8s tooling, security
# scanners. Standard set per the OCI image-spec.
LABEL org.opencontainers.image.title="fint-link-walker-scanner" \
      org.opencontainers.image.description="Walks every link for one FINT org and publishes a scan report" \
      org.opencontainers.image.source="https://github.com/FINTLabs/fint-link-walker" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${COMMIT}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.licenses="UNLICENSED"

COPY --from=builder --link /out/scanner /scanner

# Soft heap ceiling — keeps peak memory close to 7 GiB instead of letting
# the runtime grow to ~2× live data. Override per deployment if pods have
# different memory limits (e.g. -e GOMEMLIMIT=14GiB on a 16 GiB pod).
ENV GOMEMLIMIT=7GiB

USER nonroot:nonroot
ENTRYPOINT ["/scanner"]
