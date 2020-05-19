CREATE TABLE holding_details
(
    id                      SERIAL PRIMARY KEY NOT NULL,
    symbol                  VARCHAR(255) NOT NULL,
    country                 VARCHAR(255) NOT NULL,
    currency                VARCHAR(255) NOT NULL,
    security_name           VARCHAR(255) NOT NULL,
    weighting               DECIMAL(10, 5) DEFAULT 0,
    number_of_share         BIGINT DEFAULT 0,
    share_change            BIGINT DEFAULT 0,
    market_value            BIGINT DEFAULT 0,
    sector                  INTEGER DEFAULT 0,
    holding_ytd_return      DECIMAL(10, 5) DEFAULT 0,
    region                  INTEGER DEFAULT 0,
    isin                    VARCHAR(255) NOT NULL,
    style_box               INTEGER DEFAULT 0,
    first_bought_date       DATE,
    created_date            DATE NOT NULL
);