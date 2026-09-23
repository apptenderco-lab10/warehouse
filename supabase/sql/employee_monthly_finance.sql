-- APP Gold monthly advance/loan totals.
-- Current month is a Jalali month key such as 1405/07.
-- Employees can only read their own row. Managers/admins can read and write.

create table if not exists public.employee_monthly_finance (
  user_id uuid not null references auth.users(id) on delete cascade,
  month_key text not null,
  advance_amount numeric(18,0) not null default 0,
  loan_amount numeric(18,0) not null default 0,
  updated_by uuid references auth.users(id),
  updated_at timestamptz not null default now(),
  primary key (user_id, month_key),
  constraint employee_monthly_finance_month_key_chk
    check (month_key ~ '^[0-9]{4}/(0[1-9]|1[0-2])$'),
  constraint employee_monthly_finance_advance_nonnegative_chk
    check (advance_amount >= 0),
  constraint employee_monthly_finance_loan_nonnegative_chk
    check (loan_amount >= 0)
);

alter table public.employee_monthly_finance enable row level security;

revoke all on table public.employee_monthly_finance from anon;
revoke delete on table public.employee_monthly_finance from authenticated;
grant select, insert, update on table public.employee_monthly_finance to authenticated;

drop policy if exists employee_monthly_finance_select on public.employee_monthly_finance;
create policy employee_monthly_finance_select
on public.employee_monthly_finance
for select
to authenticated
using (
  private.is_active_user()
  and (
    user_id = (select auth.uid())
    or private.current_app_role() in ('manager','admin')
  )
);

drop policy if exists employee_monthly_finance_insert on public.employee_monthly_finance;
create policy employee_monthly_finance_insert
on public.employee_monthly_finance
for insert
to authenticated
with check (
  private.is_active_user()
  and private.current_app_role() in ('manager','admin')
  and updated_by = (select auth.uid())
);

drop policy if exists employee_monthly_finance_update on public.employee_monthly_finance;
create policy employee_monthly_finance_update
on public.employee_monthly_finance
for update
to authenticated
using (
  private.is_active_user()
  and private.current_app_role() in ('manager','admin')
)
with check (
  private.is_active_user()
  and private.current_app_role() in ('manager','admin')
  and updated_by = (select auth.uid())
);
