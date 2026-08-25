package ee.tuleva.onboarding.banking.seb.fetcher;

import ee.tuleva.onboarding.banking.BankAccount;

public record SebEodFetchFailedEvent(BankAccount account, String errorMessage) {}
