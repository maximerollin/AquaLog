create table public.logsnag_deliveries (
    event_hash text primary key check(event_hash ~ '^[0-9a-f]{64}$'),
    status text not null default 'pending' check(status in ('pending','sent')),
    lease_until timestamptz
);
alter table public.logsnag_deliveries enable row level security;
revoke all on public.logsnag_deliveries from public, anon, authenticated;
grant all on public.logsnag_deliveries to service_role;
create function public.claim_logsnag_delivery(p_event_hash text) returns text
language plpgsql security definer set search_path = '' as $$
declare item public.logsnag_deliveries;
begin
    insert into public.logsnag_deliveries(event_hash) values(p_event_hash) on conflict do nothing;
    select * into item from public.logsnag_deliveries where event_hash=p_event_hash for update;
    if item.status='sent' then return 'sent'; end if;
    if item.lease_until > clock_timestamp() then return 'busy'; end if;
    update public.logsnag_deliveries set lease_until=clock_timestamp()+interval '90 seconds' where event_hash=p_event_hash;
    return 'claimed';
end;
$$;
create function public.complete_logsnag_delivery(p_event_hash text, p_delivered boolean) returns void
language sql security definer set search_path = '' as $$
    update public.logsnag_deliveries set status=case when p_delivered then 'sent' else 'pending' end, lease_until=null
    where event_hash=p_event_hash and status <> 'sent';
$$;
revoke all on function public.claim_logsnag_delivery(text) from public, anon, authenticated;
revoke all on function public.complete_logsnag_delivery(text,boolean) from public, anon, authenticated;
grant execute on function public.claim_logsnag_delivery(text) to service_role;
grant execute on function public.complete_logsnag_delivery(text,boolean) to service_role;
