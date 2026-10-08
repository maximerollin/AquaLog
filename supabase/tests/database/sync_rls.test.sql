begin;

create extension if not exists pgtap with schema extensions;
select plan(18);

insert into auth.users (id, aud, role, email, encrypted_password, created_at, updated_at)
values
    ('11000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'sync-owner@example.com', '', now(), now()),
    ('11000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'sync-other@example.com', '', now(), now());

set local role authenticated;
set local request.jwt.claims = '{"sub":"11000000-0000-0000-0000-000000000001","role":"authenticated"}';

select lives_ok($$insert into public.aquariums values ('21000000-0000-0000-0000-000000000001','11000000-0000-0000-0000-000000000001','Amazonien',120,'liters',1700000000000,'established')$$, 'owner inserts Aquarium');
select lives_ok($$insert into public.parameter_definitions values ('31000000-0000-0000-0000-000000000001','11000000-0000-0000-0000-000000000001','21000000-0000-0000-0000-000000000001','temperature',true,0,'°C',1,23,27)$$, 'owner inserts Parameter');
select lives_ok($$insert into public.sessions values ('41000000-0000-0000-0000-000000000001','11000000-0000-0000-0000-000000000001','21000000-0000-0000-0000-000000000001',1700000100000,1700000101000)$$, 'owner inserts Session');
select lives_ok($$insert into public.measurements values ('51000000-0000-0000-0000-000000000001','11000000-0000-0000-0000-000000000001','41000000-0000-0000-0000-000000000001','31000000-0000-0000-0000-000000000001',24.5)$$, 'owner inserts Measurement');
select lives_ok($$insert into public.maintenance_actions values ('61000000-0000-0000-0000-000000000001','11000000-0000-0000-0000-000000000001','41000000-0000-0000-0000-000000000001','water_change',20,'%',null)$$, 'owner inserts Action');
select lives_ok($$insert into public.session_events values ('71000000-0000-0000-0000-000000000001','11000000-0000-0000-0000-000000000001','41000000-0000-0000-0000-000000000001','observation','Eau claire')$$, 'owner inserts Event');

set local request.jwt.claims = '{"sub":"11000000-0000-0000-0000-000000000002","role":"authenticated"}';

select is((select count(*) from public.aquariums), 0::bigint, 'other account cannot read Aquarium');
select is((select count(*) from public.parameter_definitions), 0::bigint, 'other account cannot read Parameter');
select is((select count(*) from public.sessions), 0::bigint, 'other account cannot read Session');
select is((select count(*) from public.measurements), 0::bigint, 'other account cannot read Measurement');
select is((select count(*) from public.maintenance_actions), 0::bigint, 'other account cannot read Action');
select is((select count(*) from public.session_events), 0::bigint, 'other account cannot read Event');

select is_empty($$update public.aquariums set name = 'Intrusion' where id = '21000000-0000-0000-0000-000000000001' returning id$$, 'other account cannot update Aquarium');
select is_empty($$update public.parameter_definitions set unit = 'K' where id = '31000000-0000-0000-0000-000000000001' returning id$$, 'other account cannot update Parameter');
select is_empty($$update public.sessions set occurred_at_epoch_millis = 1 where id = '41000000-0000-0000-0000-000000000001' returning id$$, 'other account cannot update Session');
select is_empty($$update public.measurements set value = 99 where id = '51000000-0000-0000-0000-000000000001' returning id$$, 'other account cannot update Measurement');
select is_empty($$update public.maintenance_actions set quantity = 99 where id = '61000000-0000-0000-0000-000000000001' returning id$$, 'other account cannot update Action');
select is_empty($$update public.session_events set note = 'Intrusion' where id = '71000000-0000-0000-0000-000000000001' returning id$$, 'other account cannot update Event');

select * from finish();
rollback;
