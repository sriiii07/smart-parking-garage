# Northstar Garage — Smart Parking (Java web demonstration)

A functional browser interface backed by the **Java parking service**, with a visual 10-space garage, live occupancy pricing, entry confirmation, active tickets, exits, and searchable history. This extends the Phase 1–3 academic Java project with a small native Java HTTP server and plain HTML/CSS/JavaScript. Pricing, allocation, validation, and fee locking remain in Java.

> **Important scope:** This version stores data **in memory only**. All tickets and history reset when the server restarts. MySQL/JDBC persistence and the original Swing desktop interface have **not** been implemented. The UI calls the real Java services; it is not a static mockup or an independent JavaScript parking engine.

## Requirements

- Java 17 or newer
- Maven 3.9+ (3.8+ should also work)
- A modern browser
- Internet access the first time Maven downloads dependencies

## Run locally

```sh
cd smart-parking-garage
mvn test
mvn compile exec:java
```

Open **http://localhost:8080/**. Stop the Java server with Ctrl+C. Set a different port with the `PORT` environment variable, for example `PORT=8090 mvn compile exec:java` (macOS/Linux) or `$env:PORT=8090; mvn compile exec:java` (PowerShell).

To import into an IDE, open the directory containing `pom.xml` as a Maven project, select Java 17, then run `com.smartparking.web.WebApplication`. No separate Node server is used.

## Using the interface

1. Select **Car** or **Motorcycle**. Spots 01–08 accept both; spots 09–10 accept motorcycles only.
2. Enter a registration (e.g. `KL07AB1234`), select a green compatible space, review the displayed fee, and confirm in the dialog.
3. Save the ticket code shown in the receipt. An active session appears in the table.
4. Search by ticket or registration in **Process an exit**, check the **original agreed fee**, and explicitly confirm exit. The released spot turns available and history gains the completed record.
5. The current quote is determined by **occupied spots before entry**: 0–4 = INR 20; 5–7 = INR 40; 8–10 = INR 60. Existing tickets retain their fees. At full capacity a tier is still displayed but admission is rejected.

If occupancy changes between the initial quote and the booking request, the backend returns `QUOTE_CHANGED`; the page displays the updated price and **requires a fresh confirmation**. The server also rejects occupied, reserved, out-of-service, or incompatible spots regardless of what the browser displays.

## Architecture

- **Domain** (`model`): garage → floors → spots; abstract vehicle/spot types; immutable ticket fee and guarded session lifecycle.
- **Java business logic** (`pricing`, `service`, `validation`): pricing Strategy, allocation checks, pre-entry quote comparison, session/exit coordination, status derivation.
- **Browser adapter** (`web`): `WebApplication` starts the JDK `HttpServer`; `ParkingHttpHandler` exposes a small JSON API and serves bundled page assets. JSON uses Jackson. The HTTP layer does not compute prices.
- **Frontend** (`src/main/resources/web`): accessible responsive dashboard and JavaScript interactions; same-origin requests call the Java API. It does not call localhost from the user's browser when opened through a preview proxy.

The app binds to `0.0.0.0` to support local and hosted previews. It has no authentication and is intended as a **local academic simulation**, not as a public production service.

### HTTP endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/state` | Garage counts, spots, current quote, active and completed sessions |
| `POST /api/entry` | Validate and admit with `{registration,vehicleType,spotCode,confirmedFee}` |
| `POST /api/lookup` | Find active ticket with `{lookup}` |
| `POST /api/exit` | Complete exit and release spot with `{lookup}` |

All POST endpoints accept `Content-Type: application/json`. Successful entry and exit responses include the ticket and updated garage state. Stale fees return HTTP 409 with an `updatedFee`; validation errors and conflicts are reported as JSON.

## Tests and manual checklist

`mvn test` currently runs **28 passing JUnit tests** on Java 17: 14 pricing tests, 2 session tests, 10 service tests, and 2 HTTP integration tests. The integration tests verify that browser assets and API routes work, that Java services lock the original fee, and that stale quotes cannot create a partial booking.

Manual browser checklist:

- Select a space, change the vehicle type, and confirm an incompatible selection is cleared.
- Park five vehicles: the fifth ticket should cost INR 20 and the next displayed quote should be INR 40.
- Exit vehicles to cross 5 → 4 occupied; the next quote should return to INR 20 without changing old tickets.
- Try an occupied spot, duplicate registration, or repeat exit; the operation should be rejected.
- Confirm active list, history search, and mobile layout.
- Restart the server and confirm that demo records reset (persistence is not included yet).

## Upcoming work from the original desktop brief

MySQL schema/DAO transactions and restart persistence, plus the Swing desktop client, are separate unfinished phases. This web demonstration does **not** claim to satisfy those requirements yet.
