begin;
create extension if not exists pgtap with schema extensions;
select plan(16);
insert into auth.users(id, aud, role, email, encrypted_password, created_at, updated_at)
values ('10000000-0000-0000-0000-000000000091', 'authenticated', 'authenticated', 'rc-a@example.invalid', '', now(), now()),
       ('10000000-0000-0000-0000-000000000092', 'authenticated', 'authenticated', 'rc-b@example.invalid', '', now(), now());
set local role service_role;
select lives_ok($$select public.register_revenuecat_customer('10000000-0000-0000-0000-000000000091', repeat('a',64))$$, 'server registers an opaque identity');
select lives_ok($$select public.register_revenuecat_customer('10000000-0000-0000-0000-000000000091', repeat('a',64))$$, 'registration is idempotent');
select throws_ok($$select public.register_revenuecat_customer('10000000-0000-0000-0000-000000000091', repeat('b',64))$$,
    '42501', 'RevenueCat identity cannot be replaced', 'secret rotation cannot silently orphan purchases');
select throws_ok($$select public.sync_revenuecat_entitlement('10000000-0000-0000-0000-000000000092', repeat('a',64), 'aqualog_pro_lifetime', null, now())$$,
    '42501', 'unregistered RevenueCat account', 'a customer cannot grant access to another account');
select is(public.sync_revenuecat_entitlement('10000000-0000-0000-0000-000000000091', repeat('a',64), 'aqualog_pro_annual', now()+interval '1 year', now()) ->> 'active', 'true', 'server annual snapshot grants Pro');
select is(public.sync_revenuecat_entitlement('10000000-0000-0000-0000-000000000091', repeat('a',64), null, null, now()+interval '1 second') ->> 'active', 'false', 'refund or expiry revokes Pro');
select is(public.sync_revenuecat_entitlement('10000000-0000-0000-0000-000000000091', repeat('a',64), 'aqualog_pro_annual', now()+interval '1 year', now()) ->> 'active', 'false', 'late stale HTTP response cannot restore revoked access');
select is(public.sync_revenuecat_entitlement('10000000-0000-0000-0000-000000000091', repeat('a',64), 'aqualog_pro_lifetime', null, now()+interval '2 seconds') ->> 'active', 'true', 'non-expiring lifetime snapshot grants Pro');
select throws_ok($$select public.sync_revenuecat_entitlement('10000000-0000-0000-0000-000000000091', repeat('a',64), 'aqualog_pro_annual', null, now())$$,
    '22023', 'invalid RevenueCat entitlement', 'annual cannot become a lifetime entitlement');
reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000092', true);
select is((select count(*) from public.pro_entitlements), 0::bigint, 'B cannot read A entitlement');
select throws_ok($$select public.register_revenuecat_customer('10000000-0000-0000-0000-000000000092', repeat('b',64))$$,
    '42501', 'permission denied for function register_revenuecat_customer', 'clients cannot register billing identities');
select throws_ok($$select public.sync_revenuecat_entitlement('10000000-0000-0000-0000-000000000092', repeat('b',64), 'aqualog_pro_lifetime', null, now())$$,
    '42501', 'permission denied for function sync_revenuecat_entitlement', 'clients cannot grant themselves Pro');
select throws_ok($$select * from public.revenuecat_customers$$,
    '42501', 'permission denied for table revenuecat_customers', 'opaque customer IDs remain server-only');
reset role;
set local role service_role;
update public.revenuecat_customers set deletion_requested = true where account_id='10000000-0000-0000-0000-000000000091';
select throws_ok($$select public.register_revenuecat_customer('10000000-0000-0000-0000-000000000091', repeat('a',64))$$,
    '42501', 'RevenueCat identity cannot be replaced', 'deleting customer cannot be recreated by a concurrent request');
select throws_ok($$select public.sync_revenuecat_entitlement('10000000-0000-0000-0000-000000000091', repeat('a',64), 'aqualog_pro_lifetime', null, now())$$,
    '42501', 'unregistered RevenueCat account', 'deleting customer cannot regain Pro');
reset role;
delete from auth.users where id='10000000-0000-0000-0000-000000000091';
select is((select count(*) from public.revenuecat_customers), 0::bigint, 'Auth deletion removes RevenueCat mapping');
select * from finish();
rollback;
