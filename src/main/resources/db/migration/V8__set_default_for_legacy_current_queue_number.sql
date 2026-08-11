alter table queues
    alter column current_queue_number set default 0;

update queues
set current_queue_number = 0
where current_queue_number is null;
