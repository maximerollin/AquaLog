-- One photo per Aquarium (lot 4). photo_id names the current picture; the bytes live in the private
-- aquarium-photos bucket at <accountId>/<aquariumId>.jpg. A payload without the key (older client) keeps the
-- stored value, an explicit null clears it. Like is_archived, migrate_initial_copy is left unchanged.
alter table public.aquariums add column photo_id uuid;

alter function public.apply_sync_operation(text, text, uuid, text, bigint, jsonb)
    rename to apply_sync_operation_without_aquarium_photo;

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
    perform public.apply_sync_operation_without_aquarium_photo(
        p_operation_key,
        p_entity_type,
        p_entity_id,
        p_kind,
        p_client_updated_at_epoch_millis,
        p_payload
    );
    if p_entity_type = 'aquarium' and p_kind = 'upsert' then
        update public.aquariums
        set photo_id = case
            when p_payload ? 'photo_id' then (p_payload->>'photo_id')::uuid
            else photo_id
        end
        where id = p_entity_id and account_id = auth.uid();
    end if;
end;
$$;

revoke all on function public.apply_sync_operation_without_aquarium_photo(text, text, uuid, text, bigint, jsonb)
from public, anon, authenticated;
revoke all on function public.apply_sync_operation(text, text, uuid, text, bigint, jsonb)
from public, anon, authenticated;

-- Private bucket: each account reaches only the objects below its own <accountId>/ folder.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('aquarium-photos', 'aquarium-photos', false, 1048576, array['image/jpeg']);

create policy aquarium_photos_select on storage.objects for select to authenticated
    using (bucket_id = 'aquarium-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy aquarium_photos_insert on storage.objects for insert to authenticated
    with check (bucket_id = 'aquarium-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy aquarium_photos_update on storage.objects for update to authenticated
    using (bucket_id = 'aquarium-photos' and (storage.foldername(name))[1] = (select auth.uid())::text)
    with check (bucket_id = 'aquarium-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy aquarium_photos_delete on storage.objects for delete to authenticated
    using (bucket_id = 'aquarium-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
