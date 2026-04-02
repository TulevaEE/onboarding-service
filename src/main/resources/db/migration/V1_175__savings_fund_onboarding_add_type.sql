CREATE TABLE savings_fund_onboarding_new
(
  code       text        NOT NULL,
  type       text        NOT NULL,
  status     text        NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT savings_fund_onboarding_new_pkey PRIMARY KEY (code, type)
);

INSERT INTO savings_fund_onboarding_new (code, type, status, created_at)
SELECT TRIM(personal_code), 'PERSON', status, created_at
FROM savings_fund_onboarding
WHERE LENGTH(TRIM(personal_code)) = 11;

INSERT INTO savings_fund_onboarding_new (code, type, status, created_at)
SELECT TRIM(personal_code), 'LEGAL_ENTITY', status, created_at
FROM savings_fund_onboarding
WHERE LENGTH(TRIM(personal_code)) < 11;

DROP TABLE savings_fund_onboarding;

ALTER TABLE savings_fund_onboarding_new
  RENAME TO savings_fund_onboarding;

ALTER TABLE savings_fund_onboarding
  RENAME CONSTRAINT savings_fund_onboarding_new_pkey TO savings_fund_onboarding_pkey;
