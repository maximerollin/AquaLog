do $$
declare
    v_table text;
begin
    foreach v_table in array array[
        'aquariums',
        'parameter_definitions',
        'sessions',
        'measurements',
        'maintenance_actions',
        'session_events'
    ] loop
        execute format(
            'alter table public.%I
                add column server_version bigint not null default 1,
                add column client_updated_at_epoch_millis bigint not null default 0,
                add column server_updated_at_epoch_millis bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
                add column deleted_at_epoch_millis bigint,
                add column last_operation_key text not null default ''''',
            v_table
        );
    end loop;
end;
$$;

create table public.sync_operation_receipts (
    account_id uuid not null references auth.users(id) on delete cascade,
    operation_key text not null,
    accepted_at timestamptz not null default clock_timestamp(),
    primary key (account_id, operation_key)
);

alter table public.sync_operation_receipts enable row level security;
revoke all on public.sync_operation_receipts from public, anon, authenticated;

create or replace function public.apply_sync_operation(
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

    v_table := case p_entity_type
        when 'aquarium' then 'aquariums'
        when 'parameter_definition' then 'parameter_definitions'
        when 'session' then 'sessions'
        when 'measurement' then 'measurements'
        when 'maintenance_action' then 'maintenance_actions'
        when 'event' then 'session_events'
        else null
    end;
    if v_table is null then
        raise exception using errcode = '22023', message = 'unsupported sync entity type';
    end if;

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

    case p_entity_type
        when 'aquarium' then
            insert into public.aquariums as stored (
                id, account_id, name, volume, volume_unit, created_at_epoch_millis, profile,
                server_version, client_updated_at_epoch_millis, server_updated_at_epoch_millis,
                deleted_at_epoch_millis, last_operation_key
            ) values (
                p_entity_id, v_account_id, p_payload->>'name', (p_payload->>'volume')::double precision,
                p_payload->>'volume_unit', (p_payload->>'created_at_epoch_millis')::bigint,
                p_payload->>'profile', 1, p_client_updated_at_epoch_millis, v_server_now, null, p_operation_key
            ) on conflict (id) do update set
                name = excluded.name, volume = excluded.volume, volume_unit = excluded.volume_unit,
                created_at_epoch_millis = excluded.created_at_epoch_millis, profile = excluded.profile,
                server_version = stored.server_version + 1,
                client_updated_at_epoch_millis = excluded.client_updated_at_epoch_millis,
                server_updated_at_epoch_millis = excluded.server_updated_at_epoch_millis,
                deleted_at_epoch_millis = null, last_operation_key = excluded.last_operation_key
            where stored.account_id = v_account_id and
                (stored.client_updated_at_epoch_millis, stored.last_operation_key) <
                (excluded.client_updated_at_epoch_millis, excluded.last_operation_key);
        when 'parameter_definition' then
            insert into public.parameter_definitions as stored (
                id, account_id, aquarium_id, parameter, is_active, position, unit, precision,
                indicative_minimum, indicative_maximum, server_version, client_updated_at_epoch_millis,
                server_updated_at_epoch_millis, deleted_at_epoch_millis, last_operation_key
            ) values (
                p_entity_id, v_account_id, (p_payload->>'aquarium_id')::uuid, p_payload->>'parameter',
                (p_payload->>'is_active')::boolean, (p_payload->>'position')::integer, p_payload->>'unit',
                (p_payload->>'precision')::integer, (p_payload->>'indicative_minimum')::double precision,
                (p_payload->>'indicative_maximum')::double precision, 1, p_client_updated_at_epoch_millis,
                v_server_now, null, p_operation_key
            ) on conflict (id) do update set
                aquarium_id = excluded.aquarium_id, parameter = excluded.parameter,
                is_active = excluded.is_active, position = excluded.position, unit = excluded.unit,
                precision = excluded.precision, indicative_minimum = excluded.indicative_minimum,
                indicative_maximum = excluded.indicative_maximum, server_version = stored.server_version + 1,
                client_updated_at_epoch_millis = excluded.client_updated_at_epoch_millis,
                server_updated_at_epoch_millis = excluded.server_updated_at_epoch_millis,
                deleted_at_epoch_millis = null, last_operation_key = excluded.last_operation_key
            where stored.account_id = v_account_id and
                (stored.client_updated_at_epoch_millis, stored.last_operation_key) <
                (excluded.client_updated_at_epoch_millis, excluded.last_operation_key);
        when 'session' then
            insert into public.sessions as stored (
                id, account_id, aquarium_id, occurred_at_epoch_millis, created_at_epoch_millis,
                server_version, client_updated_at_epoch_millis, server_updated_at_epoch_millis,
                deleted_at_epoch_millis, last_operation_key
            ) values (
                p_entity_id, v_account_id, (p_payload->>'aquarium_id')::uuid,
                (p_payload->>'occurred_at_epoch_millis')::bigint,
                (p_payload->>'created_at_epoch_millis')::bigint, 1,
                p_client_updated_at_epoch_millis, v_server_now, null, p_operation_key
            ) on conflict (id) do update set
                aquarium_id = excluded.aquarium_id, occurred_at_epoch_millis = excluded.occurred_at_epoch_millis,
                created_at_epoch_millis = excluded.created_at_epoch_millis,
                server_version = stored.server_version + 1,
                client_updated_at_epoch_millis = excluded.client_updated_at_epoch_millis,
                server_updated_at_epoch_millis = excluded.server_updated_at_epoch_millis,
                deleted_at_epoch_millis = null, last_operation_key = excluded.last_operation_key
            where stored.account_id = v_account_id and
                (stored.client_updated_at_epoch_millis, stored.last_operation_key) <
                (excluded.client_updated_at_epoch_millis, excluded.last_operation_key);
        when 'measurement' then
            insert into public.measurements as stored (
                id, account_id, session_id, parameter_definition_id, value, server_version,
                client_updated_at_epoch_millis, server_updated_at_epoch_millis,
                deleted_at_epoch_millis, last_operation_key
            ) values (
                p_entity_id, v_account_id, (p_payload->>'session_id')::uuid,
                (p_payload->>'parameter_definition_id')::uuid, (p_payload->>'value')::double precision,
                1, p_client_updated_at_epoch_millis, v_server_now, null, p_operation_key
            ) on conflict (id) do update set
                session_id = excluded.session_id, parameter_definition_id = excluded.parameter_definition_id,
                value = excluded.value, server_version = stored.server_version + 1,
                client_updated_at_epoch_millis = excluded.client_updated_at_epoch_millis,
                server_updated_at_epoch_millis = excluded.server_updated_at_epoch_millis,
                deleted_at_epoch_millis = null, last_operation_key = excluded.last_operation_key
            where stored.account_id = v_account_id and
                (stored.client_updated_at_epoch_millis, stored.last_operation_key) <
                (excluded.client_updated_at_epoch_millis, excluded.last_operation_key);
        when 'maintenance_action' then
            insert into public.maintenance_actions as stored (
                id, account_id, session_id, type, quantity, unit, product, server_version,
                client_updated_at_epoch_millis, server_updated_at_epoch_millis,
                deleted_at_epoch_millis, last_operation_key
            ) values (
                p_entity_id, v_account_id, (p_payload->>'session_id')::uuid, p_payload->>'type',
                (p_payload->>'quantity')::double precision, p_payload->>'unit', p_payload->>'product',
                1, p_client_updated_at_epoch_millis, v_server_now, null, p_operation_key
            ) on conflict (id) do update set
                session_id = excluded.session_id, type = excluded.type, quantity = excluded.quantity,
                unit = excluded.unit, product = excluded.product, server_version = stored.server_version + 1,
                client_updated_at_epoch_millis = excluded.client_updated_at_epoch_millis,
                server_updated_at_epoch_millis = excluded.server_updated_at_epoch_millis,
                deleted_at_epoch_millis = null, last_operation_key = excluded.last_operation_key
            where stored.account_id = v_account_id and
                (stored.client_updated_at_epoch_millis, stored.last_operation_key) <
                (excluded.client_updated_at_epoch_millis, excluded.last_operation_key);
        when 'event' then
            insert into public.session_events as stored (
                id, account_id, session_id, type, note, server_version,
                client_updated_at_epoch_millis, server_updated_at_epoch_millis,
                deleted_at_epoch_millis, last_operation_key
            ) values (
                p_entity_id, v_account_id, (p_payload->>'session_id')::uuid, p_payload->>'type',
                p_payload->>'note', 1, p_client_updated_at_epoch_millis, v_server_now, null, p_operation_key
            ) on conflict (id) do update set
                session_id = excluded.session_id, type = excluded.type, note = excluded.note,
                server_version = stored.server_version + 1,
                client_updated_at_epoch_millis = excluded.client_updated_at_epoch_millis,
                server_updated_at_epoch_millis = excluded.server_updated_at_epoch_millis,
                deleted_at_epoch_millis = null, last_operation_key = excluded.last_operation_key
            where stored.account_id = v_account_id and
                (stored.client_updated_at_epoch_millis, stored.last_operation_key) <
                (excluded.client_updated_at_epoch_millis, excluded.last_operation_key);
    end case;
end;
$$;

revoke all on function public.apply_sync_operation(text, text, uuid, text, bigint, jsonb)
from public, anon;
grant execute on function public.apply_sync_operation(text, text, uuid, text, bigint, jsonb)
to authenticated;
