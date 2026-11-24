begin;

create extension if not exists pgtap with schema extensions;
select plan(13);

insert into auth.users (id, aud, role, email, encrypted_password, created_at, updated_at)
values
  ('10000000-0000-0000-0000-000000000091', 'authenticated', 'authenticated', 'tasks-a@example.com', '', now(), now()),
  ('10000000-0000-0000-0000-000000000092', 'authenticated', 'authenticated', 'tasks-b@example.com', '', now(), now());

select has_table('public', 'tasks', 'tasks are stored');
select has_table('public', 'task_occurrences', 'task occurrences are stored');

set local role authenticated;
set local request.jwt.claims = '{"sub":"10000000-0000-0000-0000-000000000091","role":"authenticated"}';

select public.apply_sync_operation(
    'task-aquarium', 'aquarium', '91000000-0000-0000-0000-000000000001', 'upsert', 1, 0,
    '{"id":"91000000-0000-0000-0000-000000000001","name":"Salon","volume":120,"volume_unit":"liters","created_at_epoch_millis":1,"profile":"established","is_archived":false}'::jsonb
);
select public.apply_sync_operation(
    'task-create', 'task', '92000000-0000-0000-0000-000000000001', 'upsert', 2, 0,
    '{"id":"92000000-0000-0000-0000-000000000001","aquarium_id":"91000000-0000-0000-0000-000000000001","title":"Water change","recurrence":"weekly","monthly_anchor_day":10,"maintenance_action_type":"water_change"}'::jsonb
);
select public.apply_sync_operation(
    'occurrence-create', 'task_occurrence', '93000000-0000-0000-0000-000000000001', 'upsert', 3, 0,
    '{"id":"93000000-0000-0000-0000-000000000001","task_id":"92000000-0000-0000-0000-000000000001","aquarium_id":"91000000-0000-0000-0000-000000000001","title":"Water change","due_year":2027,"due_month":1,"due_day":10,"minute_of_day":null,"time_zone_id":"Europe/Paris","resolution":null,"resolved_at_epoch_millis":null,"maintenance_action_type":"water_change"}'::jsonb
);
select results_eq(
  $$select title, recurrence, monthly_anchor_day, maintenance_action_type, server_version
    from public.tasks where id = '92000000-0000-0000-0000-000000000001'$$,
  $$values ('Water change'::text, 'weekly'::text, 10, 'water_change'::text, 1::bigint)$$,
  'an upsert through the client entry point stores the task'
);
select results_eq(
  $$select due_year, due_month, due_day, minute_of_day, time_zone_id, resolution, server_version
    from public.task_occurrences where id = '93000000-0000-0000-0000-000000000001'$$,
  $$values (2027, 1, 10, null::integer, 'Europe/Paris'::text, null::text, 1::bigint)$$,
  'and its pending occurrence, without a time'
);

select public.apply_sync_operation(
    'occurrence-complete', 'task_occurrence', '93000000-0000-0000-0000-000000000001', 'upsert', 4, 1,
    '{"id":"93000000-0000-0000-0000-000000000001","task_id":"92000000-0000-0000-0000-000000000001","aquarium_id":"91000000-0000-0000-0000-000000000001","title":"Water change","due_year":2027,"due_month":1,"due_day":10,"minute_of_day":null,"time_zone_id":"Europe/Paris","resolution":"completed","resolved_at_epoch_millis":1800000000000,"maintenance_action_type":"water_change"}'::jsonb
);
select results_eq(
  $$select resolution, resolved_at_epoch_millis, server_version
    from public.task_occurrences where id = '93000000-0000-0000-0000-000000000001'$$,
  $$values ('completed'::text, 1800000000000::bigint, 2::bigint)$$,
  'resolving the occurrence is a later accepted update'
);

