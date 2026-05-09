# fint-link-walker

Cross-resource link-integrity overview for the FINT data platform. Walks every record a tenant exposes through the FINT API across all FINT domains (`utdanning`, `administrasjon`, `arkiv`, `personvern`, `okonomi`, `ressurs`, `felles`), follows the `_links` between resources, and surfaces broken references so teams can see — at a glance — how healthy their FINT data is.

## What it does

For each configured FINT component (e.g. `utdanning-elev`, `arkiv-noark`, `administrasjon-personal`), the **scanner**:

1. Asks the FLAIS gateway for the link-walker-managed FINT client for the tenant — creating one with the configured components if it doesn't already exist — then exchanges its credentials at the FINT IdP for a bearer token.
2. Discovers each type's record count via `/cache/size`, then fetches every page concurrently (offset-paginated, `?size=N&offset=M`).
3. Builds an in-memory index keyed by canonical `self` href, recording every outbound `_links` reference per record.
4. Validates each reference against the index and classifies failures:
   - `missing-resource` — referenced resource not found in the index.
   - `missing-back-link-adapter` — the relation has an `inverseName` in the metamodel, but the target record doesn't link back.
5. Writes one `report_summary` row + N `report_row` rows to Postgres in a single transaction (Postgres `COPY` for the row batch).

The **reader** is a tiny HTTP service that serves what the scanner publishes:

- `GET /report/{orgId}/summary` — aggregate counts and integrity for the latest scan.
- `GET /report/{orgId}/rows?component=&resource=&problemType=&page=&size=` — paginated, SQL-filtered broken-link rows.
- `GET /reports` — list every org's latest summary.

## Layout

```
fint-link-walker/
├── go/                       Library: typed FINT resources + runtime
│   ├── runtime/              Link, Identifikator, Resource interface, dispatch
│   ├── manifest/             Generated component → type catalog (no runtime JSON)
│   └── <component dirs>/     Generated typed resources (utdanning/elev/elev.go, …)
│
├── go-services/              Services that consume go/
│   ├── pkg/
│   │   ├── auth/             FLAIS gateway client + IdP token exchange
│   │   ├── report/           Shared report data model
│   │   └── store/            Store interface + Postgres impl (pgx)
│   ├── scanner/              One-shot scan binary
│   │   ├── cmd/scanner/      main()
│   │   └── internal/         config, fintapi, index, validate, summary, scan
│   └── reader/               HTTP service
│       ├── cmd/reader/       main()
│       └── internal/         handlers
│
├── deployments/              Kubernetes manifests (CronJob + Deployment)
├── docker-compose.yaml       Local Postgres on :5432
└── local-monitoring/         Optional Grafana/Prometheus stack
```

The split into two Go modules is deliberate. `go/` is a self-contained library — typed resource structs + runtime + manifest, no service-specific code. Any consumer (scanner, reader, future readers, codegen tools) imports it independently. `go-services/` references it via a `replace` directive in its `go.mod`.

## Storage

Postgres, two tables. Schema mirrors the JPA entities in the previous Kotlin implementation byte-for-byte, so a Go scanner could publish reports a Kotlin reader serves and vice versa.

```
report_summary(id UUID, scan_id UUID, org_id, scan_completed_at TIMESTAMPTZ, summary_json TEXT)
  index: (org_id, scan_completed_at DESC)

report_row(id BIGSERIAL, scan_id UUID, org_id, scan_completed_at,
           component, resource, problem_type,
           source_self, target_href, relation_name, expected_inverse_name)
  index: (org_id, scan_completed_at DESC)
  index: (org_id, scan_id, component, resource, problem_type)
```

Schema is applied idempotently on every connect (`CREATE … IF NOT EXISTS`). No external migration tool yet.

## Running locally

### 1. Postgres

```sh
docker compose up -d postgres
```

Brings up `postgres:16-alpine` on `:5432` with db / user / password all `linkwalker`. The Go services run the (idempotent) schema on connect, so no init script needed.

### 2. FLAIS port-forward (for auth)

```sh
kubectl port-forward -n flais-io svc/fint-customer-objects-gateway 64130:8080
```

Lens or any other k8s tool works equivalently. The scanner needs FLAIS to look up / create / decrypt the tenant's FINT client.

