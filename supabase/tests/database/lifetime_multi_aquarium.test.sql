begin;

create extension if not exists pgtap with schema extensions;
select plan(15);

insert into auth.users (id, aud, role, email, encrypted_password, created_at, updated_at)
values
  ('10000000-0000-0000-0000-000000000051', 'authenticated', 'authenticated', 'lifetime@example.com', '', now(), now()),
  ('10000000-0000-0000-0000-000000000052', 'authenticated', 'authenticated', 'other@example.com', '', now(), now());

set local role service_role;
select lives_ok(
  $$select public.claim_verified_pro_purchase(
    '10000000-0000-0000-0000-000000000051', 'aqualog_pro_lifetime',
    'lifetime-token-hash', 'encrypted-lifetime-token', null, 'GPA.LIFETIME'
  )$$,
  'the server can claim a non-expiring lifetime purchase'
);
select lives_ok(
  $$select public.claim_verified_pro_purchase(
    '10000000-0000-0000-0000-000000000051', 'aqualog_pro_lifetime',
    'lifetime-token-hash', 'encrypted-lifetime-token', null, 'GPA.LIFETIME'
  )$$,
  'replaying the lifetime claim for its owner is idempotent'
);
select throws_ok(
  $$select public.claim_verified_pro_purchase(
    '10000000-0000-0000-0000-000000000052', 'aqualog_pro_lifetime',
    'lifetime-token-hash', 'attacker-ciphertext', null, 'GPA.LIFETIME'
  )$$,
  '42501', 'purchase token belongs to another account',
  'another account cannot steal a lifetime purchase'
);
select lives_ok(
  $$select public.activate_claimed_pro_purchase(
    '10000000-0000-0000-0000-000000000051', 'aqualog_pro_lifetime',
    'lifetime-token-hash', null, 'GPA.LIFETIME'
  )$$,
  'the lifetime entitlement activates only after acknowledgement'
);
select is(
  (select product_id from public.pro_entitlements where account_id = '10000000-0000-0000-0000-000000000051'),
  'aqualog_pro_lifetime',
  'the server stores the lifetime product'
);
select is(
  (select valid_until from public.pro_entitlements where account_id = '10000000-0000-0000-0000-000000000051'),
  null::timestamptz,
  'lifetime entitlement has no artificial expiry'
);
reset role;

set local role authenticated;
set local request.jwt.claims = '{"sub":"10000000-0000-0000-0000-000000000051","role":"authenticated"}';
select is((select count(*) from public.pro_entitlements), 1::bigint, 'owner can read the lifetime entitlement');

select lives_ok(
  $$select public.apply_sync_operation(
    'ticket15-aquarium-one', 'aquarium', '51000000-0000-0000-0000-000000000001',
    'upsert', 1, 0,
    '{"id":"51000000-0000-0000-0000-000000000001","name":"Salon","volume":120,"volume_unit":"liters","created_at_epoch_millis":1,"profile":"established","is_archived":false}'::jsonb
  )$$,
  'owner can sync the first Aquarium'
);
select lives_ok(
  $$select public.apply_sync_operation(
    'ticket15-aquarium-two', 'aquarium', '51000000-0000-0000-0000-000000000002',
    'upsert', 2, 0,
    '{"id":"51000000-0000-0000-0000-000000000002","name":"Crevettes","volume":30,"volume_unit":"liters","created_at_epoch_millis":2,"profile":"planted_shrimp","is_archived":true}'::jsonb
  )$$,
  'owner can sync another archived Aquarium'
);
select is((select count(*) from public.aquariums), 2::bigint, 'both owned Aquariums remain stored');
select ok(
  (select is_archived from public.aquariums where id = '51000000-0000-0000-0000-000000000002'),
  'archive state is synchronized'
);

set local request.jwt.claims = '{"sub":"10000000-0000-0000-0000-000000000052","role":"authenticated"}';
select is((select count(*) from public.pro_entitlements), 0::bigint, 'another account cannot read the lifetime entitlement');
select is((select count(*) from public.aquariums), 0::bigint, 'another account cannot read either Aquarium');
select lives_ok(
  $$select public.apply_sync_operation(
    'ticket15-attacker-replay', 'aquarium', '51000000-0000-0000-0000-000000000002',
    'upsert', 3, 0,
    '{"id":"51000000-0000-0000-0000-000000000002","name":"Stolen","volume":1,"volume_unit":"liters","created_at_epoch_millis":2,"profile":"established","is_archived":false}'::jsonb
  )$$,
  'an ownership-conflicting sync remains non-mutating'
);

reset role;
select is(
  (select name from public.aquariums where id = '51000000-0000-0000-0000-000000000002'),
  'Crevettes',
  'ownership conflict cannot mutate another account Aquarium'
);

select * from finish();
rollback;
