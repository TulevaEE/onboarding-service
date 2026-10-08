-- The currency a fund reports its assets in. EODHD's ETF_Data::TotalAssets comes in this
-- currency, not in General::CurrencyCode, which is the listing's: the XETRA listing of a USD
-- fund trades in EUR, but its total assets are in USD. The ownership limit check converts the
-- fund size from this currency.
ALTER TABLE instrument_reference ADD COLUMN fund_currency varchar(3);

-- TKF100's holdings, fund currencies as justETF gives them on 08.10.2026
UPDATE instrument_reference SET fund_currency = 'USD'
WHERE isin IN (
    'IE000F60HVH9', -- Amundi MSCI USA Screened
    'IE000O58J820', -- Vanguard ESG North America All Cap
    'IE00BJZ2DC62', -- Xtrackers MSCI USA Screened
    'LU0476289540', -- Xtrackers MSCI Canada Screened
    'IE00BMDBMY19', -- Invesco MSCI EM Universal Screened
    'IE00BFG1TM61'  -- iShares Developed World Screened Index Fund
);

UPDATE instrument_reference SET fund_currency = 'EUR'
WHERE isin IN (
    'LU1291099718', -- BNP Paribas Easy MSCI Europe Min TE
    'LU1291102447', -- BNP Paribas Easy MSCI Japan Min TE
    'LU1291106356'  -- BNP Paribas Easy MSCI Pacific ex Japan Min TE
);
