package ee.tuleva.onboarding.banking.seb.fetcher;

import ee.tuleva.onboarding.banking.BankAccountType;

public record SebEodFetchFailedEvent(BankAccountType account, String errorMessage) {}
