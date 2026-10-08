begin;

create extension if not exists pgtap with schema extensions;
select plan(17);

insert into auth.users (id, aud, role, email, encrypted_password, created_at, updated_at)
values (
    '10000000-0000-0000-0000-000000000001',
    'authenticated',
    'authenticated',
    'owner@example.com',
    '',
    now(),
    now()
);

set local role authenticated;
set local request.jwt.claims = '{"sub":"10000000-0000-0000-0000-000000000001","role":"authenticated"}';

select lives_ok(
    $$select public.migrate_initial_copy($json$
    {
      "accountId":"10000000-0000-0000-0000-000000000001",
      "aquariums":[{"id":"20000000-0000-0000-0000-000000000001","name":"Amazonien","volume":120.0,"volumeUnit":"liters","createdAtEpochMillis":1700000000000,"profile":"established"}],
      "parameterDefinitions":[{"id":"30000000-0000-0000-0000-000000000001","aquariumId":"20000000-0000-0000-0000-000000000001","parameter":"temperature","isActive":true,"position":0,"unit":"°C","precision":1,"indicativeMinimum":23.0,"indicativeMaximum":27.0}],
      "sessions":[{"id":"40000000-0000-0000-0000-000000000001","aquariumId":"20000000-0000-0000-0000-000000000001","occurredAtEpochMillis":1700000100000,"createdAtEpochMillis":1700000101000}],
      "measurements":[{"id":"50000000-0000-0000-0000-000000000001","sessionId":"40000000-0000-0000-0000-000000000001","parameterDefinitionId":"30000000-0000-0000-0000-000000000001","value":24.5}],
      "maintenanceActions":[{"id":"60000000-0000-0000-0000-000000000001","sessionId":"40000000-0000-0000-0000-000000000001","type":"water_change","quantity":20.0,"unit":"%","product":null}],
      "events":[{"id":"70000000-0000-0000-0000-000000000001","sessionId":"40000000-0000-0000-0000-000000000001","type":"observation","note":"Clear water"}]
    }
    $json$::jsonb)$$,
    'the authenticated owner can migrate one complete local copy'
);

select is((select count(*) from public.aquariums), 1::bigint, 'one Aquarium is stored');
select is((select count(*) from public.parameter_definitions), 1::bigint, 'one Parameter is stored');
select is((select count(*) from public.sessions), 1::bigint, 'one Session is stored');
select is((select count(*) from public.measurements), 1::bigint, 'one Measurement is stored');
select is((select count(*) from public.maintenance_actions), 1::bigint, 'one maintenance Action is stored');
select is((select count(*) from public.session_events), 1::bigint, 'one Event is stored');
select is(
    (select account_id from public.aquariums where id = '20000000-0000-0000-0000-000000000001'),
    '10000000-0000-0000-0000-000000000001'::uuid,
    'the server attaches the copy to auth.uid()'
);

select lives_ok(
    $$select public.migrate_initial_copy($json$
    {
      "accountId":"10000000-0000-0000-0000-000000000001",
      "aquariums":[{"id":"20000000-0000-0000-0000-000000000001","name":"Amazonien","volume":120.0,"volumeUnit":"liters","createdAtEpochMillis":1700000000000,"profile":"established"}],
      "parameterDefinitions":[{"id":"30000000-0000-0000-0000-000000000001","aquariumId":"20000000-0000-0000-0000-000000000001","parameter":"temperature","isActive":true,"position":0,"unit":"°C","precision":1,"indicativeMinimum":23.0,"indicativeMaximum":27.0}],
      "sessions":[{"id":"40000000-0000-0000-0000-000000000001","aquariumId":"20000000-0000-0000-0000-000000000001","occurredAtEpochMillis":1700000100000,"createdAtEpochMillis":1700000101000}],
      "measurements":[{"id":"50000000-0000-0000-0000-000000000001","sessionId":"40000000-0000-0000-0000-000000000001","parameterDefinitionId":"30000000-0000-0000-0000-000000000001","value":24.5}],
      "maintenanceActions":[{"id":"60000000-0000-0000-0000-000000000001","sessionId":"40000000-0000-0000-0000-000000000001","type":"water_change","quantity":20.0,"unit":"%","product":null}],
      "events":[{"id":"70000000-0000-0000-0000-000000000001","sessionId":"40000000-0000-0000-0000-000000000001","type":"observation","note":"Clear water"}]
    }
    $json$::jsonb)$$,
    'replaying the same local copy succeeds'
);

select is((select count(*) from public.aquariums), 1::bigint, 'replay does not duplicate the Aquarium');
select is((select count(*) from public.sessions), 1::bigint, 'replay does not duplicate the Session');
select is((select count(*) from public.measurements), 1::bigint, 'replay does not duplicate the Measurement');

select throws_ok(
    $$select public.migrate_initial_copy('{"accountId":"10000000-0000-0000-0000-000000000099","aquariums":[],"parameterDefinitions":[],"sessions":[],"measurements":[],"maintenanceActions":[],"events":[]}'::jsonb)$$,
    '42501',
    'accountId must match the authenticated user',
    'a client cannot attach data to another account'
);

select throws_ok(
    $$select public.migrate_initial_copy($json$
    {
      "accountId":"10000000-0000-0000-0000-000000000001",
      "aquariums":[{"id":"20000000-0000-0000-0000-000000000099","name":"Atomic","volume":10.0,"volumeUnit":"liters","createdAtEpochMillis":1700000000000,"profile":"established"}],
      "parameterDefinitions":[],
      "sessions":[{"id":"40000000-0000-0000-0000-000000000099","aquariumId":"20000000-0000-0000-0000-000000000098","occurredAtEpochMillis":1700000100000,"createdAtEpochMillis":1700000101000}],
      "measurements":[],"maintenanceActions":[],"events":[]
    }
    $json$::jsonb)$$,
    '23503',
    null,
    'an invalid child rejects the complete copy atomically'
);
select is(
    (select count(*) from public.aquariums where id = '20000000-0000-0000-0000-000000000099'),
    0::bigint,
    'the failed atomic copy leaves no parent row'
);

select is(
    (select id from public.sessions where id = '40000000-0000-0000-0000-000000000001'),
    '40000000-0000-0000-0000-000000000001'::uuid,
    'the original local Session UUID is preserved'
);

reset role;
insert into auth.users (id, aud, role, email, encrypted_password, created_at, updated_at)
values (
    '10000000-0000-0000-0000-000000000002',
    'authenticated',
    'authenticated',
    'other@example.com',
    '',
    now(),
    now()
);
set local role authenticated;
set local request.jwt.claims = '{"sub":"10000000-0000-0000-0000-000000000002","role":"authenticated"}';
select throws_ok(
    $$select public.migrate_initial_copy($json$
    {
      "accountId":"10000000-0000-0000-0000-000000000002",
      "aquariums":[{"id":"20000000-0000-0000-0000-000000000001","name":"Stolen UUID","volume":10.0,"volumeUnit":"liters","createdAtEpochMillis":1700000000000,"profile":"established"}],
      "parameterDefinitions":[],"sessions":[],"measurements":[],"maintenanceActions":[],"events":[]
    }
    $json$::jsonb)$$,
    '42501',
    'a local UUID already belongs to another account',
    'a second account cannot claim an existing local UUID'
);

select * from finish();
rollback;
