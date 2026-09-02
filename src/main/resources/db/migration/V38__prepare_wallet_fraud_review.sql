alter table users add column if not exists confirmed_wallet_fraud_count integer not null default 0;

alter table users drop constraint if exists chk_users_confirmed_wallet_fraud_count;
alter table users add constraint chk_users_confirmed_wallet_fraud_count check (
    confirmed_wallet_fraud_count >= 0
);

alter table wallet_transactions drop constraint if exists chk_wallet_transactions_type;
alter table wallet_transactions add constraint chk_wallet_transactions_type check (
    transaction_type in (
        'ADMIN_CREDIT',
        'TOP_UP',
        'TOP_UP_REVERSAL',
        'SUBSCRIPTION_PAYMENT',
        'REFUND'
    )
);

alter table wallet_transactions drop constraint if exists chk_wallet_transactions_type_direction;
alter table wallet_transactions add constraint chk_wallet_transactions_type_direction check (
    (transaction_type in ('ADMIN_CREDIT', 'TOP_UP', 'REFUND') and direction = 'CREDIT')
    or (transaction_type in ('TOP_UP_REVERSAL', 'SUBSCRIPTION_PAYMENT') and direction = 'DEBIT')
);

alter table wallet_top_up_requests add column if not exists reversal_wallet_transaction_id bigint;
alter table wallet_top_up_requests add column if not exists fraud_count_after integer;

alter table wallet_top_up_requests drop constraint if exists fk_wallet_top_up_requests_reversal_transaction;
alter table wallet_top_up_requests add constraint fk_wallet_top_up_requests_reversal_transaction
    foreign key (reversal_wallet_transaction_id) references wallet_transactions (id);

alter table wallet_top_up_requests drop constraint if exists chk_wallet_top_up_requests_fraud_count;
alter table wallet_top_up_requests add constraint chk_wallet_top_up_requests_fraud_count check (
    fraud_count_after is null or fraud_count_after >= 1
);

create unique index if not exists uq_wallet_top_up_requests_reversal_transaction
    on wallet_top_up_requests (reversal_wallet_transaction_id);

alter table wallet_top_up_requests drop constraint if exists chk_wallet_top_up_requests_status;
alter table wallet_top_up_requests add constraint chk_wallet_top_up_requests_status check (
    status in (
        'AWAITING_RECEIPT',
        'PENDING_REVIEW',
        'MANUAL_REVIEW',
        'AUTO_CREDITED_PENDING_REVIEW',
        'APPROVED',
        'VERIFIED',
        'REJECTED',
        'FRAUD_CONFIRMED',
        'EXPIRED'
    )
);

alter table wallet_top_up_requests drop constraint if exists chk_wallet_top_up_requests_state;
alter table wallet_top_up_requests add constraint chk_wallet_top_up_requests_state check (
    (status = 'AWAITING_RECEIPT'
        and active_user_id = user_id
        and receipt_uploaded_at is null
        and receipt_attachment_id is null
        and reviewed_by_admin_id is null
        and reviewed_at is null
        and resolution_note is null
        and wallet_transaction_id is null
        and reversal_wallet_transaction_id is null
        and fraud_count_after is null)
    or (status in ('PENDING_REVIEW', 'MANUAL_REVIEW')
        and active_user_id = user_id
        and receipt_uploaded_at is not null
        and receipt_attachment_id is not null
        and reviewed_by_admin_id is null
        and reviewed_at is null
        and resolution_note is null
        and wallet_transaction_id is null
        and reversal_wallet_transaction_id is null
        and fraud_count_after is null)
    or (status = 'AUTO_CREDITED_PENDING_REVIEW'
        and active_user_id = user_id
        and receipt_uploaded_at is not null
        and receipt_attachment_id is not null
        and reviewed_by_admin_id is null
        and reviewed_at is null
        and resolution_note is null
        and wallet_transaction_id is not null
        and reversal_wallet_transaction_id is null
        and fraud_count_after is null)
    or (status in ('APPROVED', 'VERIFIED')
        and active_user_id is null
        and receipt_uploaded_at is not null
        and receipt_attachment_id is not null
        and reviewed_by_admin_id is not null
        and reviewed_at is not null
        and wallet_transaction_id is not null
        and reversal_wallet_transaction_id is null
        and fraud_count_after is null)
    or (status = 'REJECTED'
        and active_user_id is null
        and receipt_uploaded_at is not null
        and receipt_attachment_id is not null
        and reviewed_by_admin_id is not null
        and reviewed_at is not null
        and resolution_note is not null
        and trim(resolution_note) <> ''
        and fraud_count_after is null
        and ((wallet_transaction_id is null and reversal_wallet_transaction_id is null)
            or (wallet_transaction_id is not null and reversal_wallet_transaction_id is not null)))
    or (status = 'FRAUD_CONFIRMED'
        and active_user_id is null
        and receipt_uploaded_at is not null
        and receipt_attachment_id is not null
        and reviewed_by_admin_id is not null
        and reviewed_at is not null
        and resolution_note is not null
        and trim(resolution_note) <> ''
        and ((wallet_transaction_id is null and reversal_wallet_transaction_id is null)
            or (wallet_transaction_id is not null and reversal_wallet_transaction_id is not null))
        and fraud_count_after is not null)
    or (status = 'EXPIRED'
        and active_user_id is null
        and receipt_uploaded_at is null
        and receipt_attachment_id is null
        and reviewed_by_admin_id is null
        and reviewed_at is null
        and resolution_note is null
        and wallet_transaction_id is null
        and reversal_wallet_transaction_id is null
        and fraud_count_after is null)
);

