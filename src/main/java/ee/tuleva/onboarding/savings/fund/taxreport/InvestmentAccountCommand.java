package ee.tuleva.onboarding.savings.fund.taxreport;

import ee.tuleva.onboarding.capital.transfer.iban.ValidIban;

public record InvestmentAccountCommand(@ValidIban String iban) {}
