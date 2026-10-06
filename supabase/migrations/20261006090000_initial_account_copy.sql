create table public.aquariums (
    id uuid primary key,
    account_id uuid not null references auth.users(id) on delete cascade,
    name text not null,
    volume double precision not null check (volume > 0),
    volume_unit text not null,
    created_at_epoch_millis bigint not null,
    profile text not null,
    unique (account_id, id)
);

create table public.parameter_definitions (
    id uuid primary key,
    account_id uuid not null references auth.users(id) on delete cascade,
    aquarium_id uuid not null,
    parameter text not null,
    is_active boolean not null,
    position integer not null check (position >= 0),
    unit text not null,
    precision integer not null check (precision between 0 and 3),
    indicative_minimum double precision,
    indicative_maximum double precision,
    unique (account_id, id),
    foreign key (account_id, aquarium_id) references public.aquariums(account_id, id) on delete cascade
);

create table public.sessions (
    id uuid primary key,
    account_id uuid not null references auth.users(id) on delete cascade,
    aquarium_id uuid not null,
    occurred_at_epoch_millis bigint not null,
    created_at_epoch_millis bigint not null,
    unique (account_id, id),
    foreign key (account_id, aquarium_id) references public.aquariums(account_id, id) on delete cascade
);

create table public.measurements (
    id uuid primary key,
    account_id uuid not null references auth.users(id) on delete cascade,
    session_id uuid not null,
    parameter_definition_id uuid not null,
    value double precision not null,
    foreign key (account_id, session_id) references public.sessions(account_id, id) on delete cascade,
    foreign key (account_id, parameter_definition_id) references public.parameter_definitions(account_id, id) on delete cascade
);

create table public.maintenance_actions (
    id uuid primary key,
    account_id uuid not null references auth.users(id) on delete cascade,
    session_id uuid not null,
    type text not null,
    quantity double precision,
    unit text,
    product text,
    foreign key (account_id, session_id) references public.sessions(account_id, id) on delete cascade
);

create table public.session_events (
    id uuid primary key,
    account_id uuid not null references auth.users(id) on delete cascade,
    session_id uuid not null,
    type text not null,
    note text not null,
    foreign key (account_id, session_id) references public.sessions(account_id, id) on delete cascade
);

alter table public.aquariums enable row level security;
alter table public.parameter_definitions enable row level security;
alter table public.sessions enable row level security;
alter table public.measurements enable row level security;
alter table public.maintenance_actions enable row level security;
alter table public.session_events enable row level security;

create policy "owners read their aquariums"
on public.aquariums for select to authenticated
using (account_id = (select auth.uid()));

create policy "owners read their parameter definitions"
on public.parameter_definitions for select to authenticated
using (account_id = (select auth.uid()));

create policy "owners read their sessions"
on public.sessions for select to authenticated
using (account_id = (select auth.uid()));

create policy "owners read their measurements"
on public.measurements for select to authenticated
using (account_id = (select auth.uid()));

create policy "owners read their maintenance actions"
on public.maintenance_actions for select to authenticated
using (account_id = (select auth.uid()));

create policy "owners read their session events"
on public.session_events for select to authenticated
using (account_id = (select auth.uid()));

revoke all on public.aquariums from anon, authenticated;
revoke all on public.parameter_definitions from anon, authenticated;
revoke all on public.sessions from anon, authenticated;
revoke all on public.measurements from anon, authenticated;
revoke all on public.maintenance_actions from anon, authenticated;
revoke all on public.session_events from anon, authenticated;

grant select on public.aquariums to authenticated;
grant select on public.parameter_definitions to authenticated;
grant select on public.sessions to authenticated;
grant select on public.measurements to authenticated;
grant select on public.maintenance_actions to authenticated;
grant select on public.session_events to authenticated;

