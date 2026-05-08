# fint-link-walker

Cross-resource link integrity overview for the FINT data platform. Walks every record a tenant exposes through the FINT API across all FINT domains (`utdanning`, `administrasjon`, `arkiv`, `personvern`, `okonomi`, `ressurs`, `felles`), follows the `_links` between resources, and surfaces broken references as Prometheus metrics and a JSON summary so teams can see — at a glance — how healthy their FINT data is.

## What it does

For each configured FINT component (e.g. `utdanning_elev`, `arkiv_noark`, `administrasjon_personal`), the scanner:

1. Asks FLAIS for the link-walker-managed FINT client for the tenant — creating one with the configured components if it doesn't already exist — then exchanges its credentials at the FINT IDP for a bearer token.
2. Streams every resource page to disk and indexes the canonical `self` href plus all outbound `_links` references.
3. Validates each reference against the in-memory index and classifies failures into:
   - `missing-resource` — referenced resource not found in the index.
   - `missing-back-link-adapter` / `missing-back-link-autorelation` — adapter or autorelation expected a back-reference that wasn't there.
   - `unknown-link` — reference points outside the indexed component set.
4. Persists the scan to Postgres: one row in `report_summary` (aggregate JSON) plus one row per broken-link finding in `report_row`. The reader serves these via HTTP and exposes per-tenant `link_walker_*` Prometheus metrics.

PII identifiers (`fodselsnummer`, `feidenavn` by default) are masked at report-emit time so reports are safe to share. Validation itself runs against the unmasked index, so accuracy is preserved.

## Architecture

Three Gradle modules:

| Module    | Role                                                                                                | Runtime                          |
|-----------|-----------------------------------------------------------------------------------------------------|----------------------------------|
| `core`    | Shared types, index/validator, JPA entities/repos and the `ReportStore` they back, HTTP client (`FintClient`). | Library                       |
| `scanner` | One-shot `ApplicationRunner` that builds the index, validates, persists the scan, then exits.       | Kubernetes `CronJob`             |
| `reader`  | Always-on Spring Boot service exposing `/actuator/prometheus`, `/report/{orgId}/summary` and `/report/{orgId}/rows`. | Kubernetes `Deployment`/`Service`|

The split lets the scanner run heavy, memory-hungry work on a schedule and tear down, while the reader stays cheap and serves Prometheus scrapes plus paginated JSON queries from the shared Postgres database.

## Storage

Reports live in Postgres. The schema is two tables, both written by the scanner and read by the reader:

```
report_summary(id, scan_id, org_id, scan_completed_at, summary_json TEXT)
  index: (org_id, scan_completed_at DESC)

report_row(id, scan_id, org_id, scan_completed_at,
           component, resource, problem_type,
           source_self, target_href, relation_name, expected_inverse_name)
  index: (org_id, scan_completed_at DESC)
  index: (org_id, scan_id, component, resource, problem_type)
```

`scan_id` ties summary and rows for one scan together. Each scan produces one `report_summary` row and N `report_row` rows. The reader's `/rows` endpoint resolves the latest scan per org, then runs a real `WHERE … LIMIT … OFFSET …` against the indexed columns — pagination and filtering happen in SQL, not in memory.

The summary aggregate (nested by component → resource → problem-type) is stored as JSON in `summary_json` because we never query its internals; queryable fields (org_id, completed-at) are proper columns.

### Schema management

We use `spring.jpa.hibernate.ddl-auto=update` for now. This gets us moving without committing to a migration tool while the schema is still settling. Once the design is stable in production we'll switch to Flyway and check in a `V1__init.sql`. Until then, schema changes happen via Hibernate evolving the columns; destructive changes (drops, type changes) require a manual `psql` step.

### Retention

Both tables grow forever. We deliberately do not prune.

The reasoning:
- **Summaries are tiny** (~1 KB each). 15 orgs × daily × years = a few hundred MB at most. Trend graphs need them indefinitely.
- **Rows are larger but not the bottleneck yet.** At our current scale, even a multi-year accumulation fits comfortably in the Aiven plan.
- **A blunt time-based prune solves the wrong problem.** The expensive thing isn't *time*, it's *duplication* — the same broken link gets re-inserted by every daily scan it persists through. A retention window doesn't fix duplication; it just amputates history along with the duplicates.

When row growth eventually becomes a real concern (watch DB size on the Aiven dashboard), the right fix is **not** to tighten retention but to switch the row schema to a de-duplicated `link_finding(first_seen_at, last_seen_at, resolved_at)` shape — one row per unique broken-link finding, UPSERTed each scan. That gives you years of meaningful history at a fraction of the storage. Until that lever is needed, keeping everything is the cheapest, simplest option.

## Modules

### `core`
- `FintClient` — `RestClient`-based HTTP client with retry, content-type sniffing (`200 + text/html` → `NoRouteException`), `503 + CacheNotFoundException` → `NoDataException`. Streams response bodies to temp files so the indexer can Jackson-stream them token-by-token without holding multi-MB pages in memory.
- `IndexBuilder` / `IndexValidator` / `RecordExtractor` — Jackson-streaming index construction and link validation.
- `HrefSanitizer` — PII masking at report-emit time.
- `ReportStore` — Postgres-backed via `report.jpa.JpaReportStore`, sharing the schema below across scanner (writer) and reader (queries).
- `report.jpa.*` — `ReportSummaryEntity`, `ReportRowEntity`, Spring Data repositories, `JpaReportConfig`.

### `scanner`
- `ScanRunner` — orchestrates auth → index → validate → publish → exit. `ReportStore.publish` is one transaction: the summary insert and the row-batch insert succeed or fail together.
- Configured via `application.yaml` and per-environment overrides (`--link-walker.org-id=…`).

