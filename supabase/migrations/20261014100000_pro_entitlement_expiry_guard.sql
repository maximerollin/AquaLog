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
    if p_valid_until is null or p_valid_until <= clock_timestamp() then
        raise exception using errcode = '22023', message = 'active entitlement must expire in the future';
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
        when p_active and p_valid_until > clock_timestamp() then 'active'
        else 'inactive'
    end;
begin
    if p_account_id is null or p_product_id <> 'aqualog_pro_annual' or p_purchase_token_hash = '' then
        raise exception using errcode = '22023', message = 'invalid entitlement revalidation';
    end if;

    update public.play_purchase_verifications
    set purchase_status = new_status,
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
        p_account_id, p_product_id, new_status, p_valid_until, now(), now()
    )
    on conflict (account_id) do update set
        product_id = excluded.product_id,
        status = excluded.status,
        valid_until = excluded.valid_until,
        verified_at = excluded.verified_at,
        updated_at = now();
end;
$$;
