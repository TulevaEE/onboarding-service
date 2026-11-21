package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.message.BankMessageType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.message.BankMessageType.INTRA_DAY_REPORT;
import static ee.tuleva.onboarding.investment.transaction.PevaRavaCycleState.ACTIVE;
import static ee.tuleva.onboarding.investment.transaction.PevaRavaCycleState.OUTSIDE_A_CYCLE;
import static ee.tuleva.onboarding.investment.transaction.SebStatementFixture.currentDayReport;
import static ee.tuleva.onboarding.investment.transaction.SebStatementFixture.currentDayReportWithoutBookedBalance;
import static ee.tuleva.onboarding.investment.transaction.SebStatementFixture.endOfDayStatement;
import static ee.tuleva.onboarding.investment.transaction.SebStatementFixture.entry;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.message.BankMessageType;
import ee.tuleva.onboarding.banking.message.BookedBalanceReader;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import ee.tuleva.onboarding.banking.xml.Iso20022Marshaller;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.CurrentDayBalance;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.NoBookedBalance;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.NoCurrentDayReportToday;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.NoInvestmentAccount;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.Unreadable;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  SameDayCashReader.class,
  BookedBalanceReader.class,
  BankStatementExtractor.class,
  Iso20022Marshaller.class,
  FundNavQueryService.class,
  InvestmentParameterRepository.class,
  SameDayCashReaderIT.FixedClock.class
})
class SameDayCashReaderIT {

  private static final String TUK75_INVESTMENT_IBAN = "EE111111111111111111";
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
  private static final Instant THIS_MORNING = Instant.parse("2026-11-20T07:40:00Z");
  private static final Instant LATER_THIS_MORNING = Instant.parse("2026-11-20T08:20:00Z");

