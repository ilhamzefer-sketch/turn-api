# Wallet payment lifecycle and wallet/admin experience

This contract applies to the backend and matching `enovbe-web` wallet/admin changes. Stage runs in live Epoint mode only when its external merchant keys are configured. Automated tests use an isolated fake provider; they do not perform a live card payment.

## Custom amount contract (Step 1, V44)

`POST /api/users/me/wallet/top-up-requests` now accepts `{ "amountAzn": "0.10" }` (a JSON number also works). Send exactly one of `amountAzn` and the legacy `packageCode`. The endpoint keeps its existing HTTP 200 success response. Missing/both selections, amounts below the minimum, excessive precision, non-10-qəpik increments, and amounts over the configured maximum return 400 before creating an attempt.

The minimum is **0.10 AZN / 10 qəpik = 1 coin**. The rate is fixed at 10 coins per AZN, matching the existing database and package catalog; configuration now rejects other rates. Wallets hold whole coins, so amounts increase in 0.10 AZN steps. Current stage limits remain 1–1,000,000 coins (0.10–100,000.00 AZN). The backend validates with decimal arithmetic and derives `coinAmount`; a client-provided coin count cannot change it. Amount and coins are persisted together as the purchase snapshot, and callbacks credit that snapshot only after the exact payment amount is confirmed.

Top-up options add `customAmountEnabled`, `minimumAmountAzn`, `maximumAmountAzn`, and `amountStepAzn`. Existing fields and package choices remain for compatibility with the currently deployed UI. Custom requests have `packageCode: null`; both user and admin responses support this. Equivalent legacy and custom amounts reuse the same active checkout. Different amounts supersede READY attempts; PREPARING/UNKNOWN attempts block a different amount until their outcome is resolved. Late success still credits the original saved amount exactly once.

Custom amounts require a configured card gateway. They return 503 when it is unavailable, even if manual top-ups are enabled: a static package payment link cannot safely charge an arbitrary amount. Historical manual requests keep their existing receipt workflow.

V44 makes the package reference optional and extends the request constraint with a separate custom-amount branch. Custom rows require the external provider, at least 0.10 AZN, and exactly 10 coins per AZN. Legacy package constraints, catalog entries, saved request amounts, and ledger history remain unchanged. No existing migration is edited.

Step 2 consumes these fields: replace package cards with an AZN input and live coin total, use **Ödəniş et**, and remove provider names from customer-facing copy. Release this compatible backend before that frontend change. Rollback after custom requests exist must retain nullable-package support; use a forward correction rather than deploying the old backend against new custom rows.

Verification includes minimum/maximum values, invalid payloads, client coin tampering, owner-only lookup, duplicate/late callbacks, same-amount retries, changed and unknown attempts, manual fallback rejection, PostgreSQL constraints, and historical migration fixtures. Provider traffic is simulated; no real card payment is made.

## Payment authority and durable attempts

A short database transaction saves the amount and coin snapshot and unique external order before any provider request. Provider I/O runs after that transaction commits. A second short transaction stores the provider transaction and redirect. A signed callback can therefore arrive before the checkout response and still find its order. Checkout finalization never replaces the financial outcome recorded by a callback.

The normal provider deadline is 10 seconds, including waiting for its response body; connection establishment is limited to 3 seconds. `EPOINT_CONNECT_TIMEOUT` and `EPOINT_REQUEST_TIMEOUT` configure these values, with positive connect <= request <= 15 seconds. Requests do not follow HTTP redirects. Provider checkout responses require success, a transaction identifier, and an HTTPS checkout URL.

Same-amount retries reuse the active attempt and do not issue another provider request. Changing amounts supersedes a ready attempt; its old checkout can still be paid, so it remains eligible for a valid late callback. A concurrent first-create conflict returns 409; fetching the active request or retrying the same amount resumes the winning attempt.

Timeouts, invalid responses, and transport/provider errors preserve the attempt as `UNKNOWN`. They do not imply that the customer was charged or that payment failed. The create endpoint returns the persisted request with a null checkout URL. A retry of that amount returns the same attempt. Selecting another amount while the outcome is unknown returns 409.

Callbacks verify the signature, exact persisted order, provider transaction, and payment amount. Currency is checked against the request snapshot when supplied. The documented callback does not include currency as a standard field, so its absence is accepted; the checkout itself always uses the saved AZN currency. If supplied, operation code must be `100` for a successful customer payment. Missing amount or transaction cannot credit a wallet. Only `failed` and `error` produce a provider failure; intermediate statuses cannot mark payment failed.

Signed success can settle `AWAITING_RECEIPT`, `SUPERSEDED`, `EXPIRED`, or `PAYMENT_FAILED`. The request row lock, existing ledger idempotency key `top-up-request:<id>`, and unique provider transaction binding protect concurrent/repeated callbacks. A paid request cannot be demoted by a later failure. The wallet ledger and PAID transition commit together.

## API contract for step 2

Existing request fields remain, with these additions:

| Field | Meaning |
| --- | --- |
| `paymentProvider` | `epoint` or `manual`; controls which completion UI applies. |
| `externalOrderId` | The persisted Epoint order, null for manual requests. |
| `checkoutState` | `NOT_REQUIRED`, `PREPARING`, `READY`, or `UNKNOWN`. This describes checkout creation, not whether money was received. |
| `paymentUrl` | Now nullable. An external URL is returned only for an awaiting request with a ready checkout. |
| `status` | Adds `SUPERSEDED`. `PAID` is the authoritative external-payment success state. |

`POST /api/users/me/wallet/top-up-requests` creates or resumes an attempt. `GET /api/users/me/wallet/top-up-requests/active` resumes the active request and does not confuse external payment with manual receipt eligibility. Ready requests expire locally after the existing 30-minute window; unknown/in-flight requests stay discoverable. Expiry or replacement cannot stop a valid later payment from being credited.

