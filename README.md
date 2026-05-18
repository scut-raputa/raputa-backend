# Raputa Backend

Raputa backend is a Spring Boot service that provides authentication, patient and appointment management, device discovery and locking, real-time data collection, report/file storage, statistics, and model API orchestration.

## Stack

- Java 21
- Spring Boot 3.5
- Spring Web, Security, Validation, WebSocket
- Spring Data JPA
- MySQL
- JWT authentication with HttpOnly cookie sessions
- OpenAPI through SpringDoc

## Start

```bash
./mvnw spring-boot:run
```

Default URL:

```text
http://localhost:8080
```

OpenAPI UI:

```text
http://localhost:8080/swagger-ui.html
```

## Database

The development configuration uses MySQL:

```yaml
spring.datasource.url: jdbc:mysql://127.0.0.1:3306/raputa
spring.datasource.username: root
spring.datasource.password: 12345678
```

Create the database before starting the backend:

```sql
CREATE DATABASE raputa CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

For deployment, override the datasource and JWT secret instead of using the development defaults.

## Storage

Runtime files are stored under:

```text
RAPUTA_STORAGE_ROOT=/tmp/raputa/storage
RAPUTA_TMP_DIR=/tmp/raputa/tmp
```

These can be overridden with environment variables:

```bash
export RAPUTA_STORAGE_ROOT=/path/to/storage
export RAPUTA_TMP_DIR=/path/to/tmp
```

Stored files include uploaded signal files, generated reports, session files, and temporary file-mode uploads.

## Model Services

The backend calls two model API services:

| Task | Default URL | Auto endpoint | Manual endpoint |
| --- | --- | --- | --- |
| Dysphagia screening | `http://127.0.0.1:8001` | `/upload_predict/dys/` | `/upload_predict/dys_direct/` |
| Aspiration detection | `http://127.0.0.1:8002` | `/upload_predict/` | `/upload_predict/asp_direct/` |

Override URLs when the model services run elsewhere:

```bash
export RAPUTA_DYS_INFERENCE_BASE_URL=http://<host>:<port>
export RAPUTA_ASP_INFERENCE_BASE_URL=http://<host>:<port>
```

## Device Flow

- Device discovery updates device identity, IP, and online state.
- Hardware identity is treated as system-maintained device identity.
- A device can be occupied by one active detection session at a time.
- Heartbeats keep a session alive; stale sessions are cleaned by the backend.
- Force release asks the occupying browser to stop detection and release the device. If manual swallow classification is pending, the frontend keeps the result flow intact before releasing.

## Account Sessions

- Login issues an HttpOnly session cookie and marks the account as active.
- Authenticated requests refresh the account's last-seen time.
- Logout clears the cookie and marks the account as inactive.
- Administrator deletion is guarded on the backend: the current account, an online account, or an account that is still using a device cannot be deleted.

## Main API Groups

- `/api/user`: registration, login, current session, logout.
- `/api/admin/users`: administrator account management.
- `/api/patient`: patient CRUD.
- `/api/appointment`: appointment CRUD.
- `/api/check`: patient check records.
- `/api/screening-record`: appointment screening records and archive flow.
- `/api/device`: device registry, discovery, status refresh, and force release.
- `/api/realtime`: real-time device connection, segmentation mode, manual swallow segments, session finalization, and temporary CSV file controls.
- `/api/inference`: model health, model list, and file-mode detection.
- `/api/patient-file`: stored patient file overview.
- `/api/download`: single file, patient ZIP, and all-file ZIP downloads.
- `/api/stats`: dashboard statistics.

## Verification

```bash
./mvnw -q -DskipTests compile
```
