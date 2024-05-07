package ee.tuleva.onboarding.capital;

import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ApiCapitalEvent(
    LocalDate date, MemberCapitalEventType type, BigDecimal value, Currency currency) {}
