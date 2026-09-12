# Kubernetes deployment

The `k8s/stage` directory is the GitOps source watched by the `turn-api-stage` Argo CD application.

The cluster must provide these secrets outside Git:

- `turn-stage-secrets`: `POSTGRES_USER`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `APP_JWT_SECRET`, `APP_ALLOWED_ORIGINS`, `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH`
- `ghcr-creds`: a pull secret with read access to the private GHCR images

The CI workflow builds an immutable image tagged with the source commit SHA and updates `k8s/stage/kustomization.yaml`. Argo CD then performs the rolling deployment automatically.

`k8s/stage` deploys the live-payment API and does not include the Epoint sandbox. For isolated payment testing, `k8s/sandbox` is an optional overlay with the sandbox workload and checkout assets. Its image is pinned by digest. Apply that overlay only in a dedicated test environment with an appropriate sandbox route; Argo CD's stage application continues to watch `k8s/stage` alone.
