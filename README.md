# fint-link-walker

Cross-resource link integrity overview for the FINT data platform. Walks every record a tenant exposes through the FINT API across all FINT domains (`utdanning`, `administrasjon`, `arkiv`, `personvern`, `okonomi`, `ressurs`, `felles`), follows the `_links` between resources, and surfaces broken references as Prometheus metrics and a JSON summary so teams can see — at a glance — how healthy their FINT data is.

## What it does

For each configured FINT component (e.g. `utdanning_elev`, `arkiv_noark`, `administrasjon_personal`), the scanner:

1. Asks FLAIS for the link-walker-managed FINT client for the tenant — creating one with the configured components if it doesn't already exist — then exchanges its credentials at the FINT IDP for a bearer token.
2. Streams every resource page to disk and indexes the canonical `self` href plus all outbound `_links` references.
3. Sorts every link by where it points:
   - `within_domain`: the target is in the same domain as the source (utdanning to utdanning). The domain's own adapter is responsible.
   - `cross_domain`: the target is in another domain (utdanning to felles). Two source systems have to agree on ids.
   - `not_covered`: the target resource was not fetched (not in `components`, no route, no data, 403 or 404). These links are counted but never checked, and they are left out of integrity.
4. Validates each checked reference against the in-memory index and classifies failures into:
   - `missing-resource`: the target resource was fetched, but the referenced record is not in it.
   - `missing-back-link-adapter` / `missing-back-link-autorelation`: adapter or autorelation expected a back-reference that wasn't there.
   - `unknown-link`: the href does not have the `{domain}/{package}/{resource}/{field}/{value}` shape.
5. Persists the scan to Postgres: one row in `report_summary` (aggregate JSON) plus one row per broken-link finding in `report_row`. The reader serves these via HTTP and exposes per-tenant `link_walker_*` Prometheus metrics.

PII identifiers (`fodselsnummer`, `feidenavn` by default) are masked at report-emit time so reports are safe to share. Validation itself runs against the unmasked index, so accuracy is preserved.

## Architecture

Three Gradle modules:

| Module    | Role                                                                                                | Runtime                          |
|-----------|-----------------------------------------------------------------------------------------------------|----------------------------------|
| `core`    | Shared types, index/validator, JPA entities/repos and the `ReportStore` they back, HTTP client (`FintClient`). | Library                       |
| `scanner` | One-shot `ApplicationRunner` that builds the index, validates, persists the scan, then exits.       | Kubernetes `CronJob`             |
| `reader`  | Always-on Spring Boot service exposing `/actuator/prometheus`, `/report/{orgId}/summary` and `/report/{orgId}/problems`. | Kubernetes `Deployment`/`Service`|

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

`scan_id` ties summary and rows for one scan together. Each scan produces one `report_summary` row and N `report_row` rows. The reader's `/problems` endpoint resolves the latest scan per org, then runs a real `WHERE … LIMIT … OFFSET …` against the indexed columns — pagination and filtering happen in SQL, not in memory.

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
- `ReportController` — `GET /report/{orgId}/summary` (nested aggregate) and `GET /report/{orgId}/problems` (paginated, filterable broken-link list, max page size 1000). Pagination and filtering are pushed down to SQL via `ReportStore.findRows`.
- `SummaryMetrics` — `@Scheduled` Micrometer `MultiGauge` publisher; refreshes every 60 s by querying the latest summary per org.

## Configuration

Key properties under `link-walker`:

| Property                        | Default                                | Notes                                                                  |
|---------------------------------|----------------------------------------|------------------------------------------------------------------------|
| `org-id`                        | _required_ (scanner only)              | E.g. `afk-no`. Tags every persisted scan and metric. Reader is multi-org. |
| `base-url`                      | `https://api.felleskomponent.no`       | FINT API root.                                                         |
| `fetch-base-url`                | unset                                  | When set, every page is fetched from this base instead of `base-url`, with the public host in the `Host` header. Used in the cluster to reach Traefik directly and skip the Access Gateway, which damages large bodies. A page that answers 401, 403 or 404 there, or cannot be connected to, is fetched from `base-url` instead. |
| `canary-path`                   | `utdanning/elev/elevforhold?size=2000` | One page fetched through `base-url` per scan when `fetch-base-url` is set, checked for damage and stored in the summary as `gatewayCanary`. |
| `service-routing.enabled`       | `false`                                | Fetch pages straight from the org's Kubernetes Services, tried before `fetch-base-url` and `base-url`. |
| `service-routing.client-api-domains` | `utdanning`                       | Domains served by `service-routing.client-api-service` (`fint-core-client-api`). Every other domain is fetched from `fint-core-consumer-{domain}-{package}`. |
| `service-routing.host-pattern`  | `{service}.{namespace}.svc.cluster.local:8080` | How the Service host is built. The namespace is `org-id` with underscores turned into dashes unless `service-routing.namespace` is set. |
| `page-size`                     | `10000`                                | Entries requested per page. The scanner follows each page's `_links.next` until a page has none. |
| `page-sizes`                    | `skole: 3`                             | Per-resource page size, keyed by resource name (case-insensitive). Use for resources whose entries carry many links. |
| `components`                    | all FINT components                    | Defaults to the full set across `administrasjon`/`arkiv`/`felles`/`okonomi`/`personvern`/`ressurs`/`utdanning` (see `LinkWalkerConfig.ALL_FINT_COMPONENTS`). Override to narrow scope. |
| `extra-resources`               | `felles_kodeverk: iso/kjonn, iso/landkode, iso/sprak` | Resource paths per component that fint-core-metamodel does not list. The resource is named by the last path segment. |
| `auto-relation-components`      | empty                                  | Subset of `components` where autorelation back-links are required for the tenant. |
| `pii-identifiers`               | `fodselsnummer, feidenavn`             | Identifier types to mask in emitted reports.                           |
| `exclude-relations`             | `vigoreferanse, grepreferanse`         | Relations to ignore during `unknown-link` classification.              |
| `max-attempts`                  | `5`                                    | Retry attempts for 5xx / network errors, and re-fetches of a page whose body fails to parse. 4xx is terminal. |
| `max-concurrent-fetches`        | `1`                                    | Resources fetched in parallel. `1` walks one resource at a time.       |
| `read-timeout`                  | `10m`                                  | Per-resource HTTP read timeout.                                        |

