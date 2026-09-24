-- APP 20: employee loan schedules and private manager messages.
-- Employees can read only their own data. Managers/admins can manage records.

create table if not exists public.employee_loans (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  total_amount numeric(18,0) not null check (total_amount > 0),
  receive_month text not null check (receive_month ~ '^[0-9]{4}/(0[1-9]|1[0-2])$'),
  first_deduction_month text not null check (first_deduction_month ~ '^[0-9]{4}/(0[1-9]|1[0-2])$'),
  last_deduction_month text not null check (last_deduction_month ~ '^[0-9]{4}/(0[1-9]|1[0-2])$'),
  status text not null default 'active' check (status in ('active','settled','cancelled')),
  note text not null default '',
  created_by uuid references auth.users(id),
  updated_by uuid references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint employee_loans_month_order_chk check (first_deduction_month <= last_deduction_month)
);

create index if not exists employee_loans_user_idx on public.employee_loans(user_id);
create index if not exists employee_loans_status_idx on public.employee_loans(status);
create index if not exists employee_loans_user_status_idx on public.employee_loans(user_id,status);

alter table public.employee_loans enable row level security;
revoke all on table public.employee_loans from anon;
grant select, insert, update, delete on table public.employee_loans to authenticated;

drop policy if exists employee_loans_select on public.employee_loans;
create policy employee_loans_select on public.employee_loans for select to authenticated
using (
  private.is_active_user()
  and (user_id=(select auth.uid()) or private.current_app_role() in ('manager','admin'))
);

drop policy if exists employee_loans_insert on public.employee_loans;
create policy employee_loans_insert on public.employee_loans for insert to authenticated
with check (
  private.is_active_user()
  and private.current_app_role() in ('manager','admin')
  and created_by=(select auth.uid())
  and updated_by=(select auth.uid())
);

drop policy if exists employee_loans_update on public.employee_loans;
create policy employee_loans_update on public.employee_loans for update to authenticated
using (private.is_active_user() and private.current_app_role() in ('manager','admin'))
with check (
  private.is_active_user()
  and private.current_app_role() in ('manager','admin')
  and updated_by=(select auth.uid())
);

drop policy if exists employee_loans_delete on public.employee_loans;
create policy employee_loans_delete on public.employee_loans for delete to authenticated
using (private.is_active_user() and private.current_app_role() in ('manager','admin'));

create table if not exists public.employee_personal_messages (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  message_type text not null default 'info' check (message_type in ('reward','praise','warning','info')),
  title text not null check (char_length(trim(title)) between 1 and 120),
  body text not null check (char_length(trim(body)) between 1 and 1200),
  starts_on date not null default current_date,
  ends_on date,
  active boolean not null default true,
  created_by uuid references auth.users(id),
  updated_by uuid references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint employee_personal_messages_date_chk check (ends_on is null or ends_on >= starts_on)
);

create index if not exists employee_personal_messages_user_idx on public.employee_personal_messages(user_id);
create index if not exists employee_personal_messages_active_idx on public.employee_personal_messages(user_id,active,starts_on,ends_on);

alter table public.employee_personal_messages enable row level security;
revoke all on table public.employee_personal_messages from anon;
grant select, insert, update, delete on table public.employee_personal_messages to authenticated;

drop policy if exists employee_personal_messages_select on public.employee_personal_messages;
create policy employee_personal_messages_select on public.employee_personal_messages for select to authenticated
using (
  private.is_active_user()
  and (
    private.current_app_role() in ('manager','admin')
    or (
      user_id=(select auth.uid())
      and active=true
      and starts_on<=current_date
      and (ends_on is null or ends_on>=current_date)
    )
  )
);

drop policy if exists employee_personal_messages_insert on public.employee_personal_messages;
create policy employee_personal_messages_insert on public.employee_personal_messages for insert to authenticated
with check (
  private.is_active_user()
  and private.current_app_role() in ('manager','admin')
  and created_by=(select auth.uid())
  and updated_by=(select auth.uid())
);

drop policy if exists employee_personal_messages_update on public.employee_personal_messages;
create policy employee_personal_messages_update on public.employee_personal_messages for update to authenticated
using (private.is_active_user() and private.current_app_role() in ('manager','admin'))
with check (
  private.is_active_user()
  and private.current_app_role() in ('manager','admin')
  and updated_by=(select auth.uid())
);

drop policy if exists employee_personal_messages_delete on public.employee_personal_messages;
create policy employee_personal_messages_delete on public.employee_personal_messages for delete to authenticated
using (private.is_active_user() and private.current_app_role() in ('manager','admin'));
