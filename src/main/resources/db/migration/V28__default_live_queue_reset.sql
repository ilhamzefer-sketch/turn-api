update rooms
set live_queue_reset_policy = 'DAILY_AT_TIME',
    live_queue_reset_local_time = time '00:00:00',
    live_queue_reset_interval_minutes = null,
    updated_at = current_timestamp
where reservation_mode = 'LIVE_QUEUE'
  and (
      live_queue_reset_policy is null
      or (
          live_queue_reset_policy = 'EVERY_INTERVAL'
          and (
              live_queue_reset_interval_minutes is null
              or live_queue_reset_interval_minutes <= 0
          )
      )
  );

update rooms
set live_queue_reset_local_time = coalesce(live_queue_reset_local_time, time '00:00:00'),
    live_queue_reset_interval_minutes = null,
    updated_at = current_timestamp
where reservation_mode = 'LIVE_QUEUE'
  and live_queue_reset_policy = 'DAILY_AT_TIME';

update rooms
set live_queue_reset_local_time = null,
    updated_at = current_timestamp
where reservation_mode = 'LIVE_QUEUE'
  and live_queue_reset_policy = 'EVERY_INTERVAL';

update rooms
set live_queue_reset_policy = null,
    live_queue_reset_local_time = null,
    live_queue_reset_interval_minutes = null,
    live_queue_max_participants = null,
    updated_at = current_timestamp
where reservation_mode = 'PLANNED_BOOKING';

alter table rooms drop constraint if exists chk_rooms_published_live_queue_ready;

alter table rooms
    add constraint chk_rooms_live_queue_reset_ready
        check (
            reservation_mode <> 'LIVE_QUEUE'
            or (
                live_queue_reset_policy = 'DAILY_AT_TIME'
                and live_queue_reset_local_time is not null
                and live_queue_reset_interval_minutes is null
            )
            or (
                live_queue_reset_policy = 'EVERY_INTERVAL'
                and live_queue_reset_local_time is null
                and live_queue_reset_interval_minutes is not null
                and live_queue_reset_interval_minutes > 0
            )
        );