Datasource via Spring properties (env-var overridable):

| Property                  | Default (local)                                | Override in prod via                |
|---------------------------|------------------------------------------------|-------------------------------------|
| `spring.datasource.url`   | `jdbc:postgresql://localhost:5432/linkwalker`  | `FINT_DATABASE_URL`                 |
| `spring.datasource.username` | `linkwalker`                                | `FINT_DATABASE_USERNAME`            |
| `spring.datasource.password` | `linkwalker`                                | `FINT_DATABASE_PASSWORD`            |

## Fetching around the Access Gateway

The public hostnames (`beta.felleskomponent.no`, `api.felleskomponent.no`) are fronted by a NetIQ
Access Gateway. It rewrites URLs inside response bodies and, on large pages, sometimes damages them:
a link comes back as `https:https://beta.felleskomponent.noonent.no/...`, a run of NUL bytes
appears, or the body is cut short. The scanner runs in the same cluster as the FINT services, so
it does not need the public host at all. For every page it tries these routes in order and moves
on when a route answers 401, 403 or 404 or cannot be connected to:

1. The org's own Kubernetes Service (`service-routing`): `fint-core-client-api` in the org's
   namespace for the domains it serves, the legacy `fint-core-consumer-<domain>-<package>` for the
   rest. No gateway, no Traefik, plain HTTP inside the cluster.
2. Traefik (`fetch-base-url`) with the public host in the `Host` header, which is how the gateway
   itself reaches the services.
3. The public URL (`base-url`), through the gateway.

A host that cannot be connected to is skipped for the rest of the scan. Each page log line says
which route served it (`route=service|traefik|gateway`). The links inside the bodies still carry
the public host, so validation is unchanged.

Every downloaded page is checked before it is parsed. A page with a NUL byte, a doubled scheme,
`httphttp`, a `${` placeholder in a link, or a link that is only the public host is fetched again,
up to `max-attempts`, and then fails the scan for that resource with `PageCorruptException`. An
entry whose self href is not a FINT resource href is not indexed and is counted in the page log
line.

Because the scan no longer passes the gateway, one page per scan is fetched through the public host
(`canary-path`) and checked the same way. The result is logged and stored in the scan summary as
`gatewayCanary`, with the gateway node id from the `Via` header, so a damaged gateway still shows
up in the report.

`Host` is a restricted header in the JDK HTTP client, needed only for the Traefik route. The
scanner sets `jdk.httpclient.allowRestrictedHeaders=host` in `main` and in the image's
`JAVA_TOOL_OPTIONS`. Without it the header is dropped silently and Traefik answers 404.

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
- `http://localhost:8081/link-walker/report/{orgId}/problems?component=…&resource=…&problemType=…&page=0&size=100` — paginated `ReportRow`s for the broken-link list. All filters AND-combined and pushed down to SQL; max page size 1000.
- `http://localhost:8081/link-walker/actuator/prometheus` — `link_walker_*` metrics. `link_walker_links_count` and `link_walker_link_errors` carry `scope` and `target_component` labels, and drive the Domain Integrity dashboard.

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
| **Live Postgres**            | `JpaReportStoreIntegrationTest`, `ReaderContextSmokeTest`. | Boots the Spring context against a Testcontainers `postgres:16-alpine` (Docker must be running; no compose step needed) and exercises publish → query through the actual `@Transactional` proxy + Hibernate batched inserts. Catches schema/mapping/SQL drift the way prod will see it. |

`JpaReportStoreIntegrationTest` and the smoke test both require `docker compose up -d postgres`. The integration test cleans both tables in `@BeforeEach`, so it's safe to re-run.

## Deployment

