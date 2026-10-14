begin;

create extension if not exists pgtap with schema extensions;
select plan(10);

insert into auth.users (id, aud, role, email, encrypted_password, created_at, updated_at)
values
    ('13000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'delete-owner@example.com', '', now(), now()),
    ('13000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'keep-owner@example.com', '', now(), now());

set local role authenticated;
set local request.jwt.claims = '{"sub":"13000000-0000-0000-0000-000000000001","role":"authenticated"}';
select lives_ok($$insert into public.aquariums(id,account_id,name,volume,volume_unit,created_at_epoch_millis,profile) values ('23000000-0000-0000-0000-000000000001','13000000-0000-0000-0000-000000000001','Delete',10,'liters',1,'established')$$, 'first owner creates data');
select lives_ok($$insert into public.sessions(id,account_id,aquarium_id,occurred_at_epoch_millis,created_at_epoch_millis) values ('43000000-0000-0000-0000-000000000001','13000000-0000-0000-0000-000000000001','23000000-0000-0000-0000-000000000001',2,2)$$, 'first owner creates child data');

set local request.jwt.claims = '{"sub":"13000000-0000-0000-0000-000000000002","role":"authenticated"}';
select lives_ok($$insert into public.aquariums(id,account_id,name,volume,volume_unit,created_at_epoch_millis,profile) values ('23000000-0000-0000-0000-000000000002','13000000-0000-0000-0000-000000000002','Keep',20,'liters',1,'established')$$, 'second owner creates isolated data');

set local request.jwt.claims = '{"sub":"13000000-0000-0000-0000-000000000001","role":"authenticated"}';
select lives_ok($$select public.delete_account_data('delete-operation-1')$$, 'owner deletes account data');
select is((select count(*) from public.aquariums), 0::bigint, 'deleted owner sees no aquarium');
select is((select count(*) from public.sessions), 0::bigint, 'deleted owner sees no session');

set local request.jwt.claims = '{"sub":"13000000-0000-0000-0000-000000000002","role":"authenticated"}';
select is((select count(*) from public.aquariums), 1::bigint, 'other account data remains');
select is((select name from public.aquariums), 'Keep', 'other account content is unchanged');

set local request.jwt.claims = '{"sub":"13000000-0000-0000-0000-000000000001","role":"authenticated"}';
select lives_ok($$select public.delete_account_data('delete-operation-1')$$, 'same operation is idempotent');
select throws_ok(
    $$select public.delete_account_data('')$$,
    '22023',
    'deletion operation key is invalid',
    'empty operation key is rejected'
);

select * from finish();
rollback;

