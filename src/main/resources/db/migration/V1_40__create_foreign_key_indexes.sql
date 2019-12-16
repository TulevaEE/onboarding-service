CREATE INDEX mandate_user_id_index
    ON mandate (user_id);

CREATE INDEX mandate_process_mandate_id_index
    ON mandate_process (mandate_id);

CREATE INDEX fund_transfer_exchange_mandate_id_index
    ON fund_transfer_exchange (mandate_id);

CREATE INDEX aml_check_user_id_index
    ON aml_check (user_id);

CREATE INDEX fund_fund_manager_id_index
    ON fund (fund_manager_id);

