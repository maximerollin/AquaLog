grant insert, update on public.aquariums to authenticated;
grant insert, update on public.parameter_definitions to authenticated;
grant insert, update on public.sessions to authenticated;
grant insert, update on public.measurements to authenticated;
grant insert, update on public.maintenance_actions to authenticated;
grant insert, update on public.session_events to authenticated;

create policy "owners insert their aquariums"
on public.aquariums for insert to authenticated
with check (account_id = (select auth.uid()));
create policy "owners update their aquariums"
on public.aquariums for update to authenticated
using (account_id = (select auth.uid()))
with check (account_id = (select auth.uid()));

create policy "owners insert their parameter definitions"
on public.parameter_definitions for insert to authenticated
with check (account_id = (select auth.uid()));
create policy "owners update their parameter definitions"
on public.parameter_definitions for update to authenticated
using (account_id = (select auth.uid()))
with check (account_id = (select auth.uid()));

create policy "owners insert their sessions"
on public.sessions for insert to authenticated
with check (account_id = (select auth.uid()));
create policy "owners update their sessions"
on public.sessions for update to authenticated
using (account_id = (select auth.uid()))
with check (account_id = (select auth.uid()));

create policy "owners insert their measurements"
on public.measurements for insert to authenticated
with check (account_id = (select auth.uid()));
create policy "owners update their measurements"
on public.measurements for update to authenticated
using (account_id = (select auth.uid()))
with check (account_id = (select auth.uid()));

create policy "owners insert their maintenance actions"
on public.maintenance_actions for insert to authenticated
with check (account_id = (select auth.uid()));
create policy "owners update their maintenance actions"
on public.maintenance_actions for update to authenticated
using (account_id = (select auth.uid()))
with check (account_id = (select auth.uid()));

create policy "owners insert their session events"
on public.session_events for insert to authenticated
with check (account_id = (select auth.uid()));
create policy "owners update their session events"
on public.session_events for update to authenticated
using (account_id = (select auth.uid()))
with check (account_id = (select auth.uid()));
