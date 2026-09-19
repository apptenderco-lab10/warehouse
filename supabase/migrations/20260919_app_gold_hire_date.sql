alter table public.profiles
  add column if not exists hire_date date;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'profiles_hire_date_check'
      and conrelid = 'public.profiles'::regclass
  ) then
    alter table public.profiles
      add constraint profiles_hire_date_check
      check (hire_date is null or hire_date <= current_date);
  end if;
end $$;