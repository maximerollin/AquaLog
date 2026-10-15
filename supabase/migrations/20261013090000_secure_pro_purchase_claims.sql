drop function if exists public.record_verified_pro_purchase(uuid, text, text, text, timestamptz, text);

create or replace function public.claim_verified_pro_purchase(
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
    if p_account_id is null or p_product_id <> 'aqualog_pro_annual' or p_purchase_token_hash = '' then
        raise exception using errcode = '22023', message = 'invalid verified purchase claim';
    end if;

    insert into public.play_purchase_verifications as stored (
        purchase_token_hash, account_id, product_id, purchase_status, valid_until,
        latest_order_id, verified_at
    ) values (
        p_purchase_token_hash, p_account_id, p_product_id, 'inactive', p_valid_until,
        p_latest_order_id, now()
    )
    on conflict (purchase_token_hash) do update set
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
    if p_account_id is null or p_product_id <> 'aqualog_pro_annual' or p_purchase_token_hash = '' then
        raise exception using errcode = '22023', message = 'invalid verified purchase activation';
    end if;

    update public.play_purchase_verifications
    set purchase_status = 'active',
        valid_until = p_valid_until,
        latest_order_id = p_latest_order_id,
        verified_at = now()
    where purchase_token_hash = p_purchase_token_hash
      and account_id = p_account_id
      and product_id = p_product_id;

    if not found then
        raise exception using errcode = '42501', message = 'purchase claim belongs to another account';
    end if;

    insert into public.pro_entitlements as entitlement (
        account_id, product_id, status, valid_until, verified_at, updated_at
    ) values (
        p_account_id, p_product_id, 'active', p_valid_until, now(), now()
    )
    on conflict (account_id) do update set
        product_id = excluded.product_id,
        status = excluded.status,
        valid_until = excluded.valid_until,
        verified_at = excluded.verified_at,
        updated_at = now();
end;
$$;

revoke all on function public.claim_verified_pro_purchase(uuid, text, text, timestamptz, text)
from public, anon, authenticated;
revoke all on function public.activate_claimed_pro_purchase(uuid, text, text, timestamptz, text)
from public, anon, authenticated;
grant execute on function public.claim_verified_pro_purchase(uuid, text, text, timestamptz, text)
to service_role;
grant execute on function public.activate_claimed_pro_purchase(uuid, text, text, timestamptz, text)
to service_role;
