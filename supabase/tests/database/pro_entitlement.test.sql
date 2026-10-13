begin;

create extension if not exists pgtap with schema extensions;
select plan(11);

insert into auth.users (id, aud, role, email, encrypted_password, created_at, updated_at)
values
  ('10000000-0000-0000-0000-000000000011', 'authenticated', 'authenticated', 'pro@example.com', '', now(), now()),
  ('10000000-0000-0000-0000-000000000012', 'authenticated', 'authenticated', 'free@example.com', '', now(), now());

set local role service_role;
select lives_ok(
  $$select public.claim_verified_pro_purchase(
    '10000000-0000-0000-0000-000000000011',
    'aqualog_pro_annual',
    'sha256-token',
    '2027-10-21T12:00:00Z',
    'GPA.1234'
  )$$,
  'the server can claim a Google-bound Play purchase'
);
select lives_ok(
  $$select public.claim_verified_pro_purchase(
    '10000000-0000-0000-0000-000000000011',
    'aqualog_pro_annual',
    'sha256-token',
    '2027-10-21T12:00:00Z',
    'GPA.1234'
  )$$,
  'replaying the same claim for its owner is idempotent'
);
select throws_ok(
  $$select public.claim_verified_pro_purchase(
    '10000000-0000-0000-0000-000000000012',
    'aqualog_pro_annual',
    'sha256-token',
    '2027-10-21T12:00:00Z',
    'GPA.1234'
  )$$,
  '42501',
  'purchase token belongs to another account',
  'another account cannot steal an existing claim'
);
reset role;
select is((select count(*) from public.pro_entitlements), 0::bigint, 'a claim alone grants no entitlement');
set local role service_role;
select lives_ok(
  $$select public.activate_claimed_pro_purchase(
    '10000000-0000-0000-0000-000000000011',
    'aqualog_pro_annual',
    'sha256-token',
    '2027-10-21T12:00:00Z',
    'GPA.1234'
  )$$,
  'the owner activates Pro only after Google acknowledgement'
);
reset role;
select is((select count(*) from public.play_purchase_verifications), 1::bigint, 'one verification row remains');
select is((select count(*) from public.pro_entitlements), 1::bigint, 'one entitlement row remains');

set local role authenticated;
set local request.jwt.claims = '{"sub":"10000000-0000-0000-0000-000000000011","role":"authenticated"}';
select is((select product_id from public.pro_entitlements), 'aqualog_pro_annual', 'owner can read the entitlement');
select throws_ok(
  $$select * from public.play_purchase_verifications$$,
  '42501',
  null,
  'the client cannot read server-side purchase verification records'
);
select throws_ok(
  $$insert into public.pro_entitlements(account_id, product_id, status, valid_until) values (
    '10000000-0000-0000-0000-000000000011', 'aqualog_pro_annual', 'active', now()
  )$$,
  '42501',
  null,
  'the client cannot mint an entitlement'
);

set local request.jwt.claims = '{"sub":"10000000-0000-0000-0000-000000000012","role":"authenticated"}';
select is((select count(*) from public.pro_entitlements), 0::bigint, 'another account cannot read the entitlement');

select * from finish();
rollback;
