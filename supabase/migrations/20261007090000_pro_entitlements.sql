create table public.play_purchase_verifications (
    purchase_token_hash text primary key,
    account_id uuid not null references auth.users(id) on delete cascade,
    product_id text not null,
    purchase_status text not null check (purchase_status in ('active', 'inactive')),
    valid_until timestamptz not null,
    latest_order_id text,
    verified_at timestamptz not null default now()
);

create table public.pro_entitlements (
    account_id uuid primary key references auth.users(id) on delete cascade,
    product_id text not null,
    status text not null check (status in ('active', 'inactive')),
    valid_until timestamptz not null,
    verified_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.play_purchase_verifications enable row level security;
alter table public.pro_entitlements enable row level security;

create policy "owners read their Pro entitlement"
on public.pro_entitlements for select to authenticated
using (account_id = (select auth.uid()));

revoke all on public.play_purchase_verifications from public, anon, authenticated;
revoke all on public.pro_entitlements from public, anon, authenticated;
grant select on public.pro_entitlements to authenticated;

create or replace function public.record_verified_pro_purchase(
    p_account_id uuid,
    p_product_id text,
    p_purchase_token_hash text,
    p_status text,
    p_valid_until timestamptz,
    p_latest_order_id text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_account_id is null or p_product_id <> 'aqualog_pro_annual' then
        raise exception using errcode = '22023', message = 'invalid verified purchase';
    end if;
    if p_status not in ('active', 'inactive') then
        raise exception using errcode = '22023', message = 'invalid purchase status';
    end if;

    insert into public.play_purchase_verifications as stored (
        purchase_token_hash, account_id, product_id, purchase_status, valid_until,
        latest_order_id, verified_at
    ) values (
        p_purchase_token_hash, p_account_id, p_product_id, p_status, p_valid_until,
        p_latest_order_id, now()
    )
    on conflict (purchase_token_hash) do update set
        purchase_status = excluded.purchase_status,
        valid_until = excluded.valid_until,
        latest_order_id = excluded.latest_order_id,
        verified_at = now()
    where stored.account_id = p_account_id and stored.product_id = p_product_id;

    if not found then
        raise exception using errcode = '42501', message = 'purchase token belongs to another account';
    end if;

    insert into public.pro_entitlements as entitlement (
        account_id, product_id, status, valid_until, verified_at, updated_at
    ) values (
        p_account_id, p_product_id, p_status, p_valid_until, now(), now()
    )
    on conflict (account_id) do update set
        product_id = excluded.product_id,
        status = excluded.status,
        valid_until = excluded.valid_until,
        verified_at = excluded.verified_at,
        updated_at = now();
end;
$$;

revoke all on function public.record_verified_pro_purchase(uuid, text, text, text, timestamptz, text)
from public, anon, authenticated;
grant execute on function public.record_verified_pro_purchase(uuid, text, text, text, timestamptz, text)
to service_role;
