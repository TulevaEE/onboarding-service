-- =============================================================================
-- instrument_reference: central instrument master table
-- Replaces FundTicker.java enum. One ISIN, one row, all metadata.
-- =============================================================================

CREATE TABLE instrument_reference (
    id                   bigserial    NOT NULL,
    isin                 varchar(12)  NOT NULL,
    display_name         text         NOT NULL,
    seb_position_name    text,
    fund_manager         text,
    country              varchar(2),
    instrument_type      text,
    asset_class          text,
    yahoo_ticker         text,
    eodhd_ticker         text,
    bloomberg_ticker     text,
    ric                  text,
    morningstar_id       text,
    blackrock_product_id text,
    benchmark_category   varchar(20),
    active               boolean      NOT NULL DEFAULT true,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT instrument_reference_pkey PRIMARY KEY (id),
    CONSTRAINT instrument_reference_isin_unique UNIQUE (isin)
);

-- =============================================================================
-- Seed: portfolio instruments (TUK75/TUV100 equity)
-- =============================================================================

INSERT INTO instrument_reference (isin, display_name, fund_manager, country, instrument_type, asset_class, yahoo_ticker, eodhd_ticker, bloomberg_ticker, ric, morningstar_id, blackrock_product_id, benchmark_category) VALUES
('IE00BFG1TM61', 'iShares Developed World Screened Index Fund',                    'BlackRock Asset Management Ireland Ltd', 'IE', 'FUND', 'equity', '0P000152G5.F',  'IE00BFG1TM61.EUFUND', 'BDWTEIA',  NULL,      '0P000152G5', '270890', 'EQUITY_DM'),
('IE0009FT4LX4', 'CCF Developed World Screened Index Fund',                        'BlackRock Asset Management Ireland Ltd', 'IE', 'FUND', 'equity', '0P0001N0Z0.F',  'IE0009FT4LX4.EUFUND', 'BLESIXE',  NULL,      '0P0001N0Z0', '320377', 'EQUITY_DM'),
('IE00BFNM3G45', 'iShares MSCI USA Screened UCITS ETF',                            'BlackRock Asset Management Ireland Ltd', 'IE', 'ETF',  'equity', 'SGAS.DE',       'SGAS.XETRA',          'SGAS',     'SGAS.DE', NULL,         NULL,     'EQUITY_DM'),
('IE00BFNM3D14', 'iShares MSCI Europe Screened UCITS ETF',                         'BlackRock Asset Management Ireland Ltd', 'IE', 'ETF',  'equity', 'SLMC.DE',       'SLMC.XETRA',          'SLMC',     'SLMC.DE', NULL,         NULL,     'EQUITY_DM'),
('IE00BFNM3L97', 'iShares MSCI Japan Screened UCITS ETF',                          'BlackRock Asset Management Ireland Ltd', 'IE', 'ETF',  'equity', 'SGAJ.DE',       'SGAJ.XETRA',          'SGAJ',     'SGAJ.DE', NULL,         NULL,     'EQUITY_DM'),
('IE00BKPTWY98', 'iShares Emerging Market Screened Equity Index Fund (IE)',         'BlackRock Asset Management Ireland Ltd', 'IE', 'FUND', 'equity', '0P0001MGOG.F',  'IE00BKPTWY98.EUFUND', 'BEMEFLE',  NULL,      '0P0001MGOG', '316651', 'EQUITY_EM');

-- =============================================================================
-- Seed: portfolio instruments (TUK00 bonds)
-- =============================================================================

