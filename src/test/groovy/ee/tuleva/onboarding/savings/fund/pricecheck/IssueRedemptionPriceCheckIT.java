package ee.tuleva.onboarding.savings.fund.pricecheck;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.ISSUE_REDEMPTION_PRICE_DEVIATION_THRESHOLD;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.NAV_DIFFERS_FROM_PUBLISHED;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.NAV_NOT_PUBLISHED;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.PRICED_AT_THE_NAV;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.PRICE_DEVIATES;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.JdbcFundValueRepository;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.ledger.SavingsFundLedgerStackConfiguration;
import ee.tuleva.onboarding.ledger.UnitDealings;
import ee.tuleva.onboarding.savings.IssueRedemptionPriceThreshold;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  SavingsFundLedgerStackConfiguration.class,
  UnitDealings.class,
  JdbcFundValueRepository.class,
  InvestmentParameterRepository.class,
  PublicHolidays.class,
  IssueRedemptionPriceCheck.class,
  CheckedBookingDays.class,
  IssueRedemptionPriceCheckIT.SeededThreshold.class
})
class IssueRedemptionPriceCheckIT {

  private static final LocalDate THURSDAY = LocalDate.parse("2026-11-26");
  private static final LocalDate TUESDAY_NAV = LocalDate.parse("2026-11-24");

  @Autowired SavingsFundLedger savingsFundLedger;
  @Autowired JdbcFundValueRepository fundValues;
  @Autowired IssueRedemptionPriceCheck check;

  PartyRef party = new PartyRef(PERSON, "38888888888");

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void aDealAtThePublishedNavPassesAndOneAtAnotherPriceIsReported() {
    publishNav(TUESDAY_NAV, "1.1000");
    holdUnitsFromAnEarlierDeal();
    bookAt("2026-11-25T14:05:00Z");
    UUID atTheNav = subscribe("1000.00", "909.09090", "1.1000", TUESDAY_NAV);
    UUID overpaid = redeem("100.00000", "120.00", "1.1000", TUESDAY_NAV);

    var run = check.run(THURSDAY).orElseThrow();

    assertThat(verdictsByTransaction(run))
        .containsOnly(entry(atTheNav, PRICED_AT_THE_NAV), entry(overpaid, PRICE_DEVIATES));
  }

  @Test
  void aDealBookedAtANavOtherThanThePublishedOneOfItsDateIsReported() {
    publishNav(TUESDAY_NAV, "1.1000");
    bookAt("2026-11-25T14:05:00Z");
    UUID atAnotherNav = subscribe("1000.00", "917.43119", "1.0900", TUESDAY_NAV);

    var run = check.run(THURSDAY).orElseThrow();

    assertThat(verdictsByTransaction(run))
        .containsOnly(entry(atAnotherNav, NAV_DIFFERS_FROM_PUBLISHED));
  }

  @Test
  void aDealWhoseNavDateWasNeverPublishedIsReported() {
    bookAt("2026-11-25T14:05:00Z");
    UUID unpublished = subscribe("1000.00", "909.09090", "1.1000", TUESDAY_NAV);

    var run = check.run(THURSDAY).orElseThrow();

    assertThat(verdictsByTransaction(run)).containsOnly(entry(unpublished, NAV_NOT_PUBLISHED));
  }

  @Test
  void theFirstRunChecksOnlyTheDealsOfThePreviousWorkingDay() {
    publishNav(TUESDAY_NAV, "1.1000");
    holdUnitsFromAnEarlierDeal();
    bookAt("2026-11-24T14:05:00Z");
    redeem("100.00000", "120.00", "1.1000", TUESDAY_NAV);

    assertThat(check.run(THURSDAY).orElseThrow().dealings()).isEmpty();
  }

  @Test
  void dealsOfADayAMissedRunLeftUncheckedAreCheckedByTheNextRun() {
    publishNav(TUESDAY_NAV, "1.1000");
    holdUnitsFromAnEarlierDeal();
    check.recordChecked(
        new PriceCheckRun(
            new BookingDays(LocalDate.parse("2026-11-23"), LocalDate.parse("2026-11-24")),
            new BigDecimal("0.001"),
            List.of()),
        Instant.parse("2026-11-24T06:00:00Z"));
    bookAt("2026-11-24T14:05:00Z");
    UUID missedOnWednesday = redeem("100.00000", "120.00", "1.1000", TUESDAY_NAV);

    var run = check.run(THURSDAY).orElseThrow();

    assertThat(run.days().describe()).isEqualTo("2026-11-24–2026-11-25");
    assertThat(verdictsByTransaction(run)).containsOnly(entry(missedOnWednesday, PRICE_DEVIATES));
  }

  @Test
  void daysAlreadyCheckedAreNotCheckedAgain() {
    publishNav(TUESDAY_NAV, "1.1000");
    bookAt("2026-11-25T14:05:00Z");
    subscribe("1000.00", "909.09090", "1.1000", TUESDAY_NAV);
    check.recordChecked(check.run(THURSDAY).orElseThrow(), Instant.parse("2026-11-26T06:00:00Z"));

    assertThat(check.run(THURSDAY)).isEmpty();
  }

  @Test
  void readsTheSeededThresholdForTheRunDay() {
    assertThat(check.run(THURSDAY).orElseThrow().deviationThreshold())
        .isEqualByComparingTo(new BigDecimal("0.001"));
  }

  private static Map<UUID, DealingPriceVerdict> verdictsByTransaction(PriceCheckRun run) {
    return run.dealings().stream()
        .collect(
            Collectors.toMap(
                price -> price.dealing().transactionId(),
                price -> price.verdict(run.deviationThreshold())));
  }

  private void publishNav(LocalDate date, String nav) {
    fundValues.save(
        new FundValue(
            TKF100.getIsin(),
            date,
            new BigDecimal(nav),
            "TULEVA",
            Instant.parse("2026-11-25T13:20:00Z")));
  }

  private void bookAt(String instant) {
    ClockHolder.setClock(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
  }

  private UUID subscribe(String cash, String units, String nav, LocalDate navDate) {
    UUID paymentId = randomUUID();
    savingsFundLedger.recordPaymentReceived(party, new BigDecimal(cash), paymentId);
    savingsFundLedger.reservePaymentForSubscription(party, new BigDecimal(cash), paymentId);
    return savingsFundLedger
        .issueFundUnitsFromReserved(
            party,
            new BigDecimal(cash),
            new BigDecimal(units),
            new BigDecimal(nav),
            navDate,
            paymentId)
        .getId();
  }

  private void holdUnitsFromAnEarlierDeal() {
    bookAt("2026-11-20T14:05:00Z");
    subscribe("1000.00", "909.09090", "1.1000", LocalDate.parse("2026-11-19"));
  }

  private UUID redeem(String units, String cash, String nav, LocalDate navDate) {
    UUID requestId = randomUUID();
    savingsFundLedger.reserveFundUnitsForRedemption(party, new BigDecimal(units), requestId);
    return savingsFundLedger
        .redeemFundUnitsFromReserved(
            party,
            new BigDecimal(units),
            new BigDecimal(cash),
            new BigDecimal(nav),
            navDate,
            requestId)
        .getId();
  }

  @TestConfiguration
  static class SeededThreshold {
    @Bean
    IssueRedemptionPriceThreshold issueRedemptionPriceThreshold(
        InvestmentParameterRepository repository) {
      return (fund, asOf) ->
          repository.findLatestValue(ISSUE_REDEMPTION_PRICE_DEVIATION_THRESHOLD, fund, asOf);
    }
  }
}
