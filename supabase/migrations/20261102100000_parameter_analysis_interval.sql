-- Per-Aquarium analysis rhythm of a Parameter (lot 2). Null means the profile default, so rows written
-- before this column, and payloads from older clients, stay valid. Like is_archived, the initial
-- account copy (migrate_initial_copy) is left unchanged: the next parameter_definition upsert carries it.
alter table public.parameter_definitions
    add column analysis_interval_days integer
        check (analysis_interval_days is null or analysis_interval_days > 0);

alter function public.apply_sync_operation(text, text, uuid, text, bigint, jsonb)
    rename to apply_sync_operation_without_analysis_interval;

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
begin
    perform public.apply_sync_operation_without_analysis_interval(
        p_operation_key,
        p_entity_type,
        p_entity_id,
        p_kind,
        p_client_updated_at_epoch_millis,
        p_payload
    );
    if p_entity_type = 'parameter_definition' and p_kind = 'upsert' then
        update public.parameter_definitions
        set analysis_interval_days = (p_payload->>'analysis_interval_days')::integer
        where id = p_entity_id and account_id = auth.uid();
    end if;
end;
$$;

revoke all on function public.apply_sync_operation_without_analysis_interval(text, text, uuid, text, bigint, jsonb)
from public, anon, authenticated;
revoke all on function public.apply_sync_operation(text, text, uuid, text, bigint, jsonb)
from public, anon, authenticated;