### `reader`
- `ReportController` — `GET /report/{orgId}/summary` (nested aggregate) and `GET /report/{orgId}/rows` (paginated, filterable broken-link list, max page size 1000). Pagination and filtering are pushed down to SQL via `ReportStore.findRows`.
- `SummaryMetrics` — `@Scheduled` Micrometer `MultiGauge` publisher; refreshes every 60 s by querying the latest summary per org.

## Configuration

Key properties under `link-walker`:

| Property                        | Default                                | Notes                                                                  |
|---------------------------------|----------------------------------------|------------------------------------------------------------------------|
| `org-id`                        | _required_ (scanner only)              | E.g. `afk-no`. Tags every persisted scan and metric. Reader is multi-org. |
| `base-url`                      | `https://api.felleskomponent.no`       | FINT API root.                                                         |
| `components`                    | all FINT components                    | Defaults to the full set across `administrasjon`/`arkiv`/`felles`/`okonomi`/`personvern`/`ressurs`/`utdanning` (see `LinkWalkerConfig.ALL_FINT_COMPONENTS`). Override to narrow scope. |
| `auto-relation-components`      | empty                                  | Subset of `components` where autorelation back-links are required for the tenant. |
| `pii-identifiers`               | `fodselsnummer, feidenavn`             | Identifier types to mask in emitted reports.                           |
| `exclude-relations`             | `vigoreferanse, grepreferanse`         | Relations to ignore during `unknown-link` classification.              |
| `max-attempts`                  | `5`                                    | Retry attempts for 5xx / network errors. 4xx is terminal.              |
| `fetch-concurrency`             | `10`                                   | Parallel resource fetches per scan.                                    |
| `read-timeout`                  | `10m`                                  | Per-resource HTTP read timeout.                                        |

Datasource via Spring properties (env-var overridable):

| Property                  | Default (local)                                | Override in prod via                |
|---------------------------|------------------------------------------------|-------------------------------------|
| `spring.datasource.url`   | `jdbc:postgresql://localhost:5432/linkwalker`  | `SPRING_DATASOURCE_URL`             |
| `spring.datasource.username` | `linkwalker`                                | `SPRING_DATASOURCE_USERNAME`        |
| `spring.datasource.password` | `linkwalker`                                | `SPRING_DATASOURCE_PASSWORD`        |

## Running locally

### Postgres

```sh
docker compose up -d postgres
```

Brings up `postgres:16-alpine` on `:5432` with db/user/password all `linkwalker`. The schema is created automatically by Hibernate the first time scanner or reader boots. Stop with `docker compose down` (add `-v` to wipe the data volume).

### Scanner (one-shot)

```sh
./gradlew :scanner:bootRun --args='--spring.profiles.active=local --link-walker.org-id=afk-no'
```

The `local` profile (`application-local.yaml`) points the FLAIS gateway at `http://localhost:56417` — port-forward the cluster service there before running. In production the in-cluster default from `AuthProperties.kt` applies. The scanner writes one summary row + N broken-link rows to Postgres, then exits.

### Reader (always-on)

```sh
./gradlew :reader:bootRun --args='--spring.profiles.active=local'
```

The `local` profile pins the reader to `8081` to avoid clashing with anything else on `8080` during dev. Production uses `8080` (default).

- `http://localhost:8081/link-walker/report/{orgId}/summary` — nested `LatestReportSummary` (tenant aggregate + per-component + per-resource integrity). Drives the dashboard's overview and drill-down views.
- `http://localhost:8081/link-walker/report/{orgId}/rows?component=…&resource=…&problemType=…&page=0&size=100` — paginated `ReportRow`s for the broken-link list. All filters AND-combined and pushed down to SQL; max page size 1000.
- `http://localhost:8081/link-walker/actuator/prometheus` — `link_walker_*` metrics.

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

The test strategy mixes pure unit tests with three kinds of integration tests, picked per the bug class each layer is most likely to produce:

| Tool                         | Where it's used                                | Why                                                                                                                                |
|------------------------------|------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| **MockK**                    | Index/validator/extractor/controller logic.    | Pure logic; HTTP and storage are mocked behind interfaces.                                                                         |
| **MockWebServer** (OkHttp)   | `FintClient`.                                  | Real local HTTP socket; exercises the actual JDK `HttpClient` transport. The bugs we've hit here (200+HTML routed via `NoRouteException`, 503+`CacheNotFoundException` body matching, retry-after-`PrematureClose`) are wire-level — content type, status+body interaction, body streaming. `MockRestServiceServer` intercepts above the transport, so it can't reproduce them. |
| **Live Postgres**            | `JpaReportStoreIntegrationTest`, `ReaderContextSmokeTest`. | Boots the reader Spring context against the running `docker compose` Postgres and exercises publish → query through the actual `@Transactional` proxy + Hibernate batched inserts. Catches schema/mapping/SQL drift the way prod will see it. |

`JpaReportStoreIntegrationTest` and the smoke test both require `docker compose up -d postgres`. The integration test cleans both tables in `@BeforeEach`, so it's safe to re-run.

## Deployment

- **Scanner** runs as a Kubernetes `CronJob` per tenant. The pod exits zero on success; the JVM uses `-XX:+ExitOnOutOfMemoryError` (set in `Dockerfile`) so OOMs fail the job rather than hang. The scan persists to Postgres in one transaction — partial scans are not visible to the reader.
- **Reader** runs as a `Deployment` with a `Service` exposing `:8080`. Prometheus scrapes `/link-walker/actuator/prometheus`; report queries hit Postgres.

The Postgres connection is an Aiven managed instance in production, configured per-environment via `SPRING_DATASOURCE_*` env vars (typically sourced from a Kubernetes secret).
