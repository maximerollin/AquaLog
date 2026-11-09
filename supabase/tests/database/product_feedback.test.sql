begin;

create extension if not exists pgtap with schema extensions;
select plan(37);

insert into auth.users (id, aud, role, email, encrypted_password, created_at, updated_at)
values
    ('17000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'feedback@example.com', '', now(), now()),
    ('17000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'feedback2@example.com', '', now(), now());

select ok(
    not has_function_privilege('anon', 'public.submit_product_feedback(text,text,text,text,text)', 'execute'),
    'anon cannot execute submit_product_feedback'
);
select ok(
    not has_function_privilege('anon', 'public.submit_anonymous_feedback(uuid,text,text,text,text,text)', 'execute'),
    'anon cannot execute submit_anonymous_feedback'
);
select ok(
    not has_function_privilege('authenticated', 'public.submit_anonymous_feedback(uuid,text,text,text,text,text)', 'execute'),
    'authenticated cannot execute submit_anonymous_feedback'
);
select ok(
    has_function_privilege('service_role', 'public.submit_anonymous_feedback(uuid,text,text,text,text,text)', 'execute'),
    'service_role can execute submit_anonymous_feedback'
);
select hasnt_function('public', 'submit_product_feedback', array['text'], 'the one-argument signature is dropped');

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
    (select array_agg(message order by id) from public.product_feedback
     where account_id = '17000000-0000-0000-0000-000000000001'),
    array['first', 'second', 'third']::text[],
    'only the three voluntary messages are stored'
);

-- typed feedback
set local role authenticated;
set local request.jwt.claims = '{"sub":"17000000-0000-0000-0000-000000000002","role":"authenticated"}';
select lives_ok($$select public.submit_product_feedback('plain')$$, 'message-only call is accepted');
select lives_ok(
    $$select public.submit_product_feedback('typo in the sheet', 'species', 'neon', '1.4.0', 'fr')$$,
    'typed call is accepted'
);
select throws_ok(
    $$select public.submit_product_feedback('x', 'unknown')$$,
    '22023', 'invalid feedback', 'unknown kind is rejected'
);
select throws_ok(
    $$select public.submit_product_feedback('x', 'species', 'Neon!')$$,
    '22023', 'invalid feedback', 'bad species id is rejected'
);
select throws_ok(
    $$select public.submit_product_feedback('x', 'other', null, null, 'français')$$,
    '22023', 'invalid feedback', 'bad language is rejected'
);
select throws_ok(
    $$select public.submit_product_feedback(repeat('x', 1990) || repeat(' ', 20))$$,
    '22023', 'invalid feedback', 'padded over-long message is rejected with 22023'
);
reset role;
select is(
    (select kind from public.product_feedback where message = 'plain'),
    'other', 'default kind is other'
);
select is(
    (select row(kind, species_id, app_version, language, install_id)::text
       from public.product_feedback where message = 'typo in the sheet'),
    '(species,neon,1.4.0,fr,)', 'typed values are stored'
);

-- anonymous feedback, service role only
set local role service_role;
select lives_ok(
    $$select public.submit_anonymous_feedback('aaaaaaaa-0000-0000-0000-000000000001', 'anon one', 'idea', null, '1.4.0', 'en')$$,
    'service role stores anonymous feedback'
);
reset role;
select is(
    (select row(account_id is null, install_id, kind)::text from public.product_feedback where message = 'anon one'),
    '(t,aaaaaaaa-0000-0000-0000-000000000001,idea)', 'anonymous row has no account and keeps the install id'
);
set local role service_role;
select lives_ok($$select public.submit_anonymous_feedback('aaaaaaaa-0000-0000-0000-000000000001', 'anon two')$$, 'second anonymous call');
select lives_ok($$select public.submit_anonymous_feedback('aaaaaaaa-0000-0000-0000-000000000001', 'anon three')$$, 'third anonymous call');
select throws_ok(
    $$select public.submit_anonymous_feedback('aaaaaaaa-0000-0000-0000-000000000001', 'anon four')$$,
    'P0001', 'feedback rate limit reached', 'fourth anonymous call for one install is rate limited'
);
select throws_ok(
    $$select public.submit_anonymous_feedback('aaaaaaaa-0000-0000-0000-000000000002', 'x', 'unknown')$$,
    '22023', 'invalid feedback', 'anonymous unknown kind is rejected'
);
select throws_ok(
    $$select public.submit_anonymous_feedback('aaaaaaaa-0000-0000-0000-000000000002', 'x', 'species', 'Neon!')$$,
    '22023', 'invalid feedback', 'anonymous bad species id is rejected'
);
select throws_ok(
    $$select public.submit_anonymous_feedback('aaaaaaaa-0000-0000-0000-000000000002', 'x', 'other', null, null, 'français')$$,
    '22023', 'invalid feedback', 'anonymous bad language is rejected'
);
select throws_ok(
    $$select public.submit_anonymous_feedback('aaaaaaaa-0000-0000-0000-000000000002', repeat('x', 1990) || repeat(' ', 20))$$,
    '22023', 'invalid feedback', 'anonymous padded over-long message is rejected with 22023'
);
select throws_ok(
    $$select public.submit_anonymous_feedback(null, 'x')$$,
    '22023', 'invalid feedback', 'anonymous call without install id is rejected'
);
reset role;
insert into public.product_feedback(install_id, message)
select gen_random_uuid(), 'bulk' from generate_series(1, 100);
set local role service_role;
select throws_ok(
    $$select public.submit_anonymous_feedback('aaaaaaaa-0000-0000-0000-000000000003', 'over the global limit')$$,
    'P0001', 'feedback rate limit reached', 'global anonymous limit is enforced'
);
reset role;

-- table constraints
select throws_ok(
    $$insert into public.product_feedback(message) values ('orphan')$$,
    '23514', null, 'a row needs an account or an install id'
);
select throws_ok(
    $$insert into public.product_feedback(account_id, install_id, message)
      values ('17000000-0000-0000-0000-000000000001', gen_random_uuid(), 'both')$$,
    '23514', null, 'a row cannot have both an account and an install id'
);

-- account deletion
delete from auth.users where id = '17000000-0000-0000-0000-000000000002';
select is(
    (select count(*)::int from public.product_feedback where message in ('plain', 'typo in the sheet')),
    0, 'deleting the account deletes its feedback'
);
select is(
    (select count(*)::int from public.product_feedback where message in ('anon one', 'anon two', 'anon three')),
    3, 'anonymous feedback remains'
);

select * from finish();
rollback;