`GET /api/users/me/wallet/top-up-requests/{requestId}` returns the authenticated owner's request, including terminal states. Another user's ID and an unknown ID both return 404. Success/error redirect URLs include `requestId`; the browser looks up that ID and treats the URL's `payment` parameter only as a return hint. Bounded polling stops on server-confirmed outcomes and shows a pending state while the callback has not arrived. A PAID request can still have UNKNOWN checkout creation state after a timeout: financial status takes precedence.

Options retain `bankCardEnabled` and add `manualTopUpEnabled`. Missing/incomplete Epoint configuration returns bankCardEnabled false. Creating a request then returns 503 unless manual creation was explicitly enabled with `APP_WALLET_MANUAL_TOP_UP_ENABLED=true` (default false). When Epoint is configured it remains the selected provider. Existing manual requests remain readable, uploadable, and reviewable independently of the creation flag. External requests cannot enter the receipt-credit flow.

The web wallet uses the capability flags, resumes existing provider checkouts, and keeps manual receipt upload restricted to manual requests. The payment return uses the authenticated request lookup and polls for up to 30 seconds; a redirect alone never announces success. A late confirmed PAID state refreshes the balance and transaction history.

The admin top-up endpoint accepts `PAID_GROUP`, `FAILED_GROUP`, and `WAITING_GROUP` alongside the individual statuses and `REVIEW_REQUIRED`. Its page response includes an unfiltered `summary` with total, paid, failed, and waiting counts plus `paidTodayAmount`, `businessDate`, and `timezone: "Asia/Baku"`. Today's amount uses payment completion/review time and converts Baku day boundaries to the server clock zone used by stored local timestamps. The web admin view uses these groups and global totals, exposes pagination, and retains manual receipt review with a confirmation step.

## Migration and retired scope

V43 is a forward migration. Previously applied migrations are unchanged. It restores the current `AZN_3` catalog entry to 3.00 AZN / 30 coins, retaining every historical request snapshot, including 0.10 AZN / 1 coin and original 3.00 AZN / 30 coin records. It adds checkout state and transaction binding and converts historical `replaced_by_new_request` failures to `SUPERSEDED` without modifying balances or ledger entries.

The incomplete Epoint registration adapter now explicitly returns 410 and cannot create a charge or fabricate sandbox confirmation. Ordinary phone registration remains free. Existing stage legacy-API disabling is unchanged.

## Operational recovery and provider limits

This step does not automatically resend ambiguous checkout requests, synthesize callbacks, or infer success from browser redirects. If the process stops after preparation, the durable row can remain PREPARING; this is an unresolved attempt and must not be resubmitted blindly. If no callback ever arrives, use the saved order and any recorded provider transaction to investigate with the Epoint merchant portal/support and request an authentic callback replay. There is no new unattended provider reconciliation job or operator override endpoint in this step.

The provider transaction is saved in `external_checkout_transaction_id`; the callback result/reference remains in the existing external payment fields. A lost checkout response can leave the transaction unknown until a callback supplies it. Merchant-confirmed cancellation/no-charge recovery requires a separate deliberate operational action; local timeout/expiry alone is not proof of cancellation. Refund automation is outside this change.

Protocol evidence: [Epoint callbacks](https://developer.epoint.az/en/callbacks) specifies signature verification, order/transaction/amount evidence and statuses; [checkout request](https://developer.epoint.az/az/checkout/request) describes the success/redirect/transaction response. The existing `/payment-request` endpoint is retained. Tests use an isolated fake provider; live merchant configuration and callback delivery were not exercised.

## Verification

The two saved audit regressions were first reproduced as failures. Permanent API regressions now check both active resume and successful payment after replacement. PostgreSQL tests cover concurrent callbacks, concurrent creation, early callbacks before provider response, late success after failure/expiry/replacement, timeout/error recovery without another charge request, bad signature/order/amount/currency/transaction/operation evidence, owner-only lookup, and external receipt rejection. Migration tests cover fresh schema and V42 historical records upgrading to V43. Existing manual receipt and admin-review tests remain in the full suite.

Run `./mvnw clean verify` with Docker running. The new payment PostgreSQL suite requires Docker rather than silently skipping it. Local verification results are recorded in the root audit output directory.

The release gate runs `./mvnw clean verify` on Java 17, `npm run check`, the full desktop/mobile Playwright suite, and `npm audit --audit-level=high`. Frontend CI now includes the browser suite. Stage GitOps excludes the isolated Epoint sandbox overlay; `k8s/README.md` describes that boundary.

Business owners can create up to five draft rooms before subscribing. Publishing a room or starting operations still requires a valid subscription. The unpaid draft limit matches the product specification and is covered by an integration test.

Standards applied: [class structure constraints](</Users/camal/Documents/programming/base for ai project/research-brain/vault/folders/java-spring-boot-code-standards/docs/java-spring-boot-class-structure-hard-constraints.md>) governed top-level types and the 400-line limit; [project-aligned delivery](</Users/camal/Documents/programming/base for ai project/research-brain/vault/folders/java-spring-boot-code-standards/docs/java-spring-boot-project-aligned-delivery-contract.md>) kept existing packages and names; [testing strategy](</Users/camal/Documents/programming/base for ai project/research-brain/vault/folders/java-spring-boot-code-standards/docs/professional-spring-boot-testing-strategy.md>) drove behavioral and real-PostgreSQL checks; [service layer standards](</Users/camal/Documents/programming/base for ai project/research-brain/vault/folders/java-spring-boot-code-standards/docs/spring-boot-service-layer-standards.md>) informed transaction boundaries.
