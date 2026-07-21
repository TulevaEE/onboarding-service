CREATE TABLE kyb_check_override
(
  id             uuid        primary key,
  registry_code  text        not null,
  check_type     text        not null,
  forced_success boolean     not null,
  reason         text        not null,
  created_by     text        not null,
  created_time   timestamptz not null default now(),
  constraint kyb_check_override_registry_code_check_type_key unique (registry_code, check_type)
);
