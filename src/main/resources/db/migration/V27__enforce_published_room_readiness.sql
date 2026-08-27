update rooms room
set status = 'INACTIVE',
    visibility = 'UNLISTED',
    updated_at = current_timestamp
where room.status = 'PUBLISHED'
  and (
      not exists (
          select 1
          from room_assignments assignment
          where assignment.room_id = room.id
            and assignment.status = 'ACTIVE'
      )
      or not exists (
          select 1
          from weekly_availability_rules availability
          where availability.room_id = room.id
            and availability.active = true
      )
      or (
          room.reservation_mode = 'LIVE_QUEUE'
          and (
              room.live_queue_reset_policy is null
              or (
                  room.live_queue_reset_policy = 'DAILY_AT_TIME'
                  and room.live_queue_reset_local_time is null
              )
              or (
                  room.live_queue_reset_policy = 'EVERY_INTERVAL'
                  and (
                      room.live_queue_reset_interval_minutes is null
                      or room.live_queue_reset_interval_minutes <= 0
                  )
              )
          )
      )
  );

alter table rooms
    add constraint chk_rooms_published_live_queue_ready
        check (
            status <> 'PUBLISHED'
            or reservation_mode <> 'LIVE_QUEUE'
            or (
                live_queue_reset_policy = 'DAILY_AT_TIME'
                and live_queue_reset_local_time is not null
            )
            or (
                live_queue_reset_policy = 'EVERY_INTERVAL'
                and live_queue_reset_interval_minutes is not null
                and live_queue_reset_interval_minutes > 0
            )
        );
