alter table wallet_top_up_requests add column if not exists receipt_attachment_id bigint;

alter table wallet_top_up_requests drop constraint if exists fk_wallet_top_up_requests_receipt_attachment;
alter table wallet_top_up_requests add constraint fk_wallet_top_up_requests_receipt_attachment
    foreign key (receipt_attachment_id) references secure_attachments (id);

alter table wallet_top_up_requests drop constraint if exists chk_wallet_top_up_requests_state;
alter table wallet_top_up_requests add constraint chk_wallet_top_up_requests_state check (
    (status = 'AWAITING_RECEIPT'
        and active_user_id = user_id
        and receipt_uploaded_at is null
        and receipt_attachment_id is null
        and reviewed_by_admin_id is null
        and reviewed_at is null
        and resolution_note is null
        and wallet_transaction_id is null)
    or (status = 'PENDING_REVIEW'
        and active_user_id = user_id
        and receipt_uploaded_at is not null
        and receipt_attachment_id is not null
        and reviewed_by_admin_id is null
        and reviewed_at is null
        and resolution_note is null
        and wallet_transaction_id is null)
    or (status = 'APPROVED'
        and active_user_id is null
        and receipt_uploaded_at is not null
        and receipt_attachment_id is not null
        and reviewed_by_admin_id is not null
        and reviewed_at is not null
        and resolution_note is null
        and wallet_transaction_id is not null)
    or (status = 'REJECTED'
        and active_user_id is null
        and receipt_uploaded_at is not null
        and receipt_attachment_id is not null
        and reviewed_by_admin_id is not null
        and reviewed_at is not null
        and resolution_note is not null
        and trim(resolution_note) <> ''
        and wallet_transaction_id is null)
    or (status = 'EXPIRED'
        and active_user_id is null
        and receipt_uploaded_at is null
        and receipt_attachment_id is null
        and reviewed_by_admin_id is null
        and reviewed_at is null
        and resolution_note is null
        and wallet_transaction_id is null)
);

create unique index if not exists uq_wallet_top_up_requests_receipt_attachment
    on wallet_top_up_requests (receipt_attachment_id);
