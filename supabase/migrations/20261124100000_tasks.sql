-- Tasks and their Occurrences join the synchronization: until now they lived on one device only and were lost with
-- its local copy. Written only through apply_sync_operation (same server-ordered LWW, receipts and tombstones as the
-- other entities); migrate_initial_copy is left unchanged: the client enqueues an upsert per row when it completes
-- the account migration, as it does for inhabitants.
create table public.tasks (
    id uuid primary key,
    account_id uuid not null references auth.users(id) on delete cascade,
    aquarium_id uuid not null,
    title text not null check (nullif(btrim(title), '') is not null),
    recurrence text not null check (recurrence in ('once', 'daily', 'weekly', 'monthly')),
    monthly_anchor_day integer not null check (monthly_anchor_day between 1 and 31),
    maintenance_action_type text,
    server_version bigint not null default 1,
    client_updated_at_epoch_millis bigint not null default 0,
    server_updated_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    deleted_at_epoch_millis bigint,
    last_operation_key text not null default '',
    unique (account_id, id),
    foreign key (account_id, aquarium_id) references public.aquariums(account_id, id) on delete cascade
);

-- Two devices that resolve the same Occurrence compute the same id for the next one (the client derives it from
-- the Task and the due date), so no uniqueness on the due date is needed here.
create table public.task_occurrences (
    id uuid primary key,
    account_id uuid not null references auth.users(id) on delete cascade,
    task_id uuid not null,
    aquarium_id uuid not null,
    title text not null,
    due_year integer not null,
    due_month integer not null check (due_month between 1 and 12),
    due_day integer not null check (due_day between 1 and 31),
    minute_of_day integer check (minute_of_day between 0 and 1439),
    time_zone_id text not null check (nullif(btrim(time_zone_id), '') is not null),
    resolution text check (resolution in ('completed', 'postponed', 'ignored')),
    resolved_at_epoch_millis bigint,
    maintenance_action_type text,
    server_version bigint not null default 1,
    client_updated_at_epoch_millis bigint not null default 0,
    server_updated_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    deleted_at_epoch_millis bigint,
    last_operation_key text not null default '',
    check ((resolution is null) = (resolved_at_epoch_millis is null)),
    foreign key (account_id, task_id) references public.tasks(account_id, id) on delete cascade,
    foreign key (account_id, aquarium_id) references public.aquariums(account_id, id) on delete cascade
);

alter table public.tasks enable row level security;
alter table public.task_occurrences enable row level security;

create policy "owners read their tasks"
on public.tasks for select to authenticated
using (account_id = (select auth.uid()));

create policy "owners read their task occurrences"
on public.task_occurrences for select to authenticated
using (account_id = (select auth.uid()));

revoke all on public.tasks from anon, authenticated;
revoke all on public.task_occurrences from anon, authenticated;
grant select on public.tasks to authenticated;
grant select on public.task_occurrences to authenticated;

-- The two new entity types are handled by a new outer six-argument function; every other type goes on to the
-- function that was there (photo, analysis-interval and archive wrappers, then the entity mapping).
alter function public.apply_sync_operation(text, text, uuid, text, bigint, jsonb)
    rename to apply_sync_operation_without_tasks;

