alter table public.play_purchase_verifications
add column purchase_token_ciphertext text;

drop function public.claim_verified_pro_purchase(uuid, text, text, timestamptz, text);

create function public.claim_verified_pro_purchase(
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
       or p_product_id <> 'aqualog_pro_annual'
       or p_purchase_token_hash = ''
       or p_purchase_token_ciphertext = '' then
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

create function public.revalidate_claimed_pro_purchase(
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
    new_status text := case when p_active then 'active' else 'inactive' end;
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

revoke all on function public.claim_verified_pro_purchase(uuid, text, text, text, timestamptz, text)
from public, anon, authenticated;
revoke all on function public.revalidate_claimed_pro_purchase(uuid, text, text, boolean, timestamptz, text)
from public, anon, authenticated;
grant execute on function public.claim_verified_pro_purchase(uuid, text, text, text, timestamptz, text)
to service_role;
grant execute on function public.revalidate_claimed_pro_purchase(uuid, text, text, boolean, timestamptz, text)
to service_role;
