create table public.product_feedback (
    id bigint generated always as identity primary key,
    account_id uuid not null references auth.users(id) on delete cascade,
    message text not null check (char_length(message) between 1 and 2000),
    created_at timestamptz not null default clock_timestamp()
);

alter table public.product_feedback enable row level security;
revoke all on public.product_feedback from public, anon, authenticated;

create or replace function public.submit_product_feedback(feedback_message text)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    caller uuid := auth.uid();
begin
    if caller is null then
        raise exception using errcode = '42501', message = 'authentication required';
    end if;
    if feedback_message is null or char_length(btrim(feedback_message)) not between 1 and 2000 then
        raise exception using errcode = '22023', message = 'invalid feedback';
    end if;
    perform pg_advisory_xact_lock(hashtextextended(caller::text, 0));
    if (select count(*) from public.product_feedback
        where account_id = caller and created_at > clock_timestamp() - interval '1 hour') >= 3 then
        raise exception using errcode = 'P0001', message = 'feedback rate limit reached';
    end if;
    insert into public.product_feedback(account_id, message) values (caller, feedback_message);
end;
$$;

revoke all on function public.submit_product_feedback(text) from public, anon;
grant execute on function public.submit_product_feedback(text) to authenticated;
