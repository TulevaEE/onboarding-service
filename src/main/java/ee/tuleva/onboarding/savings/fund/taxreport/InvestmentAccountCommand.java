package ee.tuleva.onboarding.savings.fund.taxreport;

import jakarta.validation.constraints.NotBlank;

public record InvestmentAccountCommand(@NotBlank String iban) {}
