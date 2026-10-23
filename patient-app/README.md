# Curo — patient app

Curo is a shared medicine record. Every patient has **one CareID**, and doctors, pharmacies and caregivers use it to see the same up-to-date list of medicines, instead of each keeping its own paper trail. This folder is the patient-facing mobile app (Expo React Native). It is part of the MedSync project built for IMPULSE '26.

> All data in this repo is fake. Never put real patient data in the mocks.

## What it does

A patient signs up, gets a CareID, and sees every prescription written for them in one place. The app warns about allergy conflicts and dangerous drug combinations, reminds them of today's doses, and shows who has looked at their record.

## Demo story

**Prescribed → Dispensed → Taken → Monitored**

1. **Prescribed** — a doctor looks the patient up by CareID and prescribes. The system checks the new drug against the patient's allergies and current medicines before it is saved.
2. **Dispensed** — a pharmacy finds the same CareID, sees the prescription and marks it dispensed.
3. **Taken** — the patient sees today's schedule in the app and marks each dose taken.
4. **Monitored** — missed doses raise alerts, and the patient can see in the access log who viewed their record.

## What's reused vs built here

**Reused from an earlier project (MedTrace):** the login, signup and prescription-view foundation. It lives in `_reference/` at the repo root (gitignored, read-only) and is declared in the root README.

**Dropped from the earlier project:** the ABHA integration, the blockchain module and the X-API-Key modules. Identity here is the CareID only.

**Built for this project:** CareID identity, drug-interaction and allergy checks, dose tracking, access log, caregiver and consent flows, appointment booking, and this patient app.

## Stack

- **App:** Expo (React Native), plain JavaScript
- **Backend:** Spring Boot on EC2
- **Database:** MongoDB Atlas
- **Auth:** JWT
- **AWS services (these five only):** EC2, SNS, EventBridge, IAM, CloudTrail

## Repo layout

```
backend/       Spring Boot API
frontend/      Web frontend for doctors / pharmacies
patient-app/   This app (Expo React Native)
infra/         AWS setup
docs/          API_CONTRACT.md, STATUS.md
_reference/    Reused MedTrace foundation (gitignored, read-only)
```

Inside `patient-app/`:

```
App.js              Screen switching, login/signup, Home, Today, Settings, access log
BottomNav.js        Home / Today / QR / Book / Family tabs
QrTab.js            CareID as a QR code
BookTab.js          Appointment booking (mock)
FamilyTab.js        Family members
ProfileCompletion.js  Medical profile form
PrivacyNotice.js    Privacy notice and consent text
config.js           API URL and the MOCK_MODE switch
mocks/data.js       Fake patient, prescriptions, schedule, access log
```

## Running the patient app

Requires Node.js and the Expo Go app on a phone (or an Android/iOS simulator).

```bash
cd patient-app
npm install
npx expo start -c
```

Scan the QR code with Expo Go, or press `a` / `i` for a simulator.

### Environment setup

The app runs on local mock data by default (`MOCK_MODE = true` in `config.js`) and makes no network calls. To point it at the real backend, create `patient-app/.env`:

```
EXPO_PUBLIC_API_URL=https://your-ec2-address
```

Then set `MOCK_MODE` to `false` in `config.js` and restart with `npx expo start -c`. Keep secrets out of this file and out of git. Only the API URL belongs here, and `.env*.local` files are gitignored.

## Features

Status in this app: **mock** = working screen on fake data, **planned** = depends on the backend.

- **CareID** — one health ID per patient, shown on Home and as a QR code (mock)
- **Drug-interaction and allergy checks** — banners for allergy conflicts and drug–drug interactions (mock)
- **Dose tracking** — today's schedule with "Mark taken" (mock)
- **Missed-dose alerts** — reminders sent when a dose is not marked (planned; SNS + EventBridge)
- **Access log** — who viewed the record and when (mock)
- **Consent approval** — patient approves who may see the record (planned)
- **Pharmacy finder** — find a nearby pharmacy (planned)

Also in the app: prescription history with search, family members, appointment booking, profile editing and a privacy notice.

## Team ownership

| Area | Owner |
| --- | --- |
| `infra/` and AWS | Surya |
| `backend/` and `frontend/` | Vasuki |
| `patient-app/` | Sangami |

Each folder has one owner and its own branch. Contract changes go in `docs/API_CONTRACT.md` in the same commit and are announced to the team.

## Future scope

- ABHA / ABDM integration
- NMC / HPR doctor verification
- Amazon Timestream for dose and vitals time series
- AWS HealthLake for standards-based health records
- Amazon OpenSearch for record and drug search
- Amazon Comprehend Medical for reading prescriptions and notes
