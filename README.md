# fint-link-walker

Cross-resource link integrity overview for the FINT data platform. Walks every record a tenant exposes through the FINT API across all FINT domains (`utdanning`, `administrasjon`, `arkiv`, `personvern`, `okonomi`, `ressurs`, `felles`), follows the `_links` between resources, and surfaces broken references as Prometheus metrics and a JSON summary so teams can see — at a glance — how healthy their FINT data is.

## What it does

For each configured FINT component (e.g. `utdanning_elev`, `arkiv_noark`, `administrasjon_personal`), the scanner:

1. Asks FLAIS for the link-walker-managed FINT client for the tenant — creating one with the configured components if it doesn't already exist — then exchanges its credentials at the FINT IDP for a bearer token.
2. Streams every resource page to disk and indexes the canonical `self` href plus all outbound `_links` references.
3. Validates each reference against the in-memory index and classifies failures into:
   - `missing-resource` — referenced resource not found in the index.
   - `missing-back-link` — provider-side autorelation expected a back-reference that wasn't there.
   - `unknown-link` — reference points outside the indexed component set.
4. Publishes a `LatestReport` (gzipped JSON) to file or Azure Blob storage, plus per-tenant `link_walker_*` Prometheus metrics surfaced by the reader.

PII identifiers (`fodselsnummer`, `feidenavn` by default) are masked at report-emit time so reports are safe to share. Validation itself runs against the unmasked index, so accuracy is preserved.

## Architecture

Three Gradle modules:

| Module    | Role                                                                                                | Runtime                          |
|-----------|-----------------------------------------------------------------------------------------------------|----------------------------------|
| `core`    | Shared types, index/validator, report stores (file + blob), HTTP client (`FintClient`).             | Library                          |
| `scanner` | One-shot `ApplicationRunner` that builds the index, validates, publishes the report, then exits.    | Kubernetes `CronJob`             |
| `reader`  | Always-on Spring Boot service exposing `/actuator/prometheus` and `/report/latest`.                 | Kubernetes `Deployment`/`Service`|

The split lets the scanner run heavy, memory-hungry work on a schedule and tear down, while the reader stays cheap and serves Prometheus scrapes from the latest stored report.

## Modules

### `core`
- `FintClient` — `RestClient`-based HTTP client with retry, content-type sniffing (`200 + text/html` → `NoRouteException`), `503 + CacheNotFoundException` → `NoDataException`, and streaming body-to-disk.
- `IndexBuilder` / `IndexValidator` / `RecordExtractor` — Jackson-streaming index construction and link validation.
- `HrefSanitizer` — PII masking at report-emit time.
- `BlobReportStore` / `FileReportStore` — gzipped `LatestReport` persistence.

### `scanner`
- `ScanRunner` — orchestrates auth → index → validate → publish → exit.
- Configured via `application.yaml` and per-environment overrides (`--link-walker.tenant=…`).

### `reader`
- `ReportController` — `GET /report/latest`, `GET /report/summary`.
- `SummaryMetrics` — `@Scheduled` Micrometer `MultiGauge` publisher; refreshes every 60s from the latest stored report.

## Configuration

Key properties under `link-walker`:

| Property                       | Default                                | Notes                                                                  |
|--------------------------------|----------------------------------------|------------------------------------------------------------------------|
| `tenant`                       | _required_                             | E.g. `afk-no`. Used as blob name and in metric tags.                   |
| `base-url`                     | `https://api.felleskomponent.no`       | FINT API root.                                                         |
| `components`                   | all FINT components                    | Defaults to the full set across `administrasjon`/`arkiv`/`felles`/`okonomi`/`personvern`/`ressurs`/`utdanning` (see `LinkWalkerConfig.ALL_FINT_COMPONENTS`). Override to narrow scope. |
| `auto-relation-components`     | empty                                  | Subset of `components` where autorelation back-links are required for the tenant. |
| `storage.type`                 | `file`                                 | `file` for local, `blob` for Azure.                                    |
| `storage.file.directory`       | `${java.io.tmpdir}/link-walker-reports`| File-store directory.                                                  |
| `storage.blob.endpoint`        | _none_                                 | E.g. `https://fintlink.blob.core.windows.net`. Uses `DefaultAzureCredential`. |
| `storage.blob.connection-string`| _none_                                | Optional; takes precedence over `endpoint` when set (local Azurite).   |
| `pii-identifiers`              | `fodselsnummer, feidenavn`             | Identifier types to mask in emitted reports.                           |
| `exclude-relations`            | `vigoreferanse, grepreferanse`         | Relations to ignore during `unknown-link` classification.              |
| `max-attempts`                 | `5`                                    | Retry attempts for 5xx / network errors. 4xx is terminal.              |
| `fetch-concurrency`            | `10`                                   | Parallel resource fetches per scan.                                    |
| `read-timeout`                 | `10m`                                  | Per-resource HTTP read timeout.                                        |

## Running locally

### Scanner (one-shot)

```sh
./gradlew :scanner:bootRun --args='--spring.profiles.active=local --link-walker.tenant=afk-no'
```

The `local` profile (`application-local.yaml`) points the FLAIS gateway at `http://localhost:56417` — port-forward the cluster service there before running. In production the in-cluster default from `AuthProperties.kt` applies.

Reports are written to `/tmp/link-walker-reports/<tenant>.json.gz`.

### Reader (always-on)

```sh
./gradlew :reader:bootRun
```

- `http://localhost:8080/report/latest` — full report JSON.
- `http://localhost:8080/actuator/prometheus` — `link_walker_*` metrics.

### Monitoring stack

```sh
docker compose -f local-monitoring/docker-compose.yaml up -d
```

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (auto-provisioned dashboards with `tenant` / `component` / `problem_type` filters).

## Testing

```sh
./gradlew test
```

The test strategy mixes pure unit tests with two kinds of integration tests, picked per the bug class each layer is most likely to produce:

| Tool                         | Where it's used                               | Why                                                                                                                                |
|------------------------------|-----------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| **MockK**                    | Index/validator/extractor/report logic.       | Pure logic; HTTP and storage are mocked behind interfaces.                                                                         |
| **MockWebServer** (OkHttp)   | `FintClient`.                                 | Real local HTTP socket; exercises the actual JDK `HttpClient` transport. The bugs we've hit here (200+HTML routed via `NoRouteException`, 503+`CacheNotFoundException` body matching, retry-after-`PrematureClose`) are wire-level — content type, status+body interaction, body streaming. `MockRestServiceServer` intercepts above the transport, so it can't reproduce them. |
| **Testcontainers + Azurite** | `BlobReportStore` integration.                | Catches signing / endpoint-routing bugs the Azure SDK can introduce when the connection string changes shape.                      |
| **JUnit `@TempDir`**         | `FileReportStore`.                            | Real filesystem behavior (parent-dir creation, atomic overwrite) without leaking between tests.                                    |

Note for Docker Engine ≥ 29: a system property `api.version=1.45` is set on the test task in `core/build.gradle.kts` because docker-java's default request hits a deprecated API version that newer engines reject with HTTP 400.

## Deployment

- **Scanner** runs as a Kubernetes `CronJob` per tenant. The pod exits zero on success and the JSON report lands in Azure Blob storage; the JVM uses `-XX:+ExitOnOutOfMemoryError` (set in `Dockerfile`) so OOMs fail the job rather than hang.
- **Reader** runs as a `Deployment` with a `Service` exposing `:8080`. Prometheus scrapes `/actuator/prometheus`; the latest report is fetched from the same blob the scanner writes.

Storage is configured per-environment via Spring profile / env vars; local dev defaults to file storage so no Azure credentials are needed.
