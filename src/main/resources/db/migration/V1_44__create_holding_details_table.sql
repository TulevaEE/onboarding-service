CREATE TABLE holding_details
(
    id                      SERIAL PRIMARY KEY NOT NULL,
    _id                     VARCHAR(255) NOT NULL,
    _external_id            VARCHAR(255) NOT NULL,
    symbol                  VARCHAR(255) NOT NULL,
    country_id              VARCHAR(255) NOT NULL,
    cusip                   VARCHAR(255) NOT NULL,
    currency_id             VARCHAR(255) NOT NULL,
    security_name           VARCHAR(255) NOT NULL,
    legal_type              VARCHAR(7)  NOT NULL,
    weighting               DECIMAL(10, 5) DEFAULT 0,
    number_of_share         BIGINT DEFAULT 0,
    share_change            BIGINT DEFAULT 0,
    market_value            BIGINT DEFAULT 0,
    sector                  INTEGER DEFAULT 0,
    holding_ytd_return      DECIMAL(10, 5) DEFAULT 0,
    region                  INTEGER DEFAULT 0,
    isin                    VARCHAR(255) NOT NULL,
    style_box               INTEGER DEFAULT 0,
    sedol                   VARCHAR(15) NOT NULL,
    first_bought_date       DATE,
    created_date            DATE NOT NULL
);