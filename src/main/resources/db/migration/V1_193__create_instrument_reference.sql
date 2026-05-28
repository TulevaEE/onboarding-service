CREATE TABLE instrument_reference (
    id            bigserial    NOT NULL,
    isin          varchar(12)  NOT NULL,
    display_name  text         NOT NULL,
    fund_manager  text,
    country       varchar(2),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT instrument_reference_pkey PRIMARY KEY (id),
    CONSTRAINT instrument_reference_isin_unique UNIQUE (isin)
);

INSERT INTO instrument_reference (isin, display_name, fund_manager, country) VALUES
-- TUK75 / TUV100 equity instruments
('IE0009FT4LX4', 'CCF Developed World (Screened) Index Fund',                    'BlackRock Asset Management Ireland Ltd', 'IE'),
('IE00BFG1TM61', 'BlackRock ISF - Developed World Screened Index',               'BlackRock Asset Management Ireland Ltd', 'IE'),
('IE00BKPTWY98', 'iShares Emerging Market Screened Equity Index Fund (IE)',       'BlackRock Asset Management Ireland Ltd', 'IE'),
('IE00BFNM3D14', 'iShares MSCI Europe Screened UCITS ETF',                       'BlackRock Asset Management Ireland Ltd', 'IE'),
('IE00BFNM3L97', 'iShares MSCI Japan Screened UCITS ETF',                        'BlackRock Asset Management Ireland Ltd', 'IE'),
('IE00BFNM3G45', 'iShares MSCI USA Screened UCITS ETF',                          'BlackRock Asset Management Ireland Ltd', 'IE'),
-- TUK00 bond instruments
('LU0826455353', 'BlackRock BGIF - Euro Aggregate Bond Index Fund - X2',          'Blackrock Luxembourg SA',                'LU'),
('IE0005032192', 'BlackRock FIDF - Euro Credit Bond Index Fund - Flexible',       'BlackRock Asset Management Ireland Ltd', 'IE'),
('IE0031080751', 'BlackRock FIDF - Euro Government Bond Index Fund - Flexible',   'BlackRock Asset Management Ireland Ltd', 'IE'),
('LU0839970364', 'BlackRock BGIF - Global Government Bond Index - X2',            'Blackrock Luxembourg SA',                'LU'),
-- TKF100 instruments
('IE00BMDBMY19', 'Invesco MSCI Emerging Markets Universal Screened UCITS ETF Acc','Invesco Investment Management Limited',       'IE'),
('IE00BJZ2DC62', 'Xtrackers MSCI USA Screened UCITS ETF',                         'DWS Investment S.A.',                         'IE'),
('LU0476289540', 'Xtrackers MSCI Canada Screened UCITS ETF',                      'DWS Investment S.A.',                         'LU'),
('IE000F60HVH9', 'ICAV Amundi MSCI USA Screened UCITS ETF',                       'Amundi Ireland Limited',                      'IE'),
('IE000O58J820', 'Vanguard ESG North America All Cap UCITS ETF',                  'Vanguard Group (Ireland) Limited',            'IE'),
('LU1291099718', 'BNP Paribas Easy MSCI EUROPE MIN TE UCITS ETF',                'BNP Paribas Asset Management Luxembourg',     'LU'),
('LU1291106356', 'BNP Paribas Easy MSCI Pacific ex Japan Min TE UCITS ETF',      'BNP Paribas Asset Management Luxembourg',     'LU'),
('LU1291102447', 'BNP Paribas Easy MSCI Japan Min TE UCITS ETF',                 'BNP Paribas Asset Management Luxembourg',     'LU');
