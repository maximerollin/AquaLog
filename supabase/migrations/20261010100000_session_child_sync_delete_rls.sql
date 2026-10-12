grant delete on public.measurements to authenticated;
grant delete on public.maintenance_actions to authenticated;
grant delete on public.session_events to authenticated;

create policy "owners delete their measurements"
on public.measurements for delete to authenticated
using (account_id = (select auth.uid()));

create policy "owners delete their maintenance actions"
on public.maintenance_actions for delete to authenticated
using (account_id = (select auth.uid()));

create policy "owners delete their session events"
on public.session_events for delete to authenticated
using (account_id = (select auth.uid()));
