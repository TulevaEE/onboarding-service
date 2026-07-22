CREATE INDEX idx_aml_check_type_created_time
    ON aml_check (type, created_time);

CREATE INDEX idx_mandate_mandate_batch_id
    ON mandate (mandate_batch_id);

CREATE INDEX idx_email_mandate_batch_id
    ON email (mandate_batch_id);

CREATE INDEX idx_payment_user_id
    ON payment (user_id);
