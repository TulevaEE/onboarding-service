ALTER TABLE instrument_reference ADD COLUMN fund_currency varchar(3);

UPDATE instrument_reference SET fund_currency = 'USD'
WHERE isin IN (
    'IE000F60HVH9',
    'IE000O58J820',
    'IE00BJZ2DC62',
    'LU0476289540',
    'IE00BMDBMY19',
    'IE00BFG1TM61'
);

UPDATE instrument_reference SET fund_currency = 'EUR'
WHERE isin IN (
    'LU1291099718',
    'LU1291102447',
    'LU1291106356'
);
