create table public.account_deletion_receipts (
    account_id uuid not null,
    operation_key text not null unique,
    completed_at timestamptz not null default clock_timestamp(),
    primary key (account_id, operation_key)
);

alter table public.account_deletion_receipts enable row level security;
revoke all on public.account_deletion_receipts from public, anon, authenticated;

create or replace function public.delete_account_data(p_operation_key text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_account_id uuid := auth.uid();
begin
    if v_account_id is null then
        raise exception using errcode = '28000', message = 'authentication required';
    end if;
    if nullif(trim(p_operation_key), '') is null or length(p_operation_key) > 200 then
        raise exception using errcode = '22023', message = 'deletion operation key is invalid';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_account_id::text || ':' || p_operation_key, 1301)
    );
    if exists (
        select 1 from public.account_deletion_receipts
        where account_id = v_account_id and operation_key = p_operation_key
    ) then
        return;
    end if;

    -- All account-owned rows are removed atomically. Children are explicit so this
    -- remains correct if a future relationship stops cascading.
    delete from public.measurements where account_id = v_account_id;
    delete from public.maintenance_actions where account_id = v_account_id;
    delete from public.session_events where account_id = v_account_id;
    delete from public.sessions where account_id = v_account_id;
    delete from public.parameter_definitions where account_id = v_account_id;
    delete from public.aquariums where account_id = v_account_id;
    delete from public.sync_operation_receipts where account_id = v_account_id;

    insert into public.account_deletion_receipts(account_id, operation_key)
    values (v_account_id, p_operation_key);
end;
$$;

revoke all on function public.delete_account_data(text) from public, anon;
grant execute on function public.delete_account_data(text) to authenticated;

