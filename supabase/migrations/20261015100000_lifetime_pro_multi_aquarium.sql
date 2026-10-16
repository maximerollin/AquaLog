alter table public.play_purchase_verifications
    alter column valid_until drop not null;
alter table public.pro_entitlements
    alter column valid_until drop not null;

alter table public.aquariums
    add column is_archived boolean not null default false;

alter function public.apply_sync_operation(text, text, uuid, text, bigint, jsonb)
    rename to apply_sync_operation_without_aquarium_archive;

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
    perform public.apply_sync_operation_without_aquarium_archive(
        p_operation_key,
        p_entity_type,
        p_entity_id,
        p_kind,
        p_client_updated_at_epoch_millis,
        p_payload
    );
    if p_entity_type = 'aquarium' and p_kind = 'upsert' then
        update public.aquariums
        set is_archived = coalesce((p_payload->>'is_archived')::boolean, false)
        where id = p_entity_id and account_id = auth.uid();
    end if;
end;
$$;

revoke all on function public.apply_sync_operation_without_aquarium_archive(text, text, uuid, text, bigint, jsonb)
from public, anon, authenticated;
revoke all on function public.apply_sync_operation(text, text, uuid, text, bigint, jsonb)
from public, anon, authenticated;

create or replace function public.claim_verified_pro_purchase(
    p_account_id uuid,
    p_product_id text,
    p_purchase_token_hash text,
    p_purchase_token_ciphertext text,
    p_valid_until timestamptz,
    p_latest_order_id text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_account_id is null
       or p_product_id not in ('aqualog_pro_annual', 'aqualog_pro_lifetime')
       or p_purchase_token_hash = ''
       or p_purchase_token_ciphertext = ''
       or (p_product_id = 'aqualog_pro_annual' and p_valid_until is null)
       or (p_product_id = 'aqualog_pro_lifetime' and p_valid_until is not null) then
        raise exception using errcode = '22023', message = 'invalid verified purchase claim';
    end if;

    insert into public.play_purchase_verifications as stored (
        purchase_token_hash, purchase_token_ciphertext, account_id, product_id,
        purchase_status, valid_until, latest_order_id, verified_at
    ) values (
        p_purchase_token_hash, p_purchase_token_ciphertext, p_account_id, p_product_id,
        'inactive', p_valid_until, p_latest_order_id, now()
    )
    on conflict (purchase_token_hash) do update set
        purchase_token_ciphertext = excluded.purchase_token_ciphertext,
        valid_until = excluded.valid_until,
        latest_order_id = excluded.latest_order_id,
        verified_at = now()
    where stored.account_id = p_account_id and stored.product_id = p_product_id;

    if not found then
        raise exception using errcode = '42501', message = 'purchase token belongs to another account';
    end if;
end;
$$;

create or replace function public.activate_claimed_pro_purchase(
    p_account_id uuid,
    p_product_id text,
    p_purchase_token_hash text,
    p_valid_until timestamptz,
    p_latest_order_id text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_account_id is null
       or p_product_id not in ('aqualog_pro_annual', 'aqualog_pro_lifetime')
       or p_purchase_token_hash = ''
       or (p_product_id = 'aqualog_pro_lifetime' and p_valid_until is not null) then
        raise exception using errcode = '22023', message = 'invalid verified purchase activation';
    end if;
    if p_product_id = 'aqualog_pro_annual'
       and (p_valid_until is null or p_valid_until <= clock_timestamp()) then
        raise exception using errcode = '22023', message = 'active entitlement must expire in the future';
    end if;

    update public.play_purchase_verifications
    set purchase_status = 'active', valid_until = p_valid_until,
        latest_order_id = p_latest_order_id, verified_at = now()
    where purchase_token_hash = p_purchase_token_hash
      and account_id = p_account_id and product_id = p_product_id;
    if not found then
        raise exception using errcode = '42501', message = 'purchase claim belongs to another account';
    end if;

    insert into public.pro_entitlements as entitlement (
        account_id, product_id, status, valid_until, verified_at, updated_at
    ) values (p_account_id, p_product_id, 'active', p_valid_until, now(), now())
    on conflict (account_id) do update set
        product_id = excluded.product_id, status = excluded.status,
        valid_until = excluded.valid_until, verified_at = excluded.verified_at, updated_at = now();
end;
$$;

create or replace function public.revalidate_claimed_pro_purchase(
    p_account_id uuid,
    p_product_id text,
    p_purchase_token_hash text,
    p_active boolean,
    p_valid_until timestamptz,
    p_latest_order_id text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    new_status text := case
        when p_product_id = 'aqualog_pro_lifetime' and p_active and p_valid_until is null then 'active'
        when p_product_id = 'aqualog_pro_annual' and p_active and p_valid_until > clock_timestamp() then 'active'
        else 'inactive'
    end;
begin
    if p_account_id is null
       or p_product_id not in ('aqualog_pro_annual', 'aqualog_pro_lifetime')
       or p_purchase_token_hash = '' then
        raise exception using errcode = '22023', message = 'invalid entitlement revalidation';
    end if;
    update public.play_purchase_verifications
    set purchase_status = new_status, valid_until = p_valid_until,
        latest_order_id = p_latest_order_id, verified_at = now()
    where purchase_token_hash = p_purchase_token_hash
      and account_id = p_account_id and product_id = p_product_id;
    if not found then
        raise exception using errcode = '42501', message = 'purchase claim belongs to another account';
    end if;
    insert into public.pro_entitlements as entitlement (
        account_id, product_id, status, valid_until, verified_at, updated_at
    ) values (p_account_id, p_product_id, new_status, p_valid_until, now(), now())
    on conflict (account_id) do update set
        product_id = excluded.product_id, status = excluded.status,
        valid_until = excluded.valid_until, verified_at = excluded.verified_at, updated_at = now();
end;
$$;

revoke all on function public.claim_verified_pro_purchase(uuid, text, text, text, timestamptz, text)
from public, anon, authenticated;
revoke all on function public.activate_claimed_pro_purchase(uuid, text, text, timestamptz, text)
from public, anon, authenticated;
revoke all on function public.revalidate_claimed_pro_purchase(uuid, text, text, boolean, timestamptz, text)
from public, anon, authenticated;
grant execute on function public.claim_verified_pro_purchase(uuid, text, text, text, timestamptz, text)
to service_role;
grant execute on function public.activate_claimed_pro_purchase(uuid, text, text, timestamptz, text)
to service_role;
grant execute on function public.revalidate_claimed_pro_purchase(uuid, text, text, boolean, timestamptz, text)
to service_role;
