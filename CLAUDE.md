# Curo (IMPULSE '26)
Patient-doctor medication coordination. Spring Boot + MongoDB Atlas + React + Expo app. AWS: EC2, EventBridge+SNS, Bedrock, Location Service.

## Ownership (one owner per folder, own branch)
- infra/ and AWS: Surya
- backend/ and frontend/: Vasuki
- patient-app/: Sangami
Never run two AI tools on the same folder.

## Rules
- Secrets only in env vars (MONGO_URI, JWT_SECRET). Never commit or paste them.
- Fake patient data only.
- Commit before every big AI edit. Read every diff. Plan mode first for big changes.
- Contract changes go in docs/API_CONTRACT.md in the same commit, and are announced.
- Pull before starting, push when done.
- Spring Boot properties: spring.mongodb.uri / spring.mongodb.database (not spring.data.mongodb.*)
- Roles: DOCTOR, PATIENT, CAREGIVER, PHARMACY, ADMIN
- Reused MedTrace foundation lives in _reference/ (gitignored, read-only). Declared in README.

## Shared memory
CLAUDE.md, docs/API_CONTRACT.md, docs/STATUS.md
