alter table rooms drop constraint if exists fk_rooms_individual_workspace;

drop index if exists constraint_4a8_index_5;
drop index if exists rooms_individual_workspace_id_key;

alter table rooms add constraint fk_rooms_individual_workspace
    foreign key (individual_workspace_id) references individual_workspaces (id);

create index if not exists idx_rooms_individual_workspace_status
    on rooms (individual_workspace_id, status);
