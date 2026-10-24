# Curo (IMPULSE '26)

Curo is a patient–doctor medication coordination platform. Every patient gets **one CareID**, and doctors, pharmacies, caregivers and the patient all work from the same up-to-date medicine record instead of separate paper trails.

> All data in this repo is fake. Never commit real patient data or secrets.

## Demo story

**Prescribed → Dispensed → Taken → Monitored**

1. **Prescribed**: a doctor looks the patient up by CareID and prescribes. The new drug is checked against the patient's allergies and current medicines (non-blocking warnings).
2. **Dispensed**: a pharmacy finds the CareID, sees the active prescriptions and marks them dispensed.
3. **Taken**: the patient sees today's dose schedule in the app and marks each dose taken.
4. **Monitored**: missed doses raise alerts (patient, caregivers, doctor), and the patient can see in the access log who viewed their record.

## Features

- CareID identity, signup/login with JWT and recorded policy consent
- Roles: `DOCTOR`, `PATIENT`, `CAREGIVER`, `PHARMACY`, `HOSPITAL`, `RECEPTION`, `ADMIN`, `SUPER_ADMIN`
- Doctor and pharmacy licence checks against a mock `licence_registry` (otherwise `PENDING` for super-admin approval)
- Patient-controlled access: access grants, six-digit single-use share codes, guest share, and a full audit/access log
- Prescriptions with versions, modify, discontinue and history; pharmacy lookup by CareID and dispensing
- Dose schedule, dose log and adherence; missed-dose and reminder alerts (Amazon SNS, or logged when no topic is set)
- Drug-interaction and allergy checks (built-in tables)
- Caregiver links, reception check-in and doctor queue, doctor leave, hospital and pharmacy facility management
- Internal scheduler endpoint for EventBridge (`X-Internal-Key`)

## Repo layout

```
curo-backend/   Spring Boot API (Java 21, Maven)
frontend/       Web app for reception, doctors, pharmacies and hospitals (React + Vite)
patient-app/    Patient mobile app (Expo React Native)
docs/           API_CONTRACT.md, STATUS.md
.env.example    Environment variable names
_reference/     Reused MedTrace foundation (gitignored, read-only)
```

## Tech stack

| Part | Stack |
| --- | --- |
| Backend | Spring Boot 4.1, Java 21, Spring Security, JWT (jjwt), Spring Data MongoDB, AWS SDK for SNS, Lombok |
| Database | MongoDB Atlas |
| Web | React 19, Vite, React Router 7, axios, QR code libraries |
| Mobile | Expo, React Native |
| AWS | EC2 (hosting), EventBridge + SNS (scheduling and notifications), Bedrock, Location Service |

Backend code is split into `controller`, `service`, `repository`, `model`, `dto` and `security` packages under `curo-backend/src/main/java/com/curo/curo_backend/`. Code under `legacy` packages and `/legacy/**` routes is old disabled code (profile `legacy-jpa`).

## Getting started

### Configuration

Secrets live only in environment variables. Copy `.env.example` and fill it in locally (`.env` is gitignored).

| Variable | Required | Purpose |
| --- | --- | --- |
| `MONGO_URI` | yes | MongoDB connection string (`spring.mongodb.uri`) |
| `JWT_SECRET` | yes | JWT signing secret |
| `MONGO_DATABASE` | no | Database name (default `curo`) |
| `PORT` | no | Server port (default `8080`) |
| `CORS_ALLOWED_ORIGINS` | no | Comma-separated exact origins (default `http://localhost:5173,http://localhost:8081`) |
| `INTERNAL_API_KEY` | no | Shared secret for the EventBridge scheduler endpoint; empty refuses everyone |
| `ALERTS_SNS_TOPIC_ARN` | no | SNS topic for notifications; unset logs them instead |
| `AWS_REGION` | no | AWS region (`.env.example` uses `ap-south-1`) |
| `MISSED_GRACE_MINUTES` | no | Minutes after a dose is due before it counts as missed (default 30) |
| `LICENCE_VERIFICATION_MODE` | no | Only `mock` is implemented (default) |
| `DEMO_SEED` / `DEMO_PASSWORD` | no | Set `DEMO_SEED=true` to create a fake demo world for development. Never use against real data |

### Backend

Requires Java 21 and a MongoDB database.

```bash
cd curo-backend
export MONGO_URI=... JWT_SECRET=...
./mvnw spring-boot:run     # http://localhost:8080
./mvnw test
```

Health check: `GET /health`.

### Web frontend

```bash
cd frontend
cp .env.example .env       # set VITE_API_URL to the backend URL
npm install
npm run dev                # http://localhost:5173
npm run build
npm run lint
```

### Patient app

```bash
cd patient-app
npm install
npx expo start -c
```

With no `EXPO_PUBLIC_API_URL` the app runs entirely on fake mock data. Set it in `patient-app/.env` to go live (login, home and today screens), then restart with `-c`. See `patient-app/README.md`.

## API

The REST API uses JSON with camelCase fields, `Authorization: Bearer <JWT>`, and errors shaped `{ "error": "CODE", "message": "..." }`. The full contract, including which endpoints are built and which are not, is in [`docs/API_CONTRACT.md`](docs/API_CONTRACT.md) (draft). Main areas: `/auth`, `/patient`, `/access-grants`, `/prescription`, `/pharmacy`, `/reception`, `/doctor`, `/alerts`, `/caregiver-links`, `/hospital`, `/admin`.

## Reused foundation

The login, signup and prescription-view foundation is reused from an earlier project, **MedTrace**, kept in `_reference/` at the repo root (gitignored, read-only). ABHA integration, the blockchain module and the X-API-Key modules were dropped; identity here is the CareID only.

## Team and workflow

| Area | Owner |
| --- | --- |
| `infra/` and AWS | Surya |
| `curo-backend/` and `frontend/` | Vasuki |
| `patient-app/` | Sangami |

- One owner per folder, each on their own branch; never run two AI tools on the same folder.
- Pull before starting, push when done; commit before every big AI edit and read every diff.
- Contract changes go in `docs/API_CONTRACT.md` in the same commit and are announced.
- Spring Boot properties use `spring.mongodb.uri` / `spring.mongodb.database` (not `spring.data.mongodb.*`).
- Progress is tracked in `docs/STATUS.md`.

## Future scope

Governemnt integration, doctor lisence verification, Bedrock-backed interaction checks, Amazon Timestream, HealthLake, OpenSearch and Comprehend Medical.