create function public.apply_sync_operation(
    p_operation_key text,
    p_entity_type text,
    p_entity_id uuid,
    p_kind text,
    p_client_updated_at_epoch_millis bigint,
    p_payload jsonb
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_account_id uuid := auth.uid();
    v_table text;
    v_inserted_receipt text;
    v_owned_by_other_account boolean;
    v_server_now bigint := (extract(epoch from clock_timestamp()) * 1000)::bigint;
begin
    if p_entity_type not in ('task', 'task_occurrence') then
        perform public.apply_sync_operation_without_tasks(
            p_operation_key, p_entity_type, p_entity_id, p_kind, p_client_updated_at_epoch_millis, p_payload
        );
        return;
    end if;

    if v_account_id is null then
        raise exception using errcode = '28000', message = 'authentication required';
    end if;
    if nullif(p_operation_key, '') is null then
        raise exception using errcode = '22023', message = 'operation key is required';
    end if;
    if p_client_updated_at_epoch_millis < 0 then
        raise exception using errcode = '22023', message = 'client update time is invalid';
    end if;
    if p_kind not in ('upsert', 'delete') then
        raise exception using errcode = '22023', message = 'unsupported sync operation kind';
    end if;
    v_table := case p_entity_type when 'task' then 'tasks' else 'task_occurrences' end;

    insert into public.sync_operation_receipts(account_id, operation_key)
    values (v_account_id, p_operation_key)
    on conflict do nothing
    returning operation_key into v_inserted_receipt;
    if v_inserted_receipt is null then
        return;
    end if;

    execute format(
        'select exists(select 1 from public.%I where id = $1 and account_id <> $2)',
        v_table
    ) into v_owned_by_other_account using p_entity_id, v_account_id;
    if v_owned_by_other_account then
        raise exception using errcode = '42501', message = 'entity UUID belongs to another account';
    end if;

    if p_kind = 'delete' then
        execute format(
            'update public.%I
             set deleted_at_epoch_millis = $1,
                 client_updated_at_epoch_millis = $2,
                 server_updated_at_epoch_millis = $1,
                 server_version = server_version + 1,
                 last_operation_key = $3
             where id = $4 and account_id = $5
               and (client_updated_at_epoch_millis, last_operation_key) < ($2, $3)',
            v_table
        ) using v_server_now, p_client_updated_at_epoch_millis, p_operation_key, p_entity_id, v_account_id;
        return;
    end if;

    if p_payload is null or jsonb_typeof(p_payload) <> 'object' then
        raise exception using errcode = '22023', message = 'upsert payload must be a JSON object';
    end if;
    if nullif(p_payload->>'id', '')::uuid is distinct from p_entity_id then
        raise exception using errcode = '22023', message = 'payload id must match entity id';
    end if;

    if p_entity_type = 'task' then
        insert into public.tasks as stored (
            id, account_id, aquarium_id, title, recurrence, monthly_anchor_day, maintenance_action_type,
            server_version, client_updated_at_epoch_millis, server_updated_at_epoch_millis,
            deleted_at_epoch_millis, last_operation_key
        ) values (
            p_entity_id, v_account_id, (p_payload->>'aquarium_id')::uuid, p_payload->>'title',
            p_payload->>'recurrence', (p_payload->>'monthly_anchor_day')::integer,
            p_payload->>'maintenance_action_type',
            1, p_client_updated_at_epoch_millis, v_server_now, null, p_operation_key
        ) on conflict (id) do update set
            aquarium_id = excluded.aquarium_id, title = excluded.title, recurrence = excluded.recurrence,
            monthly_anchor_day = excluded.monthly_anchor_day,
            maintenance_action_type = excluded.maintenance_action_type,
            server_version = stored.server_version + 1,
            client_updated_at_epoch_millis = excluded.client_updated_at_epoch_millis,
            server_updated_at_epoch_millis = excluded.server_updated_at_epoch_millis,
            deleted_at_epoch_millis = null, last_operation_key = excluded.last_operation_key
        where stored.account_id = v_account_id and
            (stored.client_updated_at_epoch_millis, stored.last_operation_key) <
            (excluded.client_updated_at_epoch_millis, excluded.last_operation_key);
    else
        insert into public.task_occurrences as stored (
            id, account_id, task_id, aquarium_id, title, due_year, due_month, due_day, minute_of_day,
            time_zone_id, resolution, resolved_at_epoch_millis, maintenance_action_type,
            server_version, client_updated_at_epoch_millis, server_updated_at_epoch_millis,
            deleted_at_epoch_millis, last_operation_key
        ) values (
            p_entity_id, v_account_id, (p_payload->>'task_id')::uuid, (p_payload->>'aquarium_id')::uuid,
            p_payload->>'title', (p_payload->>'due_year')::integer, (p_payload->>'due_month')::integer,
            (p_payload->>'due_day')::integer, (p_payload->>'minute_of_day')::integer,
            p_payload->>'time_zone_id', p_payload->>'resolution',
            (p_payload->>'resolved_at_epoch_millis')::bigint, p_payload->>'maintenance_action_type',
            1, p_client_updated_at_epoch_millis, v_server_now, null, p_operation_key
        ) on conflict (id) do update set
            task_id = excluded.task_id, aquarium_id = excluded.aquarium_id, title = excluded.title,
            due_year = excluded.due_year, due_month = excluded.due_month, due_day = excluded.due_day,
            minute_of_day = excluded.minute_of_day, time_zone_id = excluded.time_zone_id,
            resolution = excluded.resolution, resolved_at_epoch_millis = excluded.resolved_at_epoch_millis,
            maintenance_action_type = excluded.maintenance_action_type,
            server_version = stored.server_version + 1,
            client_updated_at_epoch_millis = excluded.client_updated_at_epoch_millis,
            server_updated_at_epoch_millis = excluded.server_updated_at_epoch_millis,
            deleted_at_epoch_millis = null, last_operation_key = excluded.last_operation_key
        where stored.account_id = v_account_id and
            (stored.client_updated_at_epoch_millis, stored.last_operation_key) <
            (excluded.client_updated_at_epoch_millis, excluded.last_operation_key);
    end if;
end;
$$;

-- The client entry point: unchanged but for the two new entity types in its table dispatch.
create or replace function public.apply_sync_operation(
    p_operation_key text,
    p_entity_type text,
    p_entity_id uuid,
    p_kind text,
    p_client_updated_at_epoch_millis bigint,
    p_base_server_version bigint,
    p_payload jsonb
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_account_id uuid := auth.uid();
    v_table text;
    v_current_server_version bigint;
    v_assigned_server_version bigint;
    v_server_acceptance_order bigint;
begin
    if v_account_id is null then
        raise exception using errcode = '28000', message = 'authentication required';
    end if;
    if p_base_server_version is null or p_base_server_version < 0 then
        raise exception using errcode = '22023', message = 'base server version is invalid';
    end if;

    v_table := case p_entity_type
        when 'aquarium' then 'aquariums'
        when 'parameter_definition' then 'parameter_definitions'
        when 'session' then 'sessions'
        when 'measurement' then 'measurements'
        when 'maintenance_action' then 'maintenance_actions'
        when 'event' then 'session_events'
        when 'inhabitant' then 'inhabitants'
        when 'task' then 'tasks'
        when 'task_occurrence' then 'task_occurrences'
        else null
    end;
    if v_table is null then
        raise exception using errcode = '22023', message = 'unsupported sync entity type';
    end if;

    -- One receipt can only ever be accepted once, even if an ambiguous response is replayed concurrently.
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_account_id::text || ':' || p_operation_key, 1201)
    );
    if exists (
        select 1 from public.sync_operation_receipts
        where account_id = v_account_id and operation_key = p_operation_key
    ) then
        return;
    end if;

    -- Serialize all accepted writes for one entity, including its first insert.
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_account_id::text || ':' || p_entity_type || ':' || p_entity_id::text, 1202)
    );
    execute format(
        'select server_version from public.%I where id = $1 and account_id = $2',
        v_table
    ) into v_current_server_version using p_entity_id, v_account_id;
    v_current_server_version := coalesce(v_current_server_version, 0);

    -- The old six-argument function owns validation and entity mapping. Its ordering input now comes
    -- exclusively from this server sequence; the client timestamp and operation key cannot win LWW.
    v_server_acceptance_order := nextval('public.sync_acceptance_order_seq');
    perform public.apply_sync_operation(
        p_operation_key,
        p_entity_type,
        p_entity_id,
        p_kind,
        v_server_acceptance_order,
        p_payload
    );

    execute format(
        'select server_version from public.%I where id = $1 and account_id = $2',
        v_table
    ) into v_assigned_server_version using p_entity_id, v_account_id;
    v_assigned_server_version := coalesce(v_assigned_server_version, v_current_server_version);

    update public.sync_operation_receipts
    set entity_type = p_entity_type,
        entity_id = p_entity_id,
        base_server_version = p_base_server_version,
        assigned_server_version = v_assigned_server_version,
        conflicted = p_base_server_version <> v_current_server_version
    where account_id = v_account_id and operation_key = p_operation_key;
end;
$$;

revoke all on function public.apply_sync_operation_without_tasks(text, text, uuid, text, bigint, jsonb)
from public, anon, authenticated;
revoke all on function public.apply_sync_operation(text, text, uuid, text, bigint, jsonb)
from public, anon, authenticated;
revoke all on function public.apply_sync_operation(text, text, uuid, text, bigint, bigint, jsonb)
from public, anon;
grant execute on function public.apply_sync_operation(text, text, uuid, text, bigint, bigint, jsonb)
to authenticated;

-- Same function as in 20261105110000_inhabitants_checks.sql, with the Tasks and their Occurrences listed explicitly.
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
    delete from public.task_occurrences where account_id = v_account_id;
    delete from public.tasks where account_id = v_account_id;
    delete from public.inhabitants where account_id = v_account_id;
    delete from public.aquariums where account_id = v_account_id;
    delete from public.sync_operation_receipts where account_id = v_account_id;

    insert into public.account_deletion_receipts(account_id, operation_key)
    values (v_account_id, p_operation_key);
end;
$$;

revoke all on function public.delete_account_data(text) from public, anon;
grant execute on function public.delete_account_data(text) to authenticated;
