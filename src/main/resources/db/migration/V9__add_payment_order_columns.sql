alter table payment_sessions
    add column if not exists external_order_id varchar(255);

alter table payment_sessions
    add column if not exists external_order_password varchar(255);

alter table payment_sessions
    add column if not exists external_hpp_url varchar(500);
