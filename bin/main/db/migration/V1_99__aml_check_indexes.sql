CREATE INDEX IF NOT EXISTS idx_aml_check_metadata_level
  ON public.aml_check (((metadata ->> 'level')::integer));

CREATE INDEX IF NOT EXISTS idx_aml_check_type_personal_code
  ON public.aml_check (type, personal_code);

CREATE INDEX IF NOT EXISTS idx_aml_check_confirmation_lookup
  ON public.aml_check (((metadata ->> 'confirmedOverrideId')::integer))
  WHERE type = 'RISK_LEVEL_OVERRIDE_CONFIRMATION';