alter table subscription_coin_payments add column if not exists subscription_state_captured boolean not null default false;
alter table subscription_coin_payments add column if not exists subscription_existed_before boolean not null default false;
alter table subscription_coin_payments add column if not exists previous_subscription_plan_id bigint;
alter table subscription_coin_payments add column if not exists previous_subscription_status varchar(30);
alter table subscription_coin_payments add column if not exists previous_billing_period varchar(20);
alter table subscription_coin_payments add column if not exists previous_room_limit integer;
alter table subscription_coin_payments add column if not exists previous_employee_limit integer;
alter table subscription_coin_payments add column if not exists previous_starts_at timestamp;
alter table subscription_coin_payments add column if not exists previous_expires_at timestamp;
alter table subscription_coin_payments add column if not exists previous_grace_ends_at timestamp;
alter table subscription_coin_payments add column if not exists previous_usage_grace_ends_at timestamp;
alter table subscription_coin_payments add column if not exists cancelled_at timestamp;
alter table subscription_coin_payments add column if not exists cancelled_by_admin_id bigint;
alter table subscription_coin_payments add column if not exists cancellation_reason varchar(1000);
alter table subscription_coin_payments add column if not exists refund_wallet_transaction_id bigint;
alter table subscription_coin_payments add column if not exists fraud_top_up_request_id bigint;

alter table subscription_coin_payments drop constraint if exists fk_subscription_coin_payments_previous_plan;
alter table subscription_coin_payments add constraint fk_subscription_coin_payments_previous_plan
    foreign key (previous_subscription_plan_id) references subscription_plans (id);

alter table subscription_coin_payments drop constraint if exists fk_subscription_coin_payments_cancelled_admin;
alter table subscription_coin_payments add constraint fk_subscription_coin_payments_cancelled_admin
    foreign key (cancelled_by_admin_id) references admin_accounts (id);

alter table subscription_coin_payments drop constraint if exists fk_subscription_coin_payments_refund_transaction;
alter table subscription_coin_payments add constraint fk_subscription_coin_payments_refund_transaction
    foreign key (refund_wallet_transaction_id) references wallet_transactions (id);

alter table subscription_coin_payments drop constraint if exists fk_subscription_coin_payments_fraud_top_up;
alter table subscription_coin_payments add constraint fk_subscription_coin_payments_fraud_top_up
    foreign key (fraud_top_up_request_id) references wallet_top_up_requests (id);

alter table subscription_coin_payments drop constraint if exists chk_subscription_coin_payments_status;
alter table subscription_coin_payments add constraint chk_subscription_coin_payments_status check (
    status in ('COMPLETED', 'CANCELLED')
);

alter table subscription_coin_payments drop constraint if exists chk_subscription_coin_payments_snapshot;
alter table subscription_coin_payments add constraint chk_subscription_coin_payments_snapshot check (
    (subscription_state_captured = false
        and subscription_existed_before = false
        and previous_subscription_plan_id is null
        and previous_subscription_status is null
        and previous_billing_period is null
        and previous_room_limit is null
        and previous_employee_limit is null
        and previous_starts_at is null
        and previous_expires_at is null
        and previous_grace_ends_at is null
        and previous_usage_grace_ends_at is null)
    or (subscription_state_captured = true
        and subscription_existed_before = false
        and previous_subscription_plan_id is null
        and previous_subscription_status is null
        and previous_billing_period is null
        and previous_room_limit is null
        and previous_employee_limit is null
        and previous_starts_at is null
        and previous_expires_at is null
        and previous_grace_ends_at is null
        and previous_usage_grace_ends_at is null)
    or (subscription_state_captured = true
        and subscription_existed_before = true
        and previous_subscription_plan_id is not null
        and previous_subscription_status is not null
        and previous_billing_period is not null
        and previous_room_limit is not null
        and previous_employee_limit is not null)
);

alter table subscription_coin_payments drop constraint if exists chk_subscription_coin_payments_cancellation;
alter table subscription_coin_payments add constraint chk_subscription_coin_payments_cancellation check (
    (status = 'COMPLETED'
        and cancelled_at is null
        and cancelled_by_admin_id is null
        and cancellation_reason is null
        and refund_wallet_transaction_id is null
        and fraud_top_up_request_id is null)
    or (status = 'CANCELLED'
        and cancelled_at is not null
        and cancelled_by_admin_id is not null
        and cancellation_reason is not null
        and trim(cancellation_reason) <> ''
        and refund_wallet_transaction_id is not null
        and fraud_top_up_request_id is not null)
);

create unique index if not exists uq_subscription_coin_payments_refund_transaction
    on subscription_coin_payments (refund_wallet_transaction_id);

create index if not exists idx_subscription_coin_payments_payer_review
    on subscription_coin_payments (payer_user_id, status, created_at, id);