create or replace function public.migrate_initial_copy(p_copy jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_account_id uuid := auth.uid();
begin
    if v_account_id is null then
        raise exception using errcode = '28000', message = 'authentication required';
    end if;
    if p_copy is null or jsonb_typeof(p_copy) <> 'object' then
        raise exception using errcode = '22023', message = 'initial copy must be a JSON object';
    end if;
    if nullif(p_copy->>'accountId', '')::uuid is distinct from v_account_id then
        raise exception using errcode = '42501', message = 'accountId must match the authenticated user';
    end if;

    if exists (
        select 1 from public.aquariums stored
        join jsonb_array_elements(coalesce(p_copy->'aquariums', '[]'::jsonb)) incoming
          on stored.id = (incoming->>'id')::uuid
        where stored.account_id <> v_account_id
    ) or exists (
        select 1 from public.parameter_definitions stored
        join jsonb_array_elements(coalesce(p_copy->'parameterDefinitions', '[]'::jsonb)) incoming
          on stored.id = (incoming->>'id')::uuid
        where stored.account_id <> v_account_id
    ) or exists (
        select 1 from public.sessions stored
        join jsonb_array_elements(coalesce(p_copy->'sessions', '[]'::jsonb)) incoming
          on stored.id = (incoming->>'id')::uuid
        where stored.account_id <> v_account_id
    ) or exists (
        select 1 from public.measurements stored
        join jsonb_array_elements(coalesce(p_copy->'measurements', '[]'::jsonb)) incoming
          on stored.id = (incoming->>'id')::uuid
        where stored.account_id <> v_account_id
    ) or exists (
        select 1 from public.maintenance_actions stored
        join jsonb_array_elements(coalesce(p_copy->'maintenanceActions', '[]'::jsonb)) incoming
          on stored.id = (incoming->>'id')::uuid
        where stored.account_id <> v_account_id
    ) or exists (
        select 1 from public.session_events stored
        join jsonb_array_elements(coalesce(p_copy->'events', '[]'::jsonb)) incoming
          on stored.id = (incoming->>'id')::uuid
        where stored.account_id <> v_account_id
    ) then
        raise exception using errcode = '42501', message = 'a local UUID already belongs to another account';
    end if;

    insert into public.aquariums as stored (
        id, account_id, name, volume, volume_unit, created_at_epoch_millis, profile
    )
    select id, v_account_id, name, volume, volume_unit, created_at_epoch_millis, profile
    from jsonb_to_recordset(coalesce(p_copy->'aquariums', '[]'::jsonb)) as incoming(
        id uuid,
        name text,
        volume double precision,
        "volumeUnit" text,
        "createdAtEpochMillis" bigint,
        profile text
    )
    cross join lateral (
        select incoming."volumeUnit" as volume_unit,
               incoming."createdAtEpochMillis" as created_at_epoch_millis
    ) mapped
    on conflict (id) do update set
        name = excluded.name,
        volume = excluded.volume,
        volume_unit = excluded.volume_unit,
        created_at_epoch_millis = excluded.created_at_epoch_millis,
        profile = excluded.profile
    where stored.account_id = v_account_id;

    insert into public.parameter_definitions as stored (
        id, account_id, aquarium_id, parameter, is_active, position, unit, precision,
        indicative_minimum, indicative_maximum
    )
    select id, v_account_id, aquarium_id, parameter, is_active, position, unit, precision,
           indicative_minimum, indicative_maximum
    from jsonb_to_recordset(coalesce(p_copy->'parameterDefinitions', '[]'::jsonb)) as incoming(
        id uuid,
        "aquariumId" uuid,
        parameter text,
        "isActive" boolean,
        position integer,
        unit text,
        precision integer,
        "indicativeMinimum" double precision,
        "indicativeMaximum" double precision
    )
    cross join lateral (
        select incoming."aquariumId" as aquarium_id,
               incoming."isActive" as is_active,
               incoming."indicativeMinimum" as indicative_minimum,
               incoming."indicativeMaximum" as indicative_maximum
    ) mapped
    on conflict (id) do update set
        aquarium_id = excluded.aquarium_id,
        parameter = excluded.parameter,
        is_active = excluded.is_active,
        position = excluded.position,
        unit = excluded.unit,
        precision = excluded.precision,
        indicative_minimum = excluded.indicative_minimum,
        indicative_maximum = excluded.indicative_maximum
    where stored.account_id = v_account_id;

    insert into public.sessions as stored (
        id, account_id, aquarium_id, occurred_at_epoch_millis, created_at_epoch_millis
    )
    select id, v_account_id, aquarium_id, occurred_at_epoch_millis, created_at_epoch_millis
    from jsonb_to_recordset(coalesce(p_copy->'sessions', '[]'::jsonb)) as incoming(
        id uuid,
        "aquariumId" uuid,
        "occurredAtEpochMillis" bigint,
        "createdAtEpochMillis" bigint
    )
    cross join lateral (
        select incoming."aquariumId" as aquarium_id,
               incoming."occurredAtEpochMillis" as occurred_at_epoch_millis,
               incoming."createdAtEpochMillis" as created_at_epoch_millis
    ) mapped
    on conflict (id) do update set
        aquarium_id = excluded.aquarium_id,
        occurred_at_epoch_millis = excluded.occurred_at_epoch_millis,
        created_at_epoch_millis = excluded.created_at_epoch_millis
    where stored.account_id = v_account_id;

    insert into public.measurements as stored (
        id, account_id, session_id, parameter_definition_id, value
    )
    select id, v_account_id, session_id, parameter_definition_id, value
    from jsonb_to_recordset(coalesce(p_copy->'measurements', '[]'::jsonb)) as incoming(
        id uuid,
        "sessionId" uuid,
        "parameterDefinitionId" uuid,
        value double precision
    )
    cross join lateral (
        select incoming."sessionId" as session_id,
               incoming."parameterDefinitionId" as parameter_definition_id
    ) mapped
    on conflict (id) do update set
        session_id = excluded.session_id,
        parameter_definition_id = excluded.parameter_definition_id,
        value = excluded.value
    where stored.account_id = v_account_id;

    insert into public.maintenance_actions as stored (
        id, account_id, session_id, type, quantity, unit, product
    )
    select id, v_account_id, session_id, type, quantity, unit, product
    from jsonb_to_recordset(coalesce(p_copy->'maintenanceActions', '[]'::jsonb)) as incoming(
        id uuid,
        "sessionId" uuid,
        type text,
        quantity double precision,
        unit text,
        product text
    )
    cross join lateral (select incoming."sessionId" as session_id) mapped
    on conflict (id) do update set
        session_id = excluded.session_id,
        type = excluded.type,
        quantity = excluded.quantity,
        unit = excluded.unit,
        product = excluded.product
    where stored.account_id = v_account_id;

    insert into public.session_events as stored (
        id, account_id, session_id, type, note
    )
    select id, v_account_id, session_id, type, note
    from jsonb_to_recordset(coalesce(p_copy->'events', '[]'::jsonb)) as incoming(
        id uuid,
        "sessionId" uuid,
        type text,
        note text
    )
    cross join lateral (select incoming."sessionId" as session_id) mapped
    on conflict (id) do update set
        session_id = excluded.session_id,
        type = excluded.type,
        note = excluded.note
    where stored.account_id = v_account_id;
end;
$$;

revoke all on function public.migrate_initial_copy(jsonb) from public, anon;
grant execute on function public.migrate_initial_copy(jsonb) to authenticated;
