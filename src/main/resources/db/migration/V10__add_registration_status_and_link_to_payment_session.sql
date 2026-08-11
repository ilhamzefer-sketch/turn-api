alter table registrations
    add column if not exists status varchar(50) not null default 'ACTIVE';

alter table payment_sessions
    add column if not exists registration_id bigint;

alter table payment_sessions
    drop constraint if exists fk_payment_sessions_registration;

alter table payment_sessions
    add constraint fk_payment_sessions_registration
        foreign key (registration_id) references registrations(id);
