begin;

create extension if not exists pgtap with schema extensions;
select plan(9);

insert into auth.users (id, aud, role, email, encrypted_password, created_at, updated_at)
values ('17000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'feedback@example.com', '', now(), now());

select throws_ok(
    $$select public.submit_product_feedback('hello')$$,
    '42501',
    'authentication required',
    'anonymous feedback is rejected'
);

set local role authenticated;
set local request.jwt.claims = '{"sub":"17000000-0000-0000-0000-000000000001","role":"authenticated"}';
select throws_ok(
    $$select public.submit_product_feedback('   ')$$,
    '22023',
    'invalid feedback',
    'blank feedback is rejected'
);
select throws_ok(
    $$select public.submit_product_feedback(repeat('x', 2001))$$,
    '22023',
    'invalid feedback',
    'oversized feedback is rejected'
);
select lives_ok($$select public.submit_product_feedback('first')$$, 'first feedback is accepted');
select lives_ok($$select public.submit_product_feedback('second')$$, 'second feedback is accepted');
select lives_ok($$select public.submit_product_feedback('third')$$, 'third feedback is accepted');
select throws_ok(
    $$select public.submit_product_feedback('fourth')$$,
    'P0001',
    'feedback rate limit reached',
    'fourth feedback in one hour is rate limited'
);
select throws_ok(
    $$select count(*) from public.product_feedback$$,
    '42501',
    null,
    'authenticated clients cannot read feedback rows'
);

reset role;
select is(
    (select array_agg(message order by id) from public.product_feedback),
    array['first', 'second', 'third']::text[],
    'only the three voluntary messages are stored'
);

select * from finish();
rollback;
