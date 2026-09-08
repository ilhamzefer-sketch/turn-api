alter table wallet_top_up_requests add column if not exists payment_provider varchar(30) not null default 'manual';
alter table wallet_top_up_requests add column if not exists external_order_id varchar(80);
alter table wallet_top_up_requests add column if not exists external_payment_reference varchar(180);
alter table wallet_top_up_requests add column if not exists external_payment_status varchar(40);

create unique index if not exists uq_wallet_top_up_requests_external_order
    on wallet_top_up_requests (external_order_id);

alter table wallet_top_up_requests drop constraint if exists chk_wallet_top_up_requests_status;
alter table wallet_top_up_requests add constraint chk_wallet_top_up_requests_status check (
    status in (
        'AWAITING_RECEIPT',
        'PENDING_REVIEW',
        'MANUAL_REVIEW',
        'AUTO_CREDITED_PENDING_REVIEW',
        'APPROVED',
        'VERIFIED',
        'PAID',
        'PAYMENT_FAILED',
        'REJECTED',
        'FRAUD_CONFIRMED',
        'EXPIRED'
    )
);

alter table wallet_top_up_requests drop constraint if exists chk_wallet_top_up_requests_fixed_package;
alter table wallet_top_up_requests add constraint chk_wallet_top_up_requests_fixed_package check (
    (package_code = 'AZN_3' and amount_azn = 3 and coin_amount = 30)
    or (package_code = 'AZN_5' and amount_azn = 5 and coin_amount = 50)
    or (package_code = 'AZN_10' and amount_azn = 10 and coin_amount = 100)
    or (package_code = 'AZN_15' and amount_azn = 15 and coin_amount = 150)
    or (package_code = 'AZN_20' and amount_azn = 20 and coin_amount = 200)
);

alter table wallet_top_up_requests drop constraint if exists chk_wallet_top_up_requests_time_window;
alter table wallet_top_up_requests add constraint chk_wallet_top_up_requests_time_window check (
    receipt_deadline_at = clicked_at + interval '30' minute
    and receipt_deadline_at > clicked_at
    and (receipt_uploaded_at is null
        or status = 'PAID'
        or receipt_uploaded_at < receipt_deadline_at)
    and (reviewed_at is null or (receipt_uploaded_at is not null and reviewed_at >= receipt_uploaded_at))
    and updated_at >= created_at
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
    or (status = 'PAID'
        and active_user_id is null
        and payment_provider <> 'manual'
        and external_order_id is not null
        and external_payment_status is not null
        and receipt_uploaded_at is not null
        and receipt_attachment_id is null
        and reviewed_by_admin_id is null
        and reviewed_at is null
        and resolution_note is null
        and wallet_transaction_id is not null
        and reversal_wallet_transaction_id is null
        and fraud_count_after is null)
    or (status = 'PAYMENT_FAILED'
        and active_user_id is null
        and payment_provider <> 'manual'
        and external_order_id is not null
        and external_payment_status is not null
        and receipt_attachment_id is null
        and reviewed_by_admin_id is null
        and reviewed_at is null
        and resolution_note is null
        and wallet_transaction_id is null
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
