UPDATE instrument_reference
SET settlement_cutoff_time = '13:15:00',
    settlement_cutoff_zone = 'Europe/Tallinn',
    settlement_days_from_acceptance = 4
WHERE isin IN ('IE00BFG1TM61', 'IE00BKPTWY98');

UPDATE instrument_reference
SET settlement_cutoff_time = '11:15:00',
    settlement_cutoff_zone = 'Europe/Tallinn',
    settlement_days_from_acceptance = 3
WHERE isin IN ('LU0826455353', 'LU0839970364');

UPDATE instrument_reference
SET settlement_cutoff_time = '09:30:00',
    settlement_cutoff_zone = 'Europe/Tallinn',
    settlement_days_from_acceptance = 3
WHERE isin IN ('IE0005032192', 'IE0031080751');
