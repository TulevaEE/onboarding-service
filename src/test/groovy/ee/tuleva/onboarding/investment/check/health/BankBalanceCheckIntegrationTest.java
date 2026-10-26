package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.message.BankMessageType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.message.BankMessageType.INTRA_DAY_REPORT;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.BANK_BALANCE;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.message.BankMessageType;
import ee.tuleva.onboarding.banking.message.BookedBalanceReader;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import ee.tuleva.onboarding.banking.xml.Iso20022Marshaller;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  BankBalanceChecker.class,
  BookedBalanceReader.class,
  BankStatementExtractor.class,
  Iso20022Marshaller.class
})
class BankBalanceCheckIntegrationTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 10, 23);
  private static final String TUK75_INVESTMENT_IBAN = "EE111111111111111111";
  private static final String TKF100_INVESTMENT_IBAN = "EE222222222222222222";
  private static final String TKF100_DEPOSIT_IBAN = "EE333333333333333333";
  private static final Instant EARLY_MORNING = Instant.parse("2026-10-24T01:00:33Z");
  private static final Instant REFETCHED = Instant.parse("2026-10-24T03:30:12Z");

  @Autowired BankBalanceChecker checker;
  @Autowired JdbcClient jdbcClient;
  @MockitoBean BankAccounts bankAccounts;

  @BeforeEach
  void fundAccounts() {
    given(bankAccounts.findAll(TUK75))
        .willReturn(
            List.of(new BankAccount(TUK75_INVESTMENT_IBAN, FUND_INVESTMENT_EUR, TUK75, "client")));
    given(bankAccounts.findAll(TKF100))
        .willReturn(
            List.of(
                new BankAccount(TKF100_DEPOSIT_IBAN, DEPOSIT_EUR, TKF100, "client"),
                new BankAccount(TKF100_INVESTMENT_IBAN, FUND_INVESTMENT_EUR, TKF100, "client")));
  }

  @Test
  void warnsWhenTheReportsCashIsBelowTheInvestmentAccountsClosingBalance() {
    storeStatement(TUK75_INVESTMENT_IBAN, NAV_DATE, "1000000.00", "CRDT", EARLY_MORNING);

    assertThat(checker.check(TUK75, NAV_DATE, cash(TUK75, "999000.00")))
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                BANK_BALANCE,
                WARNING,
                "Report cash 999000.00, bank closing balance 1000000.00 on 2026-10-23,"
                    + " difference -1000.00, NAV too low. Have SEB correct the report's cash"
                    + " before the NAV"));
  }

  @Test
  void passesWhenTheReportsCashEqualsTheClosingBalanceOfTheDaysLatestStatement() {
    storeStatement(TUK75_INVESTMENT_IBAN, NAV_DATE, "999000.00", "CRDT", EARLY_MORNING);
    storeStatement(TUK75_INVESTMENT_IBAN, NAV_DATE, "1000000.00", "CRDT", REFETCHED);
    storeStatement(TUK75_INVESTMENT_IBAN, NAV_DATE.minusDays(1), "1000000.00", "CRDT", null);

    assertThat(checker.check(TUK75, NAV_DATE, cash(TUK75, "1000000.00"))).isEmpty();
  }

  @Test
  void readsADebitClosingBalanceAsNegativeCash() {
    storeStatement(TUK75_INVESTMENT_IBAN, NAV_DATE, "500.00", "DBIT", EARLY_MORNING);

    assertThat(checker.check(TUK75, NAV_DATE, cash(TUK75, "-500.00"))).isEmpty();
  }

  @Test
  void comparesTheSavingsFundsCashWithItsInvestmentAccountAlone() {
    storeStatement(TKF100_DEPOSIT_IBAN, NAV_DATE, "25000.00", "CRDT", EARLY_MORNING);
    storeStatement(TKF100_INVESTMENT_IBAN, NAV_DATE, "70000.00", "CRDT", EARLY_MORNING);

    assertThat(checker.check(TKF100, NAV_DATE, cash(TKF100, "70000.00"))).isEmpty();
  }

  @Test
  void cannotRunUntilTheDaysStatementArrivesAndPassesOnceItHas() {
    storeStatement(TUK75_INVESTMENT_IBAN, NAV_DATE.minusDays(1), "1000000.00", "CRDT", null);
    store(
        TUK75_INVESTMENT_IBAN,
        INTRA_DAY_REPORT,
        NAV_DATE,
        EARLY_MORNING,
        "<Document/>",
        EARLY_MORNING,
        null);
    storeFailedStatement(TUK75_INVESTMENT_IBAN, NAV_DATE);

    assertThat(checker.check(TUK75, NAV_DATE, cash(TUK75, "1000000.00")))
        .containsExactly(
            new HealthCheckFinding(
                TUK75, BANK_BALANCE, NOT_RUN, "No bank statement for 2026-10-23"));

    storeStatement(TUK75_INVESTMENT_IBAN, NAV_DATE, "1000000.00", "CRDT", EARLY_MORNING);

    assertThat(checker.check(TUK75, NAV_DATE, cash(TUK75, "1000000.00"))).isEmpty();
  }

  private static List<FundPosition> cash(TulevaFund fund, String marketValue) {
    return List.of(
        FundPosition.builder()
            .navDate(NAV_DATE)
            .fund(fund)
            .accountType(CASH)
            .accountName("Cash account in SEB Pank")
            .currency("EUR")
            .marketValue(new BigDecimal(marketValue))
            .build());
  }

  private void storeStatement(
      String iban,
      LocalDate statementDate,
      String closingBooked,
      String creditOrDebit,
      @Nullable Instant receivedAt) {
    var received = receivedAt == null ? EARLY_MORNING.minusSeconds(86_400) : receivedAt;
    store(
        iban,
        HISTORIC_STATEMENT,
        statementDate,
        received,
        statement(iban, statementDate, closingBooked, creditOrDebit),
        received,
        null);
  }

  private void storeFailedStatement(String iban, LocalDate statementDate) {
    store(
        iban,
        HISTORIC_STATEMENT,
        statementDate,
        REFETCHED,
        statement(iban, statementDate, "1.00", "CRDT"),
        null,
        REFETCHED);
  }

  private void store(
      String iban,
      BankMessageType type,
      LocalDate statementDate,
      Instant receivedAt,
      String rawResponse,
      @Nullable Instant processedAt,
      @Nullable Instant failedAt) {
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
        .param("iban", iban)
        .param("statementDate", statementDate)
        .param("processedAt", processedAt == null ? null : Timestamp.from(processedAt))
        .param("failedAt", failedAt == null ? null : Timestamp.from(failedAt))
        .param("receivedAt", Timestamp.from(receivedAt))
        .update();
  }

  private static String statement(
      String iban, LocalDate statementDate, String closingBooked, String creditOrDebit) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr><MsgId>statement</MsgId><CreDtTm>%1$sT04:00:33+03:00</CreDtTm></GrpHdr>
            <Stmt>
              <Id>statement</Id>
              <CreDtTm>%1$sT04:00:33+03:00</CreDtTm>
              <FrToDt><FrDtTm>%2$sT00:00:00+03:00</FrDtTm><ToDtTm>%2$sT23:59:59+03:00</ToDtTm></FrToDt>
              <Acct>
                <Id><IBAN>%3$s</IBAN></Id>
                <Ownr><Nm>BGW TESTCLIENT1</Nm><Id><OrgId><Othr><Id>22255887</Id></Othr></OrgId></Id></Ownr>
              </Acct>
              <Bal>
                <Tp><CdOrPrtry><Cd>OPBD</Cd></CdOrPrtry></Tp>
                <Amt Ccy="EUR">%4$s</Amt>
                <CdtDbtInd>%5$s</CdtDbtInd>
                <Dt><Dt>%2$s</Dt></Dt>
              </Bal>
              <Bal>
                <Tp><CdOrPrtry><Cd>CLBD</Cd></CdOrPrtry></Tp>
                <Amt Ccy="EUR">%4$s</Amt>
                <CdtDbtInd>%5$s</CdtDbtInd>
                <Dt><Dt>%2$s</Dt></Dt>
              </Bal>
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """
        .formatted(statementDate.plusDays(1), statementDate, iban, closingBooked, creditOrDebit);
  }
}
