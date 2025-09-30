CREATE TABLE IF NOT EXISTS saving_fund_payment
(
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  end_to_end_id VARCHAR(35),
  external_id TEXT NOT NULL UNIQUE,
  amount DECIMAL(12,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  description TEXT,
  remitter_iban VARCHAR(34),
  remitter_id_code TEXT,
  remitter_name TEXT,
  beneficiary_iban VARCHAR(34),
  beneficiary_id_code TEXT,
  beneficiary_name TEXT,
  received_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
