package ee.tuleva.onboarding.banking.seb.fetcher;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.statement.StatementPeriod;

public record StatementGap(BankAccount account, StatementPeriod period) {}
