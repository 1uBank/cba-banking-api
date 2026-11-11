# Banking API

The only service client applications (the 1UBank App, iRecharge, ...) call, and the only service that calls
CBA-CORE (Fineract). Its contract is [`api/banking-api.yaml`](api/banking-api.yaml) (OpenAPI 3.1); the readable
reference is [`docs/index.html`](docs/index.html).


Java 21, Spring Boot 3.5, Maven, PostgreSQL with Liquibase, following the CBA Engineering Standards.

## Layout

A modular monolith: one deployable, one top-level package per business module under
`com.oneutility.cba.bankingapi`.

| Package | Holds |
|---|---|
| `auth` | `POST /v1/auth/token`: forwards client token requests to the auth service |
| `onus` | On-us transactions, where the money stays inside 1UBank: intra-bank transfers (including sweeps out of virtual accounts), service debits and their payment types, holds, and the status of any transfer by its reference. Each is one posting, final in the response |
| `interbank` | Transfers to other banks (NIP): the bank list, name enquiry, interbank transfers and their status as it changes |
| `accounts` | Accounts, balances, opening accounts, and the products they are opened on |
| `customers`, `virtualaccounts`, `invoices`, `transactions` | The other API areas in the contract |
| `events` | The event table (outbox) and publishing to SNS |
| `corebanking` | The client for CBA-CORE; other modules call it through its services |
| `common` | What every module shares: `exception` (error codes, `ApiException`, the error format), `security`, `web` (correlation IDs). Depends on no module |

Inside a module:

| Package | Holds |
|---|---|
| `controller` | REST controllers: read the request, call one service method, return the response |
| `service` | The logic: rules, `@Transactional`, and clients for other systems (for example `AuthServiceClient`) |
| `domain` | JPA entities and their repositories |
| `dto` | Request and response records, and other objects passed between layers |

A request goes `controller -> service -> domain`. Controllers never use repositories; services never use
controllers. A module uses another module only through that module's `service` and `dto` packages, never its
`domain`. `ArchitectureTest` fails the build when any of this is broken, or when modules depend on each other in a
cycle.

## Adding an endpoint

1. Agree the change in `api/banking-api.yaml` first, then check it renders in the docs (below).
2. Put the request and response records in the module's `dto` package, and the controller in `controller`. Guard
   the endpoint with the scope from the contract: `@PreAuthorize("hasAuthority('SCOPE_transfers')")`.
3. Put the logic in a class in `service`. Get the calling client with `CurrentClient.id()` and check ownership there.
4. Refuse with `throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "...")`; the handler returns the contract's
   problem details. A new code goes into the contract's `Error.code` list and `ErrorCode` together.
5. Entities and repositories go in `domain`. A database change is a new file in
   `src/main/resources/db/changelog/changes/`, added to the end of `db.changelog-master.yaml`.
6. Test it: an `@IntegrationTest` (real PostgreSQL through Testcontainers, MockMvc) that checks responses against the
   contract with `openApi().isValid("api/banking-api.yaml")`, as `TokenEndpointIT` does.

## Run locally

Needs JDK 21 and Docker, and the auth service running from `cba-auth` (`docker compose up -d` there; it listens on
port 8180).

```bash
cp .env.example .env            # then set BANKING_API_DB_PASSWORD to any random value
docker compose up -d --build    # the Banking API on http://localhost:8085, health on http://localhost:9105
```

Or run it from the IDE or Maven, against the compose database:

```bash
docker compose up -d banking-api-db
BANKING_API_DB_PASSWORD=<value from .env> ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile points at `localhost:5434` and `localhost:8180`, and logs as plain text.

Get a token as the App (its secret is `APP_CLIENT_SECRET` in `cba-auth/.env`):

```bash
curl -s -X POST http://localhost:8085/v1/auth/token -u "app:<secret>" -d grant_type=client_credentials
```

## Tests

```bash
./mvnw verify
```

Runs unit, integration and architecture tests (Docker must be running for Testcontainers) and fails under 80% line
coverage. The coverage report is in `target/site/jacoco/index.html`.

## API docs

`docs/index.html` renders `api/banking-api.yaml` with Scalar. Serve the repository root and open `/docs/`:

```bash
python3 -m http.server 8090     # http://localhost:8090/docs/
npx @redocly/cli@1.34.5 lint api/banking-api.yaml
```

## Deploying

Build the image with `docker build -t banking-api .`. It runs as a non-root user.

| Port | Serves |
|---|---|
| 8080 | The API, behind the load balancer |
| 9001 | `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`. Private network only |

Before the first start, create a PostgreSQL database, a user, and a schema named `banking_api` owned by that user.
Liquibase creates and updates the tables on every start.

### The auth service's two addresses

The settings below reach the same auth service by two different addresses:

| | `<auth-domain>` | `<auth service's internal address>` |
|---|---|---|
| Example | `https://auth.dev.cba.1ubank.com` | `http://cba-auth.internal:8080` |
| What it is | Its public name, through the load balancer, with TLS. The value of `KC_HOSTNAME` in cba-auth | Its private address inside the AWS network, straight to the container (for example its service-discovery name) |
| Reachable from the internet | Yes, apart from `/admin` | No |
| Used here for | `BANKING_API_AUTH_ISSUER` only | `BANKING_API_AUTH_TOKEN_URL` and `BANKING_API_AUTH_JWK_SET_URI` |

The Banking API calls the auth service on its internal address: it is faster and never leaves the private network. It
checks every token against the public name, because the auth service writes that name into each token as its issuer,
whichever address the token was requested on.

### Environment variables

Required (the service does not start without them):

| Variable | Value |
|---|---|
| `BANKING_API_DB_URL` | `jdbc:postgresql://<host>:5432/<database>` |
| `BANKING_API_DB_USERNAME` | The database user |
| `BANKING_API_DB_PASSWORD` | Secret, from the secrets manager |
| `BANKING_API_AUTH_TOKEN_URL` | `http://<auth service's internal address>/realms/clients/protocol/openid-connect/token` |
| `BANKING_API_AUTH_JWK_SET_URI` | `http://<auth service's internal address>/realms/clients/protocol/openid-connect/certs` |
| `BANKING_API_AUTH_ISSUER` | `https://<auth-domain>/realms/clients` (see "The auth service's two addresses") |

Optional:

| Variable | Default | Value |
|---|---|---|
| `BANKING_API_DB_POOL_SIZE` | `10` | Database connections per instance |
| `BANKING_API_LOG_FORMAT` | `ecs` | Log format: JSON (Elastic Common Schema) |
| `BANKING_API_PROBLEM_TYPE_BASE_URI` | `https://docs.cba.1ubank.example/problems/` | Base of the `type` URI in error responses |