  @TestConfiguration
  static class FixedClock {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-11-20T08:30:00Z"), ZoneId.of("Europe/Tallinn"));
    }
  }

  @Autowired SameDayCashReader reader;
  @Autowired JdbcClient jdbcClient;
  @MockitoBean BankAccounts bankAccounts;

  @BeforeEach
  void investmentAccount() {
    given(bankAccounts.findAll(TUK75))
        .willReturn(
            List.of(new BankAccount(TUK75_INVESTMENT_IBAN, FUND_INVESTMENT_EUR, TUK75, "client")));
  }

  @Test
  void aRunForAnotherDayDoesNotReadTheGatewayAndSaysWhy() {
    store(
        INTRA_DAY_REPORT,
        TODAY,
        THIS_MORNING,
        currentDayReport(TUK75_INVESTMENT_IBAN, TODAY, "900.00"));

    assertThat(
            reader
                .read(TUK75, TODAY.minusDays(1), null, new BigDecimal("900.00"), OUTSIDE_A_CYCLE)
                .gateway()
                .payload())
        .isEqualTo(java.util.Map.of("status", "NOT_TODAYS_RUN"));
  }

  @Test
  void theLatestCurrentDayReportForTodayIsTheCashSource() {
    store(
        INTRA_DAY_REPORT,
        TODAY,
        THIS_MORNING,
        currentDayReport(TUK75_INVESTMENT_IBAN, TODAY, "900.00"));
    store(
        INTRA_DAY_REPORT,
        TODAY,
        LATER_THIS_MORNING,
        currentDayReport(TUK75_INVESTMENT_IBAN, TODAY, "1000.00"));

    assertThat(reader.read(TUK75, TODAY, null, null, OUTSIDE_A_CYCLE))
        .isEqualTo(
            new CashSourceReport(
                CashSource.GATEWAY_BALANCE,
                new CurrentDayBalance(
                    new BigDecimal("1000.00"), LATER_THIS_MORNING, Set.of(), Set.of()),
                null,
                List.of(),
                GatewayHold.NONE));
  }

  @Test
  void theBalanceNamesTheEntriesSebStillShowsAsPendingSoNothingIsNettedAgainstThem() {
    store(
        INTRA_DAY_REPORT,
        TODAY,
        THIS_MORNING,
        currentDayReport(
            TUK75_INVESTMENT_IBAN,
            TODAY,
            "1000.00",
            entry("BOOKED-1", "200.00", "DBIT", "BOOK"),
            entry("PENDING-1", "300.00", "DBIT", "PDNG")));

    assertThat(reader.read(TUK75, TODAY, null, null, OUTSIDE_A_CYCLE).gateway())
        .isEqualTo(
            new CurrentDayBalance(
                new BigDecimal("1000.00"),
                THIS_MORNING,
                Set.of(reference("PENDING-1")),
                Set.of(reference("BOOKED-1"))));
  }

  @Test
  void
      theBalanceHoldsTodaysBookedEntriesOfEveryProcessedReportUpToItsOwnButNotOfOnesNotProcessed() {
    store(
        INTRA_DAY_REPORT,
        TODAY,
        THIS_MORNING,
        currentDayReport(
            TUK75_INVESTMENT_IBAN, TODAY, "900.00", entry("EARLY-1", "100.00", "DBIT", "BOOK")));
    store(
        INTRA_DAY_REPORT,
        TODAY,
        LATER_THIS_MORNING,
        currentDayReport(
            TUK75_INVESTMENT_IBAN, TODAY, "800.00", entry("LATE-1", "100.00", "DBIT", "BOOK")));
    storeUnprocessed(
        INTRA_DAY_REPORT,
        TODAY,
        LATER_THIS_MORNING.plusSeconds(600),
        currentDayReport(
            TUK75_INVESTMENT_IBAN, TODAY, "700.00", entry("NOT-YET-1", "100.00", "DBIT", "BOOK")));

    assertThat(reader.read(TUK75, TODAY, null, null, OUTSIDE_A_CYCLE).gateway())
        .isEqualTo(
            new CurrentDayBalance(
                new BigDecimal("800.00"),
                LATER_THIS_MORNING,
                Set.of(),
                Set.of(reference("EARLY-1"), reference("LATE-1"))));
  }

  private static UUID reference(String externalId) {
    return UUID.nameUUIDFromBytes((TUK75_INVESTMENT_IBAN + ":" + externalId).getBytes(UTF_8));
  }

  @Test
  void yesterdaysEndOfDayStatementReceivedThisMorningIsNoCurrentDayReport() {
    store(
        HISTORIC_STATEMENT,
        TODAY.minusDays(1),
        THIS_MORNING,
        endOfDayStatement(TUK75_INVESTMENT_IBAN, TODAY.minusDays(1), "900.00"));

    assertThat(reader.read(TUK75, TODAY, null, null, OUTSIDE_A_CYCLE).gateway())
        .isEqualTo(new NoCurrentDayReportToday());
  }

  @Test
  void yesterdaysCurrentDayReportIsNoCurrentDayReportToday() {
    store(
        INTRA_DAY_REPORT,
        TODAY.minusDays(1),
        Instant.parse("2026-11-19T14:50:00Z"),
        currentDayReport(TUK75_INVESTMENT_IBAN, TODAY.minusDays(1), "900.00"));

    assertThat(reader.read(TUK75, TODAY, null, null, OUTSIDE_A_CYCLE).gateway())
        .isEqualTo(new NoCurrentDayReportToday());
  }

  @Test
  void aCurrentDayReportThatCannotBeReadIsSaidToBeUnreadable() {
    store(INTRA_DAY_REPORT, TODAY, THIS_MORNING, "<not-a-report/>");

    assertThat(reader.read(TUK75, TODAY, null, null, OUTSIDE_A_CYCLE).gateway())
        .isEqualTo(new Unreadable());
  }

  @Test
  void anEarlierReportOfTheDayThatCannotBeReadLeavesTheRunOnReportCashAsUnreadable() {
    store(INTRA_DAY_REPORT, TODAY, THIS_MORNING, "<not-a-report/>");
    store(
        INTRA_DAY_REPORT,
        TODAY,
        LATER_THIS_MORNING,
        currentDayReport(TUK75_INVESTMENT_IBAN, TODAY, "1000.00"));

    var report = reader.read(TUK75, TODAY, null, new BigDecimal("900.00"), OUTSIDE_A_CYCLE);

    assertThat(report.source()).isEqualTo(CashSource.POSITION_REPORT);
    assertThat(report.gateway()).isEqualTo(new Unreadable());
  }

  @Test
  void aCurrentDayReportThatReadsButCarriesNoBookedBalanceIsSaidSoNotUnreadable() {
    store(
        INTRA_DAY_REPORT,
        TODAY,
        THIS_MORNING,
        currentDayReportWithoutBookedBalance(TUK75_INVESTMENT_IBAN, TODAY));

    assertThat(reader.read(TUK75, TODAY, null, null, OUTSIDE_A_CYCLE).gateway())
        .isEqualTo(new NoBookedBalance());
  }

  @Test
  void aFundWithoutAnInvestmentAccountIsSaidToHaveNoneRatherThanNoReportToday() {
    given(bankAccounts.findAll(TUK75))
        .willReturn(List.of(new BankAccount(TUK75_INVESTMENT_IBAN, DEPOSIT_EUR, TUK75, "client")));
    store(
        INTRA_DAY_REPORT,
        TODAY,
        THIS_MORNING,
        currentDayReport(TUK75_INVESTMENT_IBAN, TODAY, "1000.00"));

    assertThat(reader.read(TUK75, TODAY, null, null, OUTSIDE_A_CYCLE).gateway())
        .isEqualTo(new NoInvestmentAccount());
  }

  @Test
  void whileAPevaRavaCycleIsActiveTheRunStaysOnReportCashAndSaysWhy() {
    store(
        INTRA_DAY_REPORT,
        TODAY,
        THIS_MORNING,
        currentDayReport(TUK75_INVESTMENT_IBAN, TODAY, "1050.00"));

    var report = reader.read(TUK75, TODAY, null, new BigDecimal("1000.00"), ACTIVE);

    assertThat(report.source()).isEqualTo(CashSource.POSITION_REPORT);
    assertThat(report.payload())
        .isEqualTo(
            java.util.Map.of(
                "source",
                "POSITION_REPORT",
                "gatewayNotUsed",
                "PEVA_RAVA_CYCLE_ACTIVE",
                "reportCash",
                "1000.00",
                "gateway",
                java.util.Map.of(
                    "status",
                    "CURRENT_DAY_BALANCE",
                    "balance",
                    "1050.00",
                    "fetchedAt",
                    THIS_MORNING.toString()),
                "gatewayMinusReportCash",
                "50.00"));
  }

  @Test
  void anOperatorAmountIsTheCashSourceAndTheGatewayBalanceIsStillRead() {
    store(
        INTRA_DAY_REPORT,
        TODAY,
        THIS_MORNING,
        currentDayReport(TUK75_INVESTMENT_IBAN, TODAY, "1000.00"));

    assertThat(reader.read(TUK75, TODAY, new BigDecimal("999.00"), null, OUTSIDE_A_CYCLE))
        .isEqualTo(
            new CashSourceReport(
                CashSource.OPERATOR_AMOUNT,
                new CurrentDayBalance(new BigDecimal("1000.00"), THIS_MORNING, Set.of(), Set.of()),
                null,
                List.of(),
                GatewayHold.NONE));
  }

  @Test
  void aDifferenceWithinOnePercentOfNavIsShownWithoutAWarning() {
    store(
        INTRA_DAY_REPORT,
        TODAY,
        THIS_MORNING,
        currentDayReport(TUK75_INVESTMENT_IBAN, TODAY, "1050.00"));
    publishedNav("100000.00");

    var report = reader.read(TUK75, TODAY, null, new BigDecimal("1000.00"), OUTSIDE_A_CYCLE);

    assertThat(report.gatewayMinusReportCash()).isEqualByComparingTo("50.00");
    assertThat(report.warnings()).isEmpty();
  }

  @Test
  void aDifferenceBeyondOnePercentOfNavComesWithAWarning() {
    store(
        INTRA_DAY_REPORT,
        TODAY,
        THIS_MORNING,
        currentDayReport(TUK75_INVESTMENT_IBAN, TODAY, "2500.00"));
    publishedNav("100000.00");

    var report = reader.read(TUK75, TODAY, null, new BigDecimal("1000.00"), OUTSIDE_A_CYCLE);

    assertThat(report.warnings())
        .extracting(CalculationWarning::type)
        .containsExactly(CalculationWarningType.CASH_SOURCES_DIFFER);
  }

  @Test
  void withoutAPublishedNavTheDifferenceIsShownWithoutAWarning() {
    store(
        INTRA_DAY_REPORT,
        TODAY,
        THIS_MORNING,
        currentDayReport(TUK75_INVESTMENT_IBAN, TODAY, "2500.00"));

    var report = reader.read(TUK75, TODAY, null, new BigDecimal("1000.00"), OUTSIDE_A_CYCLE);

    assertThat(report.gatewayMinusReportCash()).isEqualByComparingTo("1500.00");
    assertThat(report.warnings()).isEmpty();
  }

  private void publishedNav(String nav) {
    jdbcClient
        .sql(
            """
            INSERT INTO nav_report
              (nav_date, fund_code, account_type, account_name, account_id, quantity,
               market_value, currency, calculation_id, published_at)
            VALUES (:navDate, 'TUK75', 'UNITS', 'Fund units', 'TUK75', 1, :nav, 'EUR',
               :calculationId, CURRENT_TIMESTAMP)
            """)
        .param("navDate", TODAY.minusDays(1))
        .param("nav", new BigDecimal(nav))
        .param("calculationId", UUID.randomUUID())
        .update();
  }

  private void storeUnprocessed(
      BankMessageType type, LocalDate statementDate, Instant receivedAt, String rawResponse) {
    store(type, statementDate, receivedAt, rawResponse);
    jdbcClient
        .sql("update banking_message set processed_at = null where received_at = :receivedAt")
        .param("receivedAt", Timestamp.from(receivedAt))
        .update();
  }

  private void store(
      BankMessageType type, LocalDate statementDate, Instant receivedAt, String rawResponse) {
    jdbcClient
        .sql(
            """
            insert into banking_message (id, bank_type, request_id, tracking_id, raw_response,
              timezone, message_type, account_iban, statement_from, statement_to, processed_at,
              received_at)
            values (:id, 'SEB', 'request', 'tracking', :rawResponse, 'Europe/Tallinn',
              :messageType, :iban, :day, :day, :receivedAt, :receivedAt)
            """)
        .param("id", UUID.randomUUID())
        .param("rawResponse", rawResponse)
        .param("messageType", type.name())
        .param("iban", TUK75_INVESTMENT_IBAN)
        .param("day", statementDate)
        .param("receivedAt", Timestamp.from(receivedAt))
        .update();
  }
}
