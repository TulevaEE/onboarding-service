CREATE TABLE savings_fund_onboarding
(
  user_id    integer references users (id) primary key,
  created_at timestamptz not null default now()
);
