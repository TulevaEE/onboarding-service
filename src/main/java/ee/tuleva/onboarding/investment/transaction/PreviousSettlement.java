package ee.tuleva.onboarding.investment.transaction;

import java.time.LocalDate;

public record PreviousSettlement(LocalDate reportDate, SettlementSource source) {}
