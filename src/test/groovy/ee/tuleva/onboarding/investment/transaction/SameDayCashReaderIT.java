package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.message.BankMessageType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.message.BankMessageType.INTRA_DAY_REPORT;
import static ee.tuleva.onboarding.investment.transaction.SebStatementFixture.currentDayReport;
import static ee.tuleva.onboarding.investment.transaction.SebStatementFixture.endOfDayStatement;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.message.BankMessageType;
import ee.tuleva.onboarding.banking.message.BookedBalanceReader;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import ee.tuleva.onboarding.banking.xml.Iso20022Marshaller;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.CurrentDayBalance;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.NoCurrentDayReportToday;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.Unreadable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
  void readsTheLatestCurrentDayReportForToday() {
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

    assertThat(reader.read(TUK75, null))
        .isEqualTo(
            new CashSourceReport(
                CashSource.POSITION_REPORT,
                new CurrentDayBalance(new BigDecimal("1000.00"), LATER_THIS_MORNING)));
  }

  @Test
  void yesterdaysEndOfDayStatementReceivedThisMorningIsNoCurrentDayReport() {
    store(
        HISTORIC_STATEMENT,
        TODAY.minusDays(1),
        THIS_MORNING,
        endOfDayStatement(TUK75_INVESTMENT_IBAN, TODAY.minusDays(1), "900.00"));

    assertThat(reader.read(TUK75, null).gateway()).isEqualTo(new NoCurrentDayReportToday());
  }

  @Test
  void yesterdaysCurrentDayReportIsNoCurrentDayReportToday() {
    store(
        INTRA_DAY_REPORT,
        TODAY.minusDays(1),
        Instant.parse("2026-11-19T14:50:00Z"),
        currentDayReport(TUK75_INVESTMENT_IBAN, TODAY.minusDays(1), "900.00"));

    assertThat(reader.read(TUK75, null).gateway()).isEqualTo(new NoCurrentDayReportToday());
  }

  @Test
  void aCurrentDayReportThatCannotBeReadIsSaidToBeUnreadable() {
    store(INTRA_DAY_REPORT, TODAY, THIS_MORNING, "<not-a-report/>");

    assertThat(reader.read(TUK75, null).gateway()).isEqualTo(new Unreadable());
  }

  @Test
  void anOperatorAmountIsTheCashSourceAndTheGatewayBalanceIsStillRead() {
    store(
        INTRA_DAY_REPORT,
        TODAY,
        THIS_MORNING,
        currentDayReport(TUK75_INVESTMENT_IBAN, TODAY, "1000.00"));

    assertThat(reader.read(TUK75, new BigDecimal("999.00")))
        .isEqualTo(
            new CashSourceReport(
                CashSource.OPERATOR_AMOUNT,
                new CurrentDayBalance(new BigDecimal("1000.00"), THIS_MORNING)));
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