INSERT INTO instrument_reference (isin, display_name, fund_manager, country, instrument_type, asset_class, yahoo_ticker, eodhd_ticker, bloomberg_ticker, ric, morningstar_id, blackrock_product_id, benchmark_category) VALUES
('LU0826455353', 'iShares Euro Aggregate Bond Index Fund',                          'Blackrock Luxembourg SA',                'LU', 'FUND', 'bond', '0P0000YXER.F',  'LU0826455353.EUFUND', 'BGIEAX2',  NULL,      '0P0000YXER', '254318', 'BOND_EURO'),
('IE0031080751', 'iShares Euro Government Bond Index Fund',                         'BlackRock Asset Management Ireland Ltd', 'IE', 'FUND', 'bond', '0P00006OK2.F',  'IE0031080751.EUFUND', 'BARGVBI',  NULL,      '0P00006OK2', '229062', 'BOND_EURO'),
('LU0839970364', 'iShares Global Government Bond Index Fund',                       'Blackrock Luxembourg SA',                'LU', 'FUND', 'bond', '0P0001A3RC.F',  'LU0839970364.EUFUND', 'BGGGX2E',  NULL,      '0P0001A3RC', '287052', 'BOND_GLOBAL'),
('IE0005032192', 'iShares Euro Credit Bond Index Fund',                             'BlackRock Asset Management Ireland Ltd', 'IE', 'FUND', 'bond', '0P0000STQT.F',  'IE0005032192.EUFUND', 'BAREUBD',  NULL,      '0P0000STQT', '229055', 'BOND_EURO');

-- =============================================================================
-- Seed: portfolio instruments (TKF100)
-- =============================================================================

INSERT INTO instrument_reference (isin, display_name, fund_manager, country, instrument_type, asset_class, yahoo_ticker, eodhd_ticker, bloomberg_ticker, ric, benchmark_category) VALUES
('IE00BMDBMY19', 'Invesco MSCI Emerging Markets Universal Screened UCITS ETF Acc',  'Invesco Investment Management Limited',       'IE', 'ETF', 'equity', 'ESGM.DE',  'ESGM.XETRA',  'ESGM',  'ESGM.DE',  'EQUITY_EM'),
('IE00BJZ2DC62', 'Xtrackers MSCI USA Screened UCITS ETF',                           'DWS Investment S.A.',                         'IE', 'ETF', 'equity', 'XRSM.DE',  'XRSM.XETRA',  'XRSM',  'XRSM.DE',  'EQUITY_DM'),
('LU0476289540', 'Xtrackers MSCI Canada Screened UCITS ETF',                        'DWS Investment S.A.',                         'LU', 'ETF', 'equity', 'D5BH.DE',  'D5BH.XETRA',  'D5BH',  'D5BH.DE',  'EQUITY_DM'),
('IE000F60HVH9', 'ICAV Amundi MSCI USA Screened UCITS ETF',                         'Amundi Ireland Limited',                      'IE', 'ETF', 'equity', 'USAS.PA',  'USAS.PA.EODHD','USAS', 'USAS.PA',  'EQUITY_DM'),
('IE000O58J820', 'Vanguard ESG North America All Cap UCITS ETF',                    'Vanguard Group (Ireland) Limited',            'IE', 'ETF', 'equity', 'V3YA.DE',  'V3YA.XETRA',  'V3YA',  'V3YA.DE',  'EQUITY_DM'),
('LU1291099718', 'BNP Paribas Easy MSCI EUROPE MIN TE UCITS ETF',                  'BNP Paribas Asset Management Luxembourg',     'LU', 'ETF', 'equity', 'EEUX.DE',  'EEUX.XETRA',  'EEUX',  'EEUX.DE',  'EQUITY_DM'),
('LU1291106356', 'BNP Paribas Easy MSCI Pacific ex Japan Min TE UCITS ETF',        'BNP Paribas Asset Management Luxembourg',     'LU', 'ETF', 'equity', 'PAC.DE',   'PAC.XETRA',   'PAC',   'PAC.DE',   'EQUITY_DM'),
('LU1291102447', 'BNP Paribas Easy MSCI Japan Min TE UCITS ETF',                   'BNP Paribas Asset Management Luxembourg',     'LU', 'ETF', 'equity', 'EJAP.DE',  'EJAP.XETRA',  'EJAP',  'EJAP.DE',  'EQUITY_DM');

-- =============================================================================
-- Seed: new switch instruments (TUK75/TUV100 equity + TUK00 bonds)
-- =============================================================================

