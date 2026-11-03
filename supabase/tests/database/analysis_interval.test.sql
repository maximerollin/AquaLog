begin;

create extension if not exists pgtap with schema extensions;
select plan(8);

insert into auth.users (id, aud, role, email, encrypted_password, created_at, updated_at)
values ('10000000-0000-0000-0000-000000000061', 'authenticated', 'authenticated', 'rhythm@example.com', '', now(), now());

select has_column('public', 'parameter_definitions', 'analysis_interval_days', 'Parameters store an analysis interval');

set local role authenticated;
set local request.jwt.claims = '{"sub":"10000000-0000-0000-0000-000000000061","role":"authenticated"}';

select public.apply_sync_operation(
    'rhythm-aquarium', 'aquarium', '61000000-0000-0000-0000-000000000001', 'upsert', 1, 0,
    '{"id":"61000000-0000-0000-0000-000000000001","name":"Salon","volume":120,"volume_unit":"liters","created_at_epoch_millis":1,"profile":"established","is_archived":false}'::jsonb
);
select lives_ok(
  $$select public.apply_sync_operation(
    'rhythm-parameter-with', 'parameter_definition', '62000000-0000-0000-0000-000000000001', 'upsert', 2, 0,
    '{"id":"62000000-0000-0000-0000-000000000001","aquarium_id":"61000000-0000-0000-0000-000000000001","parameter":"ph","is_active":true,"position":0,"unit":"pH","precision":1,"indicative_minimum":6.5,"indicative_maximum":7.5,"analysis_interval_days":10}'::jsonb
  )$$,
  'an upsert can carry the analysis interval'
);
select is(
  (select analysis_interval_days from public.parameter_definitions where id = '62000000-0000-0000-0000-000000000001'),
  10,
  'the analysis interval is synchronized'
);
select lives_ok(
  $$select public.apply_sync_operation(
    'rhythm-parameter-without', 'parameter_definition', '62000000-0000-0000-0000-000000000001', 'upsert', 3, 1,
    '{"id":"62000000-0000-0000-0000-000000000001","aquarium_id":"61000000-0000-0000-0000-000000000001","parameter":"ph","is_active":true,"position":0,"unit":"pH","precision":1,"indicative_minimum":6.5,"indicative_maximum":7.5}'::jsonb
  )$$,
  'an older client upsert without the field stays valid'
);
select is(
  (select analysis_interval_days from public.parameter_definitions where id = '62000000-0000-0000-0000-000000000001'),
  10,
  'a payload without the field keeps the stored value'
);
select lives_ok(
  $$select public.apply_sync_operation(
    'rhythm-parameter-null', 'parameter_definition', '62000000-0000-0000-0000-000000000001', 'upsert', 4, 2,
    '{"id":"62000000-0000-0000-0000-000000000001","aquarium_id":"61000000-0000-0000-0000-000000000001","parameter":"ph","is_active":true,"position":0,"unit":"pH","precision":1,"indicative_minimum":6.5,"indicative_maximum":7.5,"analysis_interval_days":null}'::jsonb
  )$$,
  'an upsert can reset the analysis interval'
);
select is(
  (select analysis_interval_days from public.parameter_definitions where id = '62000000-0000-0000-0000-000000000001'),
  null::integer,
  'an explicit null resets to the profile default'
);
select throws_ok(
  $$select public.apply_sync_operation(
    'rhythm-parameter-zero', 'parameter_definition', '62000000-0000-0000-0000-000000000001', 'upsert', 5, 3,
    '{"id":"62000000-0000-0000-0000-000000000001","aquarium_id":"61000000-0000-0000-0000-000000000001","parameter":"ph","is_active":true,"position":0,"unit":"pH","precision":1,"indicative_minimum":6.5,"indicative_maximum":7.5,"analysis_interval_days":0}'::jsonb
  )$$,
  '23514',
  null,
  'a non positive analysis interval is rejected'
);

select * from finish();
rollback;
