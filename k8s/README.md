# Kubernetes deployment

The `k8s/stage` directory is the GitOps source watched by the `turn-api-stage` Argo CD application.

The cluster must provide these secrets outside Git:

- `turn-stage-secrets`: `POSTGRES_USER`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `APP_JWT_SECRET`, `APP_ALLOWED_ORIGINS`, `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH`, `APP_PAYMENT_CALLBACK_BASE_URL`, `BIRBANK_USERNAME`, `BIRBANK_PASSWORD`, `ABB_USERNAME`, `ABB_PASSWORD`, `ABB_DEBIT_ACCOUNT`, `ABB_RECIPIENT_NAME`, `ABB_RECIPIENT_ACCOUNT`, `ABB_RECIPIENT_TAX_ID`, `ABB_RECIPIENT_BANK_CODE`
- `ghcr-creds`: a pull secret with read access to the private GHCR images

The CI workflow builds an immutable image tagged with the source commit SHA and updates `k8s/stage/kustomization.yaml`. Argo CD then performs the rolling deployment automatically.
