alter table rooms drop constraint if exists rooms_individual_workspace_id_key;
alter table rooms drop constraint if exists constraint_4a8;

create index if not exists idx_rooms_individual_workspace_status
    on rooms (individual_workspace_id, status);