select public.apply_sync_operation(
    'occurrence-create', 'task_occurrence', '93000000-0000-0000-0000-000000000001', 'upsert', 3, 0,
    '{"id":"93000000-0000-0000-0000-000000000001","task_id":"92000000-0000-0000-0000-000000000001","aquarium_id":"91000000-0000-0000-0000-000000000001","title":"Water change","due_year":2027,"due_month":1,"due_day":10,"minute_of_day":null,"time_zone_id":"Europe/Paris","resolution":null,"resolved_at_epoch_millis":null,"maintenance_action_type":"water_change"}'::jsonb
);
select is(
  (select resolution from public.task_occurrences where id = '93000000-0000-0000-0000-000000000001'),
  'completed',
  'a replayed operation is ignored by its receipt'
);

select public.apply_sync_operation(
    'occurrence-next', 'task_occurrence', '93000000-0000-0000-0000-000000000002', 'upsert', 5, 0,
    '{"id":"93000000-0000-0000-0000-000000000002","task_id":"92000000-0000-0000-0000-000000000001","aquarium_id":"91000000-0000-0000-0000-000000000001","title":"Water change","due_year":2027,"due_month":1,"due_day":17,"minute_of_day":1110,"time_zone_id":"Europe/Paris","resolution":null,"resolved_at_epoch_millis":null,"maintenance_action_type":"water_change"}'::jsonb
);
select public.apply_sync_operation(
    'occurrence-next-delete', 'task_occurrence', '93000000-0000-0000-0000-000000000002', 'delete', 6, 1, null
);
select isnt(
  (select deleted_at_epoch_millis from public.task_occurrences where id = '93000000-0000-0000-0000-000000000002'),
  null::bigint,
  'deleting the task leaves a tombstone on its pending occurrence'
);

reset role;
select results_eq(
  $$select entity_type, assigned_server_version, conflicted from public.sync_operation_receipts
    where account_id = '10000000-0000-0000-0000-000000000091' and operation_key = 'occurrence-complete'$$,
  $$values ('task_occurrence'::text, 2::bigint, false)$$,
  'the receipt records the occurrence and its assigned version'
);
set local role authenticated;

set local request.jwt.claims = '{"sub":"10000000-0000-0000-0000-000000000092","role":"authenticated"}';
select is(
  (select count(*)::integer from public.tasks) + (select count(*)::integer from public.task_occurrences),
  0,
  'another account cannot select the tasks or their occurrences'
);
select throws_ok(
  $$insert into public.tasks (id, account_id, aquarium_id, title, recurrence, monthly_anchor_day)
    values ('92000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000091',
            '91000000-0000-0000-0000-000000000001', 'Direct', 'once', 1)$$,
  '42501',
  null,
  'no account can insert directly'
);
select throws_ok(
  $$select public.apply_sync_operation(
      'other-overwrite', 'task', '92000000-0000-0000-0000-000000000001', 'upsert', 7, 0,
      '{"id":"92000000-0000-0000-0000-000000000001","aquarium_id":"91000000-0000-0000-0000-000000000001","title":"Stolen","recurrence":"once","monthly_anchor_day":1}'::jsonb
  )$$,
  '42501',
  'entity UUID belongs to another account',
  'another account cannot overwrite a task'
);
select throws_ok(
  $$select public.apply_sync_operation(
      'other-into-foreign-aquarium', 'task', '92000000-0000-0000-0000-000000000004', 'upsert', 7, 0,
      '{"id":"92000000-0000-0000-0000-000000000004","aquarium_id":"91000000-0000-0000-0000-000000000001","title":"Foreign","recurrence":"once","monthly_anchor_day":1}'::jsonb
  )$$,
  '23503',
  null,
  'another account cannot add a task to a foreign Aquarium'
);

reset role;
delete from auth.users where id = '10000000-0000-0000-0000-000000000091';
select is(
  (select count(*)::integer from public.tasks where account_id = '10000000-0000-0000-0000-000000000091')
    + (select count(*)::integer from public.task_occurrences where account_id = '10000000-0000-0000-0000-000000000091'),
  0,
  'deleting the auth user removes the tasks and their occurrences'
);

select * from finish();
rollback;