Manifests live in `kustomize/` and are applied by the CD workflow. Nothing is applied by hand.

```
kustomize/
  base/reader/               Application for the reader (one per environment, org fintlabs.no)
  base/scanner/              CronJob for one scanner, with ORG placeholders
  components/org/            kustomize replacements that fill the placeholders from org-values.yaml
  components/onepassword/    beta: OnePasswordItem and envFrom for the scanner's FINT client
  components/org-namespace/  api: the reader's DB secret replicated in, service routing to the pod's namespace
  overlays/beta/
    reader/                  the beta reader
    <org-id>/                one folder per scanner, all in namespace fint-core
  overlays/api/
    reader/                  the api reader
    <org-id>/                one folder per county, each in its own namespace
```

- **Scanner** runs as a `CronJob` per org. The pod exits zero on success; the JVM uses `-XX:+ExitOnOutOfMemoryError` (set in `Dockerfile`) so OOMs fail the job rather than hang. The scan persists to Postgres in one transaction, so partial scans are not visible to the reader.
  - On beta the scanners run in `fint-core`. Credentials come from a `Secret` that the 1Password operator creates from the `OnePasswordItem` in the same overlay. The vault item must exist before the first run.
  - On api each scanner runs in its county's namespace; Oslo runs as `ude-oslo-kommune-no` in `oslo-kommune-no`. There is no client secret, so the scanner gets its FINT client from `fint-customer-objects-gateway` in `flais-io`, which creates it on the first run. Service routing looks for the org's Services in the pod's own namespace.
  - On api the scanner writes with the reader's DB credentials. pgerator gives every `PGUser` a schema of its own, so a scanner with its own user would write where the reader never reads. kubernetes-replicator copies `fint-core/fint-link-walker-reader-db` into each county namespace under the same name; the CD job that deploys the reader allows the replication on the source secret.
- **Reader** runs as a FLAIS `Application` in namespace `fint-core` on both. Prometheus scrapes `/link-walker/actuator/prometheus`; report queries hit Postgres.

### Adding an org

On beta, copy any folder under `kustomize/overlays/beta/`, rename it to the dashed org id, and edit `org-values.yaml`. Create the 1Password item first.

```yaml
data:
  id-dashed: rogfk-no
  id-underscore: rogfk_no
  base-url: https://beta.felleskomponent.no
  fetch-base-url: http://flais-ingress-traefik.flais-system.svc.cluster.local
  onepassword-itempath: vaults/aks-beta-vault/items/link-walker-rogfk-client
```

On api, copy any folder under `kustomize/overlays/api/`, set `namespace:` in `kustomization.yaml` to the org's namespace, and edit `org-values.yaml`:

```yaml
data:
  id-dashed: rogfk-no
  id-underscore: rogfk_no
  base-url: https://api.felleskomponent.no
  fetch-base-url: http://traefik.traefik-v2.svc.cluster.local
```

Check the render with `kubectl kustomize kustomize/overlays/<env>/<org-id>`, then merge. CD skips folders whose name ends in `.disabled`. Removing a folder does not delete the CronJob from the cluster.

### CI

`.github/workflows/CI.yaml` runs on pull requests and on pushes to `main` and `develop`. It diffs against the pull request's base, or the previous push, and runs `./gradlew :scanner:check` and `./gradlew :reader:check` only for the modules whose paths changed. `core` and the root Gradle files count for both; the lists are in `.github/scripts/paths.sh`. `ci-ok` fails if either check failed.

### CD

`.github/workflows/CD.yaml` runs on every push to `develop` and `main`. `develop` takes direct pushes; `main` takes pull requests.

| Branch    | Environment | Cluster                    | Deploys                                       |
|-----------|-------------|----------------------------|-----------------------------------------------|
| `develop` | beta        | `aks-beta-fint-2021-11-23` | every folder under `kustomize/overlays/beta/` |
| `main`    | api         | `aks-api-fint-2022-02-08`  | every folder under `kustomize/overlays/api/`  |

Images are tagged `src-<hash>`, a hash of the git trees the image is built from (`.github/scripts/image-tags.sh`). When that tag is already in ghcr the build is skipped; otherwise the job runs `./gradlew :<module>:check`, then builds and pushes. A commit that only touches kustomize, docs or the other module redeploys with the image that is already there, and the squash commit on `main` gets the same tags as the `develop` commit it came from, so api runs the image beta ran. The commit an image was built from is in its `org.opencontainers.image.revision` label.

Each deploy job bakes its overlay with kustomize, swaps the `REPLACE` image tag for the `src-` tags, logs in with the org secret `AKS_<ENV>_FINT_GITHUB` and applies into the namespace the overlay renders into. Rolling back is re-running the workflow from an earlier commit.

Promoting to `main`: open a pull request from `develop` to `main` and squash-merge it. Merge commits are disabled on the repo, so afterwards bring the squash commit back into `develop` to keep the branches aligned:

```
git checkout develop && git pull && git merge origin/main -m "Merge main back into develop [skip ci]" && git push
```