INSERT INTO instrument_reference (isin, display_name, fund_manager, country, instrument_type, asset_class, yahoo_ticker, eodhd_ticker, bloomberg_ticker, ric, benchmark_category) VALUES
('FR0013209921', 'Amundi MSCI World Ex USA Screened UCITS ETF',                     'Amundi Asset Management SAS',             'FR', 'ETF', 'equity', 'WLXU.DE',  'WLXU.XETRA',  'WLXU',  'WLXU.DE',  'EQUITY_DM'),
('IE00BFNM3P36', 'iShares MSCI EM IMI Screened UCITS ETF',                         'BlackRock Asset Management Ireland Ltd',  'IE', 'ETF', 'equity', 'AYEM.DE',  'AYEM.XETRA',  'AYEM',  'AYEM.DE',  'EQUITY_EM'),
('IE00BH04GL39', 'Vanguard EUR Eurozone Government Bond UCITS ETF',                'Vanguard Group (Ireland) Limited',        'IE', 'ETF', 'bond',   'VGEA.DE',  'VGEA.XETRA',  'VGEA',  'VGEA.DE',  'BOND_EURO'),
('LU0478205379', 'Xtrackers II EUR Corporate Bond UCITS ETF',                      'DWS Investment S.A.',                     'LU', 'ETF', 'bond',   'D5BG.DE',  'D5BG.XETRA',  'XBLC',  'D5BG.DE',  'BOND_EURO'),
('IE000AQ7A2X6', 'SPDR Bloomberg Global Aggregate Bond UCITS ETF EUR Hedged',      'State Street Global Advisors Europe Ltd', 'IE', 'ETF', 'bond',   'SPFF.DE',  'SPFF.XETRA',  'SPFF',  'SPFF.DE',  'BOND_GLOBAL');

-- =============================================================================
-- Seed: benchmark proxy ETFs (not held in portfolios)
-- benchmarkCategory is NULL: these ARE the benchmarks, not tracked instruments.
-- =============================================================================

INSERT INTO instrument_reference (isin, display_name, instrument_type, asset_class, yahoo_ticker, eodhd_ticker, bloomberg_ticker, ric, benchmark_category) VALUES
('IE00B4L5Y983', 'iShares Core MSCI World UCITS ETF',                              'ETF', 'equity', 'EUNL.DE',  'EUNL.XETRA',   'EUNL',  'EUNL.DE',  NULL),
('IE00B4L5YC18', 'iShares MSCI EM UCITS ETF',                                      'ETF', 'equity', 'EUNM.DE',  'EUNM.XETRA',   'EUNM',  'EUNM.DE',  NULL),
('IE00B3DKXQ41', 'iShares Euro Aggregate Bond UCITS ETF',                          'ETF', 'bond',   'EUN4.DE',  'EUN4.XETRA',   'EUN4',  'EUN4.DE',  NULL),
('IE00BDBRDM35', 'iShares Core Global Aggregate Bond UCITS ETF EUR Hedged',        'ETF', 'bond',   'EUNA.DE',  'EUNA.XETRA',   'EUNA',  'EUNA.DE',  NULL),
('LU1708330318', 'Amundi Core Global Aggregate Bond UCITS ETF EUR Hedged',         'ETF', 'bond',   'GAGH.PA',  'GAGH.PA.EODHD','GAGH',  'GAGH.PA',  NULL);

-- =============================================================================
-- benchmark_category_proxy: maps categories to proxy ETF storage keys
-- Replaces TrackingDifferenceService.resolveBenchmarkKey() switch statement.
-- =============================================================================

CREATE TABLE benchmark_category_proxy (
    id                    bigserial    NOT NULL,
    benchmark_category    varchar(20)  NOT NULL,
    etf_proxy_storage_key text         NOT NULL,
    index_proxy_key       text,

    CONSTRAINT benchmark_category_proxy_pkey PRIMARY KEY (id),
    CONSTRAINT benchmark_category_proxy_category_uq UNIQUE (benchmark_category)
);

INSERT INTO benchmark_category_proxy (benchmark_category, etf_proxy_storage_key, index_proxy_key) VALUES
('EQUITY_DM',   'IE00B4L5Y983.XETR', 'MSCI_WORLD'),
('EQUITY_EM',   'IE00B4L5YC18.XETR', 'MSCI_EM'),
('BOND_EURO',   'IE00B3DKXQ41.XETR', NULL),
('BOND_GLOBAL', 'LU1708330318.XPAR', NULL);
