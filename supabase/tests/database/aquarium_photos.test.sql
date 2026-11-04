begin;

create extension if not exists pgtap with schema extensions;
select plan(8);

insert into auth.users (id, aud, role, email, encrypted_password, created_at, updated_at)
values
  ('10000000-0000-0000-0000-000000000071', 'authenticated', 'authenticated', 'photo-a@example.com', '', now(), now()),
  ('10000000-0000-0000-0000-000000000072', 'authenticated', 'authenticated', 'photo-b@example.com', '', now(), now());

select has_column('public', 'aquariums', 'photo_id', 'Aquariums store a photo id');
select is(
  (select public from storage.buckets where id = 'aquarium-photos'),
  false,
  'the aquarium-photos bucket exists and is private'
);

set local role authenticated;
set local request.jwt.claims = '{"sub":"10000000-0000-0000-0000-000000000071","role":"authenticated"}';

select public.apply_sync_operation(
    'photo-aquarium-with', 'aquarium', '71000000-0000-0000-0000-000000000001', 'upsert', 1, 0,
    '{"id":"71000000-0000-0000-0000-000000000001","name":"Salon","volume":120,"volume_unit":"liters","created_at_epoch_millis":1,"profile":"established","is_archived":false,"photo_id":"72000000-0000-0000-0000-000000000001"}'::jsonb
);
select is(
  (select photo_id from public.aquariums where id = '71000000-0000-0000-0000-000000000001'),
  '72000000-0000-0000-0000-000000000001'::uuid,
  'an upsert carrying photo_id stores it'
);
select public.apply_sync_operation(
    'photo-aquarium-without', 'aquarium', '71000000-0000-0000-0000-000000000001', 'upsert', 2, 1,
    '{"id":"71000000-0000-0000-0000-000000000001","name":"Salon","volume":120,"volume_unit":"liters","created_at_epoch_millis":1,"profile":"established","is_archived":false}'::jsonb
);
select is(
  (select photo_id from public.aquariums where id = '71000000-0000-0000-0000-000000000001'),
  '72000000-0000-0000-0000-000000000001'::uuid,
  'an upsert without the key keeps the stored photo_id'
);
select public.apply_sync_operation(
    'photo-aquarium-null', 'aquarium', '71000000-0000-0000-0000-000000000001', 'upsert', 3, 2,
    '{"id":"71000000-0000-0000-0000-000000000001","name":"Salon","volume":120,"volume_unit":"liters","created_at_epoch_millis":1,"profile":"established","is_archived":false,"photo_id":null}'::jsonb
);
select is(
  (select photo_id from public.aquariums where id = '71000000-0000-0000-0000-000000000001'),
  null::uuid,
  'an explicit null clears the photo_id'
);

select lives_ok(
  $$insert into storage.objects (bucket_id, name, owner_id)
    values ('aquarium-photos', '10000000-0000-0000-0000-000000000071/x.jpg', '10000000-0000-0000-0000-000000000071')$$,
  'an account can store an object under its own folder'
);
select throws_ok(
  $$insert into storage.objects (bucket_id, name, owner_id)
    values ('aquarium-photos', '10000000-0000-0000-0000-000000000072/x.jpg', '10000000-0000-0000-0000-000000000071')$$,
  '42501',
  null,
  'an account cannot store an object under another account folder'
);

set local request.jwt.claims = '{"sub":"10000000-0000-0000-0000-000000000072","role":"authenticated"}';
select is(
  (select count(*)::integer from storage.objects where bucket_id = 'aquarium-photos'),
  0,
  'another account cannot see the object'
);

select * from finish();
rollback;
