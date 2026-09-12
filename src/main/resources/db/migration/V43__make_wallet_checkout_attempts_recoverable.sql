alter table wallet_top_up_packages drop constraint chk_wallet_top_up_packages_fixed_catalog;
alter table wallet_top_up_packages drop constraint chk_wallet_top_up_packages_amount;
update wallet_top_up_packages set amount_azn = 3.00, coin_amount = 30 where code = 'AZN_3';
alter table wallet_top_up_packages add constraint chk_wallet_top_up_packages_amount
    check (amount_azn in (3.00, 5.00, 10.00, 15.00, 20.00));
alter table wallet_top_up_packages add constraint chk_wallet_top_up_packages_fixed_catalog check (
    (code = 'AZN_3' and amount_azn = 3.00 and coin_amount = 30
        and payment_url = 'https://cb.birbank.business/pay/7847238243e34c9c9dd4666f749d5879'
        and display_order = 1)
    or (code = 'AZN_5' and amount_azn = 5.00 and coin_amount = 50
        and payment_url = 'https://cb.birbank.business/pay/89a5510f409341ecbf82badf75512d21'
        and display_order = 2)
    or (code = 'AZN_10' and amount_azn = 10.00 and coin_amount = 100
        and payment_url = 'https://cb.birbank.business/pay/75c998cbda8e4674bb11cbf961d91c27'
        and display_order = 3)
    or (code = 'AZN_15' and amount_azn = 15.00 and coin_amount = 150
        and payment_url = 'https://cb.birbank.business/pay/1b0fcb5e4e5f4b23a4ed8ec1b8fbc978'
        and display_order = 4)
    or (code = 'AZN_20' and amount_azn = 20.00 and coin_amount = 200
        and payment_url = 'https://cb.birbank.business/pay/d2000eed920c4af68dc83106e6b56b9f'
        and display_order = 5)
);


alter table wallet_top_up_requests add column checkout_state varchar(20) not null default 'NOT_REQUIRED';
alter table wallet_top_up_requests add column external_checkout_transaction_id varchar(180);
create unique index uq_wallet_checkout_transaction on wallet_top_up_requests (external_checkout_transaction_id);
update wallet_top_up_requests set checkout_state = 'READY' where payment_provider <> 'manual';
alter table wallet_top_up_requests add constraint chk_wallet_checkout_state
    check ((payment_provider = 'manual' and checkout_state = 'NOT_REQUIRED')
        or (payment_provider <> 'manual' and checkout_state in ('PREPARING', 'READY', 'UNKNOWN')));
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
        'SUPERSEDED',
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
    or (status in ('PAYMENT_FAILED', 'SUPERSEDED')
        and active_user_id is null
        and payment_provider <> 'manual'
        and external_order_id is not null
        and (status = 'SUPERSEDED' or external_payment_status is not null)
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

update wallet_top_up_requests set status = 'SUPERSEDED'
where status = 'PAYMENT_FAILED' and payment_provider = 'epoint'
    and external_payment_status = 'replaced_by_new_request';
