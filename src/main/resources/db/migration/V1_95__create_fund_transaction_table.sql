CREATE TABLE public.fund_transaction (
                                       id BIGSERIAL PRIMARY KEY,
                                       transaction_date DATE NOT NULL,
                                       isin VARCHAR(255),
                                       person_name VARCHAR(255),
                                       personal_id VARCHAR(255) NOT NULL,
                                       pension_account VARCHAR(255),
                                       country VARCHAR(255),
                                       transaction_type VARCHAR(255) NOT NULL,
                                       purpose VARCHAR(255),
                                       application_type VARCHAR(255),
                                       unit_amount NUMERIC(19, 8) NOT NULL,
                                       price NUMERIC(19, 8),
                                       nav NUMERIC(19, 8),
                                       amount NUMERIC(19, 2) NOT NULL,
                                       service_fee NUMERIC(19, 2),
                                       date_created TIMESTAMP NOT NULL,
                                       CONSTRAINT fund_transaction_unique_key UNIQUE (transaction_date, personal_id, transaction_type, amount, unit_amount)
);

CREATE INDEX IF NOT EXISTS idx_fund_transaction_personal_id ON public.fund_transaction(personal_id);
CREATE INDEX IF NOT EXISTS idx_fund_transaction_isin_date ON public.fund_transaction(isin, transaction_date);
CREATE INDEX IF NOT EXISTS idx_fund_transaction_date ON public.fund_transaction(transaction_date);
