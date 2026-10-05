# Runbook: backend deploy & rollback

## Deploy (staging/production)
1. Build image from the merged commit: `docker build -t chattlyx/api:<sha> backend/`.
2. Push to the registry; update the K8s deployment image tag (Terraform-managed
   in `infra/`).
3. Migrations are forward-only and run on boot (`SchemaMigrator`). A failing
   migration aborts startup — the old pods keep serving (rolling update never
   drains).
4. Verify: `/healthz` 200, `/v1/config` returns expected `apiVersion`,
   Prometheus `http_requests_total` climbing, no `5xx` spike in Grafana.

## Rollback
1. Re-tag the previous known-good image and roll the deployment back.
2. Migrations never roll back; if a schema change is implicated, forward-fix
   (additive migrations only) — see `docs/decisions/ADR-template.md` for the
   incident-record format.

## Config & secrets
- All secrets come from the cluster secret manager (env injection). No
  secrets in images or the repo.
- Redis presence TTL is 75 s; after a Redis failover, presence reads are
  eventually consistent within one TTL.

## Smoke test
- `curl /healthz`, register a dev-mode OTP account, send a 1:1 message, upload
  + download an attachment, create a group — the same flow the integration
  suite runs on every merge.
