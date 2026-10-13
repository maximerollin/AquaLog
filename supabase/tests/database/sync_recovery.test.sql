begin;

create extension if not exists pgtap with schema extensions;
select plan(15);

insert into auth.users (id, aud, role, email, encrypted_password, created_at, updated_at)
values
    ('12000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'recovery-owner@example.com', '', now(), now()),
    ('12000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'recovery-other@example.com', '', now(), now());

set local role authenticated;
set local request.jwt.claims = '{"sub":"12000000-0000-0000-0000-000000000001","role":"authenticated"}';

select lives_ok(
    $$select public.apply_sync_operation(
        p_operation_key => 'zzz-device-a-future-clock',
        p_entity_type => 'aquarium',
        p_entity_id => '22000000-0000-0000-0000-000000000001',
        p_kind => 'upsert',
        p_client_updated_at_epoch_millis => 9999999999999,
        p_base_server_version => 0,
        p_payload => '{"id":"22000000-0000-0000-0000-000000000001","name":"Future clock first","volume":120,"volume_unit":"liters","created_at_epoch_millis":100,"profile":"established"}'::jsonb
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
        p_operation_key => 'aaa-device-b-normal-clock',
        p_entity_type => 'aquarium',
        p_entity_id => '22000000-0000-0000-0000-000000000001',
        p_kind => 'upsert',
        p_client_updated_at_epoch_millis => 100,
        p_base_server_version => 0,
        p_payload => '{"id":"22000000-0000-0000-0000-000000000001","name":"Accepted second","volume":180,"volume_unit":"liters","created_at_epoch_millis":100,"profile":"established"}'::jsonb
    )$$,
    'a concurrent edit with an older client clock is accepted second'
);
select is(
    (select name from public.aquariums where id = '22000000-0000-0000-0000-000000000001'),
    'Accepted second',
    'server acceptance order wins even when the first client clock is far in the future'
);
select is(
    (select server_version from public.aquariums where id = '22000000-0000-0000-0000-000000000001'),
    2::bigint,
    'the server atomically assigns the next version to the accepted conflict'
);
reset role;
select ok(
    (select conflicted from public.sync_operation_receipts
     where account_id = '12000000-0000-0000-0000-000000000001'
       and operation_key = 'aaa-device-b-normal-clock'),
    'the receipt records that the operation was based on an older version'
);
set local role authenticated;
set local request.jwt.claims = '{"sub":"12000000-0000-0000-0000-000000000001","role":"authenticated"}';

select lives_ok(
    $$select public.apply_sync_operation(
        p_operation_key => '000-device-a-same-millis',
        p_entity_type => 'aquarium',
        p_entity_id => '22000000-0000-0000-0000-000000000001',
        p_kind => 'upsert',
        p_client_updated_at_epoch_millis => 100,
        p_base_server_version => 2,
        p_payload => '{"id":"22000000-0000-0000-0000-000000000001","name":"Same millisecond accepted last","volume":80,"volume_unit":"liters","created_at_epoch_millis":100,"profile":"established"}'::jsonb
    )$$,
    'a same-millisecond edit with a lexically smaller operation key is accepted last'
);
select is(
    (select name from public.aquariums where id = '22000000-0000-0000-0000-000000000001'),
    'Same millisecond accepted last',
    'operation keys do not determine chronology'
);
select is(
    (select server_version from public.aquariums where id = '22000000-0000-0000-0000-000000000001'),
    3::bigint,
    'same-millisecond writes still receive distinct server versions'
);

select lives_ok(
    $$select public.apply_sync_operation(
        p_operation_key => '000-device-a-same-millis',
        p_entity_type => 'aquarium',
        p_entity_id => '22000000-0000-0000-0000-000000000001',
        p_kind => 'upsert',
        p_client_updated_at_epoch_millis => 1,
        p_base_server_version => 0,
        p_payload => '{"id":"22000000-0000-0000-0000-000000000001","name":"Duplicated","volume":1,"volume_unit":"liters","created_at_epoch_millis":100,"profile":"established"}'::jsonb
    )$$,
    'an ambiguous response can replay the same operation key'
);
select is(
    (select server_version from public.aquariums where id = '22000000-0000-0000-0000-000000000001'),
    3::bigint,
    'an idempotent replay does not apply twice'
);
reset role;
select is(
    (select assigned_server_version from public.sync_operation_receipts
     where account_id = '12000000-0000-0000-0000-000000000001'
       and operation_key = '000-device-a-same-millis'),
    3::bigint,
    'the replay keeps the originally assigned server version'
);
set local role authenticated;
set local request.jwt.claims = '{"sub":"12000000-0000-0000-0000-000000000001","role":"authenticated"}';

select lives_ok(
    $$select public.apply_sync_operation(
        p_operation_key => 'device-a-delete',
        p_entity_type => 'aquarium',
        p_entity_id => '22000000-0000-0000-0000-000000000001',
        p_kind => 'delete',
        p_client_updated_at_epoch_millis => 1,
        p_base_server_version => 3,
        p_payload => null
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
