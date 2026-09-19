-- APP Gold rewards and personnel work sections
alter table public.profiles
  add column if not exists work_sections text[] not null default '{}'::text[],
  add column if not exists reward_level text not null default 'none',
  add column if not exists reward_score integer not null default 0,
  add column if not exists reward_updated_at timestamptz,
  add column if not exists reward_updated_by uuid references auth.users(id);

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'profiles_work_sections_check'
      and conrelid = 'public.profiles'::regclass
  ) then
    alter table public.profiles
      add constraint profiles_work_sections_check
      check (
        cardinality(work_sections) <= 2
        and work_sections <@ array[
          'اداری',
          'تولید تیوب نسوز',
          'تولید هد سمپلر',
          'اسمبل',
          'تولید کابل',
          'تولید لنس'
        ]::text[]
      );
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'profiles_reward_level_check'
      and conrelid = 'public.profiles'::regclass
  ) then
    alter table public.profiles
      add constraint profiles_reward_level_check
      check (reward_level in ('none','bronze','silver','gold'));
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'profiles_reward_score_check'
      and conrelid = 'public.profiles'::regclass
  ) then
    alter table public.profiles
      add constraint profiles_reward_score_check
      check (reward_score >= 0);
  end if;
end $$;

create table if not exists public.reward_history (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  previous_level text not null,
  new_level text not null,
  previous_score integer not null,
  new_score integer not null,
  previous_sections text[] not null default '{}'::text[],
  new_sections text[] not null default '{}'::text[],
  changed_by uuid references auth.users(id),
  created_at timestamptz not null default now()
);

alter table public.reward_history enable row level security;

grant select, insert on public.reward_history to authenticated;
revoke update, delete on public.reward_history from authenticated;

drop policy if exists reward_history_select_own_or_management on public.reward_history;
create policy reward_history_select_own_or_management
on public.reward_history
for select
to authenticated
using (
  private.is_active_user()
  and (
    user_id = (select auth.uid())
    or private.current_app_role() = any (array['manager'::text, 'admin'::text])
  )
);

drop policy if exists reward_history_admin_insert on public.reward_history;
create policy reward_history_admin_insert
on public.reward_history
for insert
to authenticated
with check (
  private.is_active_user()
  and private.current_app_role() = 'admin'::text
  and changed_by = (select auth.uid())
);

create index if not exists reward_history_user_created_idx
  on public.reward_history(user_id, created_at desc);

create or replace function private.log_profile_reward_change()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if old.reward_level is distinct from new.reward_level
     or old.reward_score is distinct from new.reward_score
     or old.work_sections is distinct from new.work_sections then
    insert into public.reward_history (
      user_id,
      previous_level,
      new_level,
      previous_score,
      new_score,
      previous_sections,
      new_sections,
      changed_by
    )
    values (
      new.id,
      old.reward_level,
      new.reward_level,
      old.reward_score,
      new.reward_score,
      old.work_sections,
      new.work_sections,
      (select auth.uid())
    );
  end if;
  return new;
end;
$$;

drop trigger if exists profiles_reward_history_trg on public.profiles;
create trigger profiles_reward_history_trg
after update of reward_level, reward_score, work_sections
on public.profiles
for each row
execute function private.log_profile_reward_change();

-- Important security hardening:
-- users should not be able to promote themselves or change their reward data.
drop policy if exists profiles_update_self_or_admin on public.profiles;
drop policy if exists profiles_update_admin_only on public.profiles;
create policy profiles_update_admin_only
on public.profiles
for update
to authenticated
using (
  private.is_active_user()
  and private.current_app_role() = 'admin'::text
)
with check (
  private.is_active_user()
  and private.current_app_role() = 'admin'::text
);
