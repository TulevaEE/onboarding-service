package ee.tuleva.onboarding.investment.instrument;

import java.time.LocalTime;
import java.time.ZoneId;

public record SettlementTerms(LocalTime cutoffTime, ZoneId cutoffZone, int daysFromAcceptance) {}
