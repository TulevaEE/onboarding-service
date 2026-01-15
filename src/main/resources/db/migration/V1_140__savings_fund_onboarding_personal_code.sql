CREATE TABLE savings_fund_onboarding_new
(
    personal_code char(11) not null,
    created_at    timestamptz not null default now(),
    status        text not null,
    constraint savings_fund_onboarding_new_pkey primary key (personal_code)
);

INSERT INTO savings_fund_onboarding_new (personal_code, created_at, status)
SELECT users.personal_code, savings_fund_onboarding.created_at, savings_fund_onboarding.status
FROM savings_fund_onboarding
         JOIN users ON savings_fund_onboarding.user_id = users.id;

DROP TABLE savings_fund_onboarding;

ALTER TABLE savings_fund_onboarding_new RENAME TO savings_fund_onboarding;

ALTER INDEX savings_fund_onboarding_new_pkey RENAME TO savings_fund_onboarding_pkey;