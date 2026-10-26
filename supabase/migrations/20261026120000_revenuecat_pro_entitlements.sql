-- RevenueCat is the billing authority for new builds. Historical Play tables remain for audit.
create table public.revenuecat_customers (
    account_id uuid primary key references auth.users(id) on delete cascade,
    app_user_id text not null unique check (app_user_id ~ '^[0-9a-f]{64}$'),
    deletion_requested boolean not null default false,
    observed_at timestamptz
);
alter table public.revenuecat_customers enable row level security;
revoke all on public.revenuecat_customers from public, anon, authenticated;
grant all on public.revenuecat_customers to service_role;

create function public.register_revenuecat_customer(p_account_id uuid, p_app_user_id text)
returns void language plpgsql security definer set search_path = '' as $$
begin
    if p_account_id is null or p_app_user_id is null or p_app_user_id !~ '^[0-9a-f]{64}$' then
        raise exception using errcode = '22023', message = 'invalid RevenueCat identity';
    end if;
    insert into public.revenuecat_customers(account_id, app_user_id)
    values (p_account_id, p_app_user_id) on conflict (account_id) do nothing;
    if not exists (select 1 from public.revenuecat_customers where account_id = p_account_id and app_user_id = p_app_user_id and not deletion_requested) then
        raise exception using errcode = '42501', message = 'RevenueCat identity cannot be replaced';
    end if;
end;
$$;

create function public.sync_revenuecat_entitlement(
    p_account_id uuid, p_app_user_id text, p_product_id text, p_valid_until timestamptz, p_observed_at timestamptz
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
    last_observation timestamptz;
    stored public.pro_entitlements;
    new_status text;
begin
    if p_observed_at is null or p_observed_at > clock_timestamp() + interval '1 minute'
       or (p_product_id is not null and p_product_id not in ('aqualog_pro_annual', 'aqualog_pro_lifetime'))
       or (p_product_id = 'aqualog_pro_annual' and p_valid_until is null)
       or (p_product_id = 'aqualog_pro_lifetime' and p_valid_until is not null) then
        raise exception using errcode = '22023', message = 'invalid RevenueCat entitlement';
    end if;
    select observed_at into last_observation from public.revenuecat_customers
    where account_id = p_account_id and app_user_id = p_app_user_id and not deletion_requested for update;
    if not found then
        raise exception using errcode = '42501', message = 'unregistered RevenueCat account';
    end if;
    if last_observation is null or p_observed_at >= last_observation then
        if p_product_id is null then
            update public.pro_entitlements set status = 'inactive', verified_at = now(), updated_at = now()
            where account_id = p_account_id;
        else
            new_status := case when p_product_id = 'aqualog_pro_lifetime' or p_valid_until > clock_timestamp()
                then 'active' else 'inactive' end;
            insert into public.pro_entitlements(account_id, product_id, status, valid_until, verified_at, updated_at)
            values(p_account_id, p_product_id, new_status, p_valid_until, now(), now())
            on conflict(account_id) do update set product_id = excluded.product_id, status = excluded.status,
                valid_until = excluded.valid_until, verified_at = now(), updated_at = now();
        end if;
        update public.revenuecat_customers set observed_at = p_observed_at where account_id = p_account_id;
    end if;
    -- Return the latest database state, including when an older HTTP response was discarded.
    select * into stored from public.pro_entitlements where account_id = p_account_id;
    if not found or stored.status <> 'active'
       or (stored.product_id = 'aqualog_pro_annual' and stored.valid_until <= clock_timestamp()) then
        return jsonb_build_object('active', false);
    end if;
    return jsonb_build_object('active', true, 'productId', stored.product_id,
        'validUntilEpochMillis', case when stored.valid_until is null then null
            else floor(extract(epoch from stored.valid_until) * 1000)::bigint end);
end;
$$;

revoke all on function public.register_revenuecat_customer(uuid, text) from public, anon, authenticated;
revoke all on function public.sync_revenuecat_entitlement(uuid, text, text, timestamptz, timestamptz) from public, anon, authenticated;
grant execute on function public.register_revenuecat_customer(uuid, text) to service_role;
grant execute on function public.sync_revenuecat_entitlement(uuid, text, text, timestamptz, timestamptz) to service_role;