### 3. Scanner

```sh
cd go-services
cp .env.local.example .env.local         # tweak ORG_ID / COMPONENTS as needed
set -a; source .env.local; set +a
go run ./scanner/cmd/scanner
```

Watch stderr — JSON logs report per-component progress, then total timing. Roughly 30-40s for a full 16-component scan against a typical tenant.

### 4. Reader

```sh
cd go-services
go run ./reader/cmd/reader

# in another terminal:
curl http://localhost:8080/report/{orgId}/summary | jq
curl 'http://localhost:8080/report/{orgId}/rows?problemType=missing-resource&size=10' | jq
```

### 5. Monitoring (optional)

```sh
docker compose -f local-monitoring/docker-compose.yaml up -d
# Prometheus: http://localhost:9090
# Grafana:    http://localhost:3000
```

## Configuration

All env-driven. Defaults work for in-cluster deployment; local dev needs the FLAIS override.

| Var | Default | Notes |
|---|---|---|
| `ORG_ID` | _required_ | Tenant org id, e.g. `afk-no`. Tags every persisted scan. |
| `COMPONENTS` | _required_ | Comma-separated, kebab-case (e.g. `utdanning-elev,utdanning-vurdering`). |
| `BASE_URL` | `https://api.felleskomponent.no` | FINT consumer API root. |
| `FLAIS_GATEWAY_URL` | k8s in-cluster service | Override for local port-forward, e.g. `http://localhost:64130`. |
| `IDP_URL` | prod IdP | OAuth password-grant token endpoint. |
| `DATABASE_URL` | local Postgres on `:5432` | `pgx`-compatible DSN. |
| `PAGE_SIZE` | `10000` | Records per FINT API page. Larger = fewer requests, bigger payloads. |
| `HTTP_TIMEOUT` | `60s` | Per-request timeout on FLAIS / IdP / FINT calls. |
| `GOMEMLIMIT` | unset | Set to e.g. `7GiB` to cap heap growth — the Go equivalent of `-Xmx`. Already baked into the scanner's Docker image. |
| `LOG_LEVEL` | `info` | `debug` surfaces per-page fetch timings. |
| `HEAP_PROFILE` | unset | Path prefix; if set, scanner writes per-phase heap profiles for `go tool pprof`. |
| `READER_ADDR` | `:8080` | Reader bind address. |

## Building Docker images

```sh
docker build -f go-services/scanner/Dockerfile -t scanner .
docker build -f go-services/reader/Dockerfile  -t reader  .
```

Build context is the repo root, not `go-services/` — the Dockerfiles need both `go/` and `go-services/` because of the local-replace directive. Multi-stage build → `gcr.io/distroless/static-debian12:nonroot`. Final image is ~20 MB.

Cold build: ~7s on Apple Silicon. Warm rebuild after a code change: ~1s. CI with cache: ~10s per service.

## Why this is in Go

The previous incarnation of this project ran on Spring Boot. The rewrite is in Go for three concrete wins:

1. **No JVM reflection cliff.** The previous scanner used `org.reflections` to walk the classpath and instantiate FINT model classes. That worked fine until anything wanted to leave the JVM (Quarkus native, sidecars, non-JVM consumers). The Go version reads the model from `metamodel.json` (or the typed Go structs generated from it), so reflection is never on the path.
2. **One binary, distroless deploy.** Container images dropped from ~250 MB (Spring Boot) to ~20 MB. Pod cold-start dropped from ~5-15s to ~150-300ms.
3. **Memory parity at half the Sys ceiling.** The Kotlin scanner used ~7 GB peak heap. The Go scanner uses ~5.5 GB live with `GOMEMLIMIT=7GiB` capping peak Sys to ~9 GB — and runs ~5× faster (38s vs ~3 min for a full 16-component scan).

The metamodel itself (component → type → relation graph) is a language-neutral JSON artifact emitted by the upstream codegen tool (lives in its own repo). The `go/` directory in this repo is the *generated* Go consumer of that JSON — typed structs, runtime metadata, the manifest catalog. Future TypeScript or Python consumers of the same metamodel would be generated equivalent modules. This repo doesn't carry the JSON itself; the `go/` package's headers cite the metamodel.json revision they were generated from.
