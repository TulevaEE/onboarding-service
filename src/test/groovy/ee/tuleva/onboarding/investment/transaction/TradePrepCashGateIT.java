package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.message.BankMessageType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.message.BankMessageType.INTRA_DAY_REPORT;
import static ee.tuleva.onboarding.investment.transaction.TradePrepOverridesFixture.noOverrides;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.message.BankMessageType;
import ee.tuleva.onboarding.banking.message.BookedBalanceReader;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import ee.tuleva.onboarding.banking.xml.Iso20022Marshaller;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoSameDayCash;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.UnreadableSameDayCash;
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
  TradePrepCashGate.class,
  BookedBalanceReader.class,
  BankStatementExtractor.class,
  Iso20022Marshaller.class,
  TradePrepCashGateIT.FixedClock.class
})
class TradePrepCashGateIT {

  private static final String TUK75_INVESTMENT_IBAN = "EE111111111111111111";
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
  private static final Instant THIS_MORNING = Instant.parse("2026-11-20T07:40:00Z");
  private static final Instant YESTERDAY_AFTERNOON = Instant.parse("2026-11-19T14:50:00Z");

  @TestConfiguration
  static class FixedClock {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-11-20T08:30:00Z"), ZoneId.of("Europe/Tallinn"));
    }
  }

  @Autowired TradePrepCashGate gate;
  @Autowired JdbcClient jdbcClient;
  @MockitoBean BankAccounts bankAccounts;

  @BeforeEach
  void investmentAccount() {
    given(bankAccounts.findAll(TUK75))
        .willReturn(
            List.of(new BankAccount(TUK75_INVESTMENT_IBAN, FUND_INVESTMENT_EUR, TUK75, "client")));
  }

  @Test
  void aBalanceFetchedTodayLetsTheFundThrough() {
    store(HISTORIC_STATEMENT, TODAY.minusDays(1), YESTERDAY_AFTERNOON, statement("900.00"));
    store(HISTORIC_STATEMENT, TODAY, THIS_MORNING, statement("1000.00"));

    assertThat(gate.missingSameDayCash(TUK75, noOverrides())).isEmpty();
  }

  @Test
  void aBalanceLastFetchedYesterdayStopsTheFundAndSaysWhenItWasFetched() {
    store(HISTORIC_STATEMENT, TODAY.minusDays(1), YESTERDAY_AFTERNOON, statement("900.00"));

    assertThat(gate.missingSameDayCash(TUK75, noOverrides()))
        .contains(new NoSameDayCash(YESTERDAY_AFTERNOON));
  }

  @Test
  void aFetchThatFailedTodayLeavesYesterdaysBalanceAndStopsTheFund() {
    store(HISTORIC_STATEMENT, TODAY.minusDays(1), YESTERDAY_AFTERNOON, statement("900.00"));
    storeFailed(TODAY, THIS_MORNING);

    assertThat(gate.missingSameDayCash(TUK75, noOverrides()))
        .contains(new NoSameDayCash(YESTERDAY_AFTERNOON));
  }

  @Test
  void anAccountWithNoBalanceAtAllStopsTheFund() {
    assertThat(gate.missingSameDayCash(TUK75, noOverrides())).contains(new NoSameDayCash(null));
  }

  @Test
  void aBalanceThatCannotBeReadStopsTheFundAndSaysSo() {
    store(INTRA_DAY_REPORT, TODAY, THIS_MORNING, "<not-a-statement/>");

    assertThat(gate.missingSameDayCash(TUK75, noOverrides())).contains(new UnreadableSameDayCash());
  }

  @Test
  void aCashAmountWithItsCommentClearsTheGateWithoutABalance() {
    var withCash =
        new TradePrepOverrides(
            Set.of(),
            List.of(),
            null,
            PriceDateRule.FRESHEST_PER_INSTRUMENT,
            new BigDecimal("1000"),
            Set.of());

    assertThat(gate.missingSameDayCash(TUK75, withCash)).isEmpty();
  }

  private void storeFailed(LocalDate statementDate, Instant receivedAt) {
    insert(INTRA_DAY_REPORT, statementDate, receivedAt, statement("1.00"), null, receivedAt);
  }

  private void store(
      BankMessageType type, LocalDate statementDate, Instant receivedAt, String rawResponse) {
    insert(type, statementDate, receivedAt, rawResponse, receivedAt, null);
  }

  private void insert(
      BankMessageType type,
      LocalDate statementDate,
      Instant receivedAt,
      String rawResponse,
      Instant processedAt,
      Instant failedAt) {
    jdbcClient
        .sql(
            """
            insert into banking_message (id, bank_type, request_id, tracking_id, raw_response,
              timezone, message_type, account_iban, statement_from, statement_to, processed_at,
              failed_at, received_at)
            values (:id, 'SEB', 'request', 'tracking', :rawResponse, 'Europe/Tallinn',
              :messageType, :iban, :statementDate, :statementDate, :processedAt, :failedAt,
              :receivedAt)
            """)
        .param("id", UUID.randomUUID())
        .param("rawResponse", rawResponse)
        .param("messageType", type.name())
        .param("iban", TUK75_INVESTMENT_IBAN)
        .param("statementDate", statementDate)
        .param("processedAt", processedAt == null ? null : Timestamp.from(processedAt))
        .param("failedAt", failedAt == null ? null : Timestamp.from(failedAt))
        .param("receivedAt", Timestamp.from(receivedAt))
        .update();
  }

  private static String statement(String booked) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr><MsgId>statement</MsgId><CreDtTm>2026-11-20T09:00:00+02:00</CreDtTm></GrpHdr>
            <Stmt>
              <Id>statement</Id>
              <CreDtTm>2026-11-20T09:00:00+02:00</CreDtTm>
              <FrToDt><FrDtTm>2026-11-19T00:00:00+02:00</FrDtTm><ToDtTm>2026-11-19T23:59:59+02:00</ToDtTm></FrToDt>
              <Acct>
                <Id><IBAN>%1$s</IBAN></Id>
                <Ownr><Nm>BGW TESTCLIENT1</Nm><Id><OrgId><Othr><Id>22255887</Id></Othr></OrgId></Id></Ownr>
              </Acct>
              <Bal>
                <Tp><CdOrPrtry><Cd>OPBD</Cd></CdOrPrtry></Tp>
                <Amt Ccy="EUR">%2$s</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt><Dt>2026-11-19</Dt></Dt>
              </Bal>
              <Bal>
                <Tp><CdOrPrtry><Cd>CLBD</Cd></CdOrPrtry></Tp>
                <Amt Ccy="EUR">%2$s</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt><Dt>2026-11-19</Dt></Dt>
              </Bal>
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """
        .formatted(TUK75_INVESTMENT_IBAN, booked);
  }
}
