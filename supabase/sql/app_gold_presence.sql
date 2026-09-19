-- APP Gold manager online-presence backend.
-- Safe design: presence is recorded by PostgREST's db_pre_request hook.
-- It does NOT modify or add side effects to RLS authorization functions.

create table if not exists public.user_presence (
  user_id uuid primary key references auth.users(id) on delete cascade,
  first_seen timestamptz not null default now(),
  last_seen timestamptz not null default now()
);

alter table public.user_presence enable row level security;

revoke all on table public.user_presence from anon;
revoke insert, update, delete on table public.user_presence from authenticated;
grant select on table public.user_presence to authenticated;

drop policy if exists user_presence_admin_select on public.user_presence;
create policy user_presence_admin_select
on public.user_presence
for select
to authenticated
using (
  private.is_active_user()
  and private.current_app_role() = 'admin'
);

create or replace function public.apphr_touch_presence()
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
  v_now timestamptz := clock_timestamp();
begin
  if v_uid is null then
    return;
  end if;

  insert into public.user_presence(user_id, first_seen, last_seen)
  values (v_uid, v_now, v_now)
  on conflict (user_id) do update
    set last_seen = excluded.last_seen
    where public.user_presence.last_seen < (excluded.last_seen - interval '20 seconds');
end;
$$;

revoke all on function public.apphr_touch_presence() from public;
revoke all on function public.apphr_touch_presence() from anon;
grant execute on function public.apphr_touch_presence() to authenticated;

alter role authenticator
  set pgrst.db_pre_request = 'public.apphr_touch_presence';

notify pgrst, 'reload config';
