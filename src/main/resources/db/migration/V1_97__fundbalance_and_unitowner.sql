CREATE TABLE fund_balance (
                            id BIGSERIAL PRIMARY KEY,
                            security_name VARCHAR(255),
                            isin VARCHAR(255),
                            nav NUMERIC(19, 8),
                            balance NUMERIC(19, 2),
                            count_investors INTEGER,
                            count_units NUMERIC(19, 8),
                            count_units_bron NUMERIC(19, 8),
                            count_units_free NUMERIC(19, 8),
                            count_units_arest NUMERIC(19, 8),
                            count_units_fm NUMERIC(19, 8),
                            fund_manager VARCHAR(255),
                            request_date DATE NOT NULL,
                            date_created TIMESTAMP NOT NULL
);

CREATE INDEX idx_fund_balance_request_date ON fund_balance(request_date);
CREATE INDEX idx_fund_balance_isin ON fund_balance(isin);

CREATE TABLE unit_owner (
                          id BIGSERIAL PRIMARY KEY,
                          personal_id VARCHAR(255) NOT NULL,
                          first_name VARCHAR(255),
                          last_name VARCHAR(255),
                          phone VARCHAR(255),
                          email VARCHAR(255),
                          country VARCHAR(255),
                          language_preference VARCHAR(255),
                          pension_account VARCHAR(255),
                          death_date DATE,
                          fund_manager VARCHAR(255),
  -- Pillar 2 fields
                          p2_choice VARCHAR(255),
                          p2_choice_method VARCHAR(255),
                          p2_choice_date DATE,
                          p2_rava_date DATE,
                          p2_rava_status VARCHAR(255),
                          p2_mmte_date DATE,
                          p2_mmte_status VARCHAR(255),
                          p2_rate INTEGER,
                          p2_next_rate INTEGER,
                          p2_next_rate_date DATE,
                          p2_ykva_date DATE,
                          p2_plav_date DATE,
                          p2_fpaa_date DATE,
                          p2_duty_start DATE,
                          p2_duty_end DATE,
  -- Pillar 3 fields
                          p3_identification_date DATE,
                          p3_identifier VARCHAR(255),
                          p3_block_flag VARCHAR(255),
                          p3_blocker VARCHAR(255),
                          date_created TIMESTAMP NOT NULL,
                          snapshot_date DATE NOT NULL,

                          CONSTRAINT uq_unit_owner_personal_id_snapshot_date UNIQUE (personal_id, snapshot_date)
);

CREATE INDEX idx_unit_owner_fund_manager ON unit_owner(fund_manager);
CREATE INDEX idx_unit_owner_snapshot_date ON unit_owner(snapshot_date);

CREATE TABLE unit_owner_balance (
                                  unit_owner_id BIGINT NOT NULL,
                                  security_short_name VARCHAR(255),
                                  security_name VARCHAR(255),
                                  balance_type VARCHAR(255),
                                  balance_amount NUMERIC(19, 8),
                                  start_date DATE,
                                  last_updated DATE,
                                  CONSTRAINT fk_unit_owner_balance_owner
                                    FOREIGN KEY(unit_owner_id)
                                      REFERENCES unit_owner(id)
                                      ON DELETE CASCADE
);

CREATE INDEX idx_unit_owner_balance_owner_id ON unit_owner_balance(unit_owner_id);
