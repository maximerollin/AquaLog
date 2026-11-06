begin;

create extension if not exists pgtap with schema extensions;
select plan(14);

insert into auth.users (id, aud, role, email, encrypted_password, created_at, updated_at)
values
  ('10000000-0000-0000-0000-000000000081', 'authenticated', 'authenticated', 'inhabitants-a@example.com', '', now(), now()),
  ('10000000-0000-0000-0000-000000000082', 'authenticated', 'authenticated', 'inhabitants-b@example.com', '', now(), now());

select has_table('public', 'inhabitants', 'inhabitants are stored');

set local role authenticated;
set local request.jwt.claims = '{"sub":"10000000-0000-0000-0000-000000000081","role":"authenticated"}';

select public.apply_sync_operation(
    'inhabitant-aquarium', 'aquarium', '81000000-0000-0000-0000-000000000001', 'upsert', 1, 0,
    '{"id":"81000000-0000-0000-0000-000000000001","name":"Salon","volume":120,"volume_unit":"liters","created_at_epoch_millis":1,"profile":"established","is_archived":false}'::jsonb
);
select public.apply_sync_operation(
    'inhabitant-add', 'inhabitant', '82000000-0000-0000-0000-000000000001', 'upsert', 2, 0,
    '{"id":"82000000-0000-0000-0000-000000000001","aquarium_id":"81000000-0000-0000-0000-000000000001","species_id":"neon","custom_name":null,"family":"TETRA","count":6,"added_at_epoch_millis":10,"removed_at_epoch_millis":null}'::jsonb
);
select results_eq(
  $$select species_id, custom_name, family, count, added_at_epoch_millis, removed_at_epoch_millis, server_version
    from public.inhabitants where id = '82000000-0000-0000-0000-000000000001'$$,
  $$values ('neon'::text, null::text, 'TETRA'::text, 6, 10::bigint, null::bigint, 1::bigint)$$,
  'an upsert through the client entry point stores the inhabitant'
);

select public.apply_sync_operation(
    'inhabitant-split', 'inhabitant', '82000000-0000-0000-0000-000000000001', 'upsert', 3, 1,
    '{"id":"82000000-0000-0000-0000-000000000001","aquarium_id":"81000000-0000-0000-0000-000000000001","species_id":"neon","custom_name":null,"family":"TETRA","count":4,"added_at_epoch_millis":10,"removed_at_epoch_millis":null}'::jsonb
);
select results_eq(
  $$select count, server_version from public.inhabitants where id = '82000000-0000-0000-0000-000000000001'$$,
  $$values (4, 2::bigint)$$,
  'the later server-accepted update wins (server-ordered LWW)'
);

select public.apply_sync_operation(
    'inhabitant-add', 'inhabitant', '82000000-0000-0000-0000-000000000001', 'upsert', 2, 0,
    '{"id":"82000000-0000-0000-0000-000000000001","aquarium_id":"81000000-0000-0000-0000-000000000001","species_id":"neon","custom_name":null,"family":"TETRA","count":6,"added_at_epoch_millis":10,"removed_at_epoch_millis":null}'::jsonb
);
select results_eq(
  $$select count, server_version from public.inhabitants where id = '82000000-0000-0000-0000-000000000001'$$,
  $$values (4, 2::bigint)$$,
  'a replayed operation is ignored by its receipt'
);

select public.apply_sync_operation(
    'inhabitant-removed-row', 'inhabitant', '82000000-0000-0000-0000-000000000002', 'upsert', 3, 0,
    '{"id":"82000000-0000-0000-0000-000000000002","aquarium_id":"81000000-0000-0000-0000-000000000001","species_id":null,"custom_name":"Maison","family":"CICHLID","count":2,"added_at_epoch_millis":10,"removed_at_epoch_millis":20}'::jsonb
);
select public.apply_sync_operation(
    'inhabitant-delete', 'inhabitant', '82000000-0000-0000-0000-000000000002', 'delete', 4, 1, null
);
select isnt(
  (select deleted_at_epoch_millis from public.inhabitants where id = '82000000-0000-0000-0000-000000000002'),
  null::bigint,
  'a delete leaves a tombstone'
);

reset role;
select results_eq(
  $$select entity_type, assigned_server_version, conflicted from public.sync_operation_receipts
    where account_id = '10000000-0000-0000-0000-000000000081' and operation_key = 'inhabitant-split'$$,
  $$values ('inhabitant'::text, 2::bigint, false)$$,
  'the receipt records the inhabitant and its assigned version'
);
set local role authenticated;

set local request.jwt.claims = '{"sub":"10000000-0000-0000-0000-000000000082","role":"authenticated"}';
select is(
  (select count(*)::integer from public.inhabitants),
  0,
  'another account cannot select the inhabitants'
);
select throws_ok(
  $$insert into public.inhabitants (id, account_id, aquarium_id, family, count, added_at_epoch_millis)
    values ('82000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000081',
            '81000000-0000-0000-0000-000000000001', 'TETRA', 1, 1)$$,
  '42501',
  null,
  'no account can insert directly'
);
select throws_ok(
  $$select public.apply_sync_operation(
      'other-overwrite', 'inhabitant', '82000000-0000-0000-0000-000000000001', 'upsert', 5, 0,
      '{"id":"82000000-0000-0000-0000-000000000001","aquarium_id":"81000000-0000-0000-0000-000000000001","species_id":"neon","family":"TETRA","count":1,"added_at_epoch_millis":10}'::jsonb
  )$$,
  '42501',
  'entity UUID belongs to another account',
  'another account cannot overwrite an inhabitant'
);
select throws_ok(
  $$select public.apply_sync_operation(
      'other-into-foreign-aquarium', 'inhabitant', '82000000-0000-0000-0000-000000000004', 'upsert', 5, 0,
      '{"id":"82000000-0000-0000-0000-000000000004","aquarium_id":"81000000-0000-0000-0000-000000000001","species_id":"neon","family":"TETRA","count":1,"added_at_epoch_millis":10}'::jsonb
  )$$,
  '23503',
  null,
  'another account cannot add an inhabitant to a foreign Aquarium'
);
select throws_ok(
  $$select public.apply_sync_operation(
      'zero-count', 'inhabitant', '82000000-0000-0000-0000-000000000005', 'upsert', 5, 0,
      '{"id":"82000000-0000-0000-0000-000000000005","aquarium_id":"81000000-0000-0000-0000-000000000001","species_id":"neon","family":"TETRA","count":0,"added_at_epoch_millis":10}'::jsonb
  )$$,
  '23514',
  null,
  'a group counts at least one'
);
select throws_ok(
  $$select public.apply_sync_operation(
      'blank-custom-name', 'inhabitant', '82000000-0000-0000-0000-000000000006', 'upsert', 5, 0,
      '{"id":"82000000-0000-0000-0000-000000000006","aquarium_id":"81000000-0000-0000-0000-000000000001","species_id":null,"custom_name":"  ","family":"TETRA","count":1,"added_at_epoch_millis":10}'::jsonb
  )$$,
  '23514',
  null,
  'an inhabitant names a species or a non-blank custom name'
);

reset role;
delete from auth.users where id = '10000000-0000-0000-0000-000000000081';
select is(
  (select count(*)::integer from public.inhabitants where account_id = '10000000-0000-0000-0000-000000000081'),
  0,
  'deleting the auth user removes the inhabitants'
);
select is(
  (select count(*)::integer from public.sync_operation_receipts where account_id = '10000000-0000-0000-0000-000000000081'),
  0,
  'and the receipts'
);

select * from finish();
rollback;
