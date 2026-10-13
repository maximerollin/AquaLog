begin;

create extension if not exists pgtap with schema extensions;
select plan(11);

insert into auth.users (id, aud, role, email, encrypted_password, created_at, updated_at)
values
    ('12000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'recovery-owner@example.com', '', now(), now()),
    ('12000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'recovery-other@example.com', '', now(), now());

set local role authenticated;
set local request.jwt.claims = '{"sub":"12000000-0000-0000-0000-000000000001","role":"authenticated"}';

select lives_ok(
    $$select public.apply_sync_operation(
        'device-a-create', 'aquarium', '22000000-0000-0000-0000-000000000001', 'upsert', 100,
        '{"id":"22000000-0000-0000-0000-000000000001","name":"Initial","volume":120,"volume_unit":"liters","created_at_epoch_millis":100,"profile":"established"}'::jsonb
    )$$,
    'owner creates an entity through the idempotent RPC'
);
select is(
    (select server_version from public.aquariums where id = '22000000-0000-0000-0000-000000000001'),
    1::bigint,
    'server assigns the first entity version'
);

select lives_ok(
    $$select public.apply_sync_operation(
        'device-b-newer', 'aquarium', '22000000-0000-0000-0000-000000000001', 'upsert', 300,
        '{"id":"22000000-0000-0000-0000-000000000001","name":"Newer","volume":180,"volume_unit":"liters","created_at_epoch_millis":100,"profile":"established"}'::jsonb
    )$$,
    'a newer concurrent edit is accepted'
);
select lives_ok(
    $$select public.apply_sync_operation(
        'device-a-older', 'aquarium', '22000000-0000-0000-0000-000000000001', 'upsert', 200,
        '{"id":"22000000-0000-0000-0000-000000000001","name":"Older","volume":80,"volume_unit":"liters","created_at_epoch_millis":100,"profile":"established"}'::jsonb
    )$$,
    'an older concurrent edit is acknowledged safely'
);
select is(
    (select name from public.aquariums where id = '22000000-0000-0000-0000-000000000001'),
    'Newer',
    'last writer wins per entity according to the accepted server metadata'
);
select is(
    (select server_version from public.aquariums where id = '22000000-0000-0000-0000-000000000001'),
    2::bigint,
    'a stale operation does not advance the server version'
);

select lives_ok(
    $$select public.apply_sync_operation(
        'device-b-newer', 'aquarium', '22000000-0000-0000-0000-000000000001', 'upsert', 300,
        '{"id":"22000000-0000-0000-0000-000000000001","name":"Duplicated","volume":1,"volume_unit":"liters","created_at_epoch_millis":100,"profile":"established"}'::jsonb
    )$$,
    'an ambiguous response can replay the same operation key'
);
select is(
    (select server_version from public.aquariums where id = '22000000-0000-0000-0000-000000000001'),
    2::bigint,
    'an idempotent replay does not apply twice'
);

select lives_ok(
    $$select public.apply_sync_operation(
        'device-a-delete', 'aquarium', '22000000-0000-0000-0000-000000000001', 'delete', 400, null
    )$$,
    'a newer deletion becomes a tombstone'
);
select ok(
    (select deleted_at_epoch_millis is not null from public.aquariums where id = '22000000-0000-0000-0000-000000000001'),
    'the tombstone remains available for another device to acknowledge'
);

set local request.jwt.claims = '{"sub":"12000000-0000-0000-0000-000000000002","role":"authenticated"}';
select is(
    (select count(*) from public.aquariums),
    0::bigint,
    'another account cannot read live entities or tombstones'
);

select * from finish();
rollback;
