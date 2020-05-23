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
    sector                  VARCHAR(31) NOT NULL,
    holding_ytd_return      DECIMAL(10, 5) DEFAULT 0,
    region                  VARCHAR(31) NOT NULL,
    isin                    VARCHAR(255) NOT NULL,
    first_bought_date       DATE,
    created_date            DATE NOT NULL
);