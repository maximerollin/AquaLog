alter table public.sync_operation_receipts
    add column entity_type text,
    add column entity_id uuid,
    add column base_server_version bigint,
    add column assigned_server_version bigint,
    add column conflicted boolean not null default false;

create sequence public.sync_acceptance_order_seq as bigint;

select setval(
    'public.sync_acceptance_order_seq',
    greatest(
        coalesce((select max(client_updated_at_epoch_millis) from public.aquariums), 0),
        coalesce((select max(client_updated_at_epoch_millis) from public.parameter_definitions), 0),
        coalesce((select max(client_updated_at_epoch_millis) from public.sessions), 0),
        coalesce((select max(client_updated_at_epoch_millis) from public.measurements), 0),
        coalesce((select max(client_updated_at_epoch_millis) from public.maintenance_actions), 0),
        coalesce((select max(client_updated_at_epoch_millis) from public.session_events), 0),
        1
    ),
    true
);

revoke all on sequence public.sync_acceptance_order_seq from public, anon, authenticated;
revoke all on function public.apply_sync_operation(text, text, uuid, text, bigint, jsonb)
from authenticated;

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

revoke all on function public.apply_sync_operation(text, text, uuid, text, bigint, bigint, jsonb)
from public, anon;
grant execute on function public.apply_sync_operation(text, text, uuid, text, bigint, bigint, jsonb)
to authenticated;
