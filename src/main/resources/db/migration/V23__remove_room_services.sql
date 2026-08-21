alter table planned_bookings
    drop column if exists room_service_id;

drop table if exists room_services;
