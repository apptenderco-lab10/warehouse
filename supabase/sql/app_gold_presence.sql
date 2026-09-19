-- APP Gold manager presence support
-- The current client already calls API endpoints every 15 seconds.
-- private.is_active_user() records a throttled last_seen timestamp as a side effect.

create table if not exists public.user_presence (
  user_id uuid primary key references auth.users(id) on delete cascade,
  first_seen timestamptz not null default now(),
  last_seen timestamptz not null default now()
);

alter table public.user_presence enable row level security;

revoke all on table public.user_presence from anon;
revoke insert, update, delete on table public.user_presence from authenticated;
grant select on table public.user_presence to authenticated;

create or replace function private.is_active_user()
returns boolean
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
  v_active boolean := false;
  v_now timestamptz := clock_timestamp();
begin
  if v_uid is null then
    return false;
  end if;

  select exists(
    select 1
    from public.profiles
    where id = v_uid
      and active = true
  ) into v_active;

  if v_active then
    insert into public.user_presence(user_id, first_seen, last_seen)
    values (v_uid, v_now, v_now)
    on conflict (user_id) do update
      set last_seen = excluded.last_seen
      where public.user_presence.last_seen < (excluded.last_seen - interval '20 seconds');
  end if;

  return v_active;
end;
$$;

create or replace function private.current_app_role()
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(
    (
      select role
      from public.profiles
      where id = (select auth.uid())
        and active = true
    ),
    'employee'
  );
$$;

drop policy if exists user_presence_admin_select on public.user_presence;
create policy user_presence_admin_select
on public.user_presence
for select
to authenticated
using (
  private.is_active_user()
  and private.current_app_role() = 'admin'
);
