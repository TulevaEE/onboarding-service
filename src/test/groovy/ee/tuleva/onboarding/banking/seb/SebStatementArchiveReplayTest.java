package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.CLOSE;
import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.OPEN;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.ProcessBankMessagesRequested;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.seb.reconciliation.ReconciliationCompletedEvent;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatementBalance;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@Slf4j
@SebIntegrationTest
@RecordApplicationEvents
@EnabledIfEnvironmentVariable(named = "SEB_STATEMENT_ARCHIVE_DIR", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SEB_GATEWAY_REGISTRAR_IBANS", matches = ".+")
class SebStatementArchiveReplayTest {

  private static final List<TulevaFund> ARCHIVE_FUNDS = List.of(TUK75, TUK00, TUV100);
  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final Pattern IBAN_PATTERN = Pattern.compile("<IBAN>(EE\\d{18})</IBAN>");

  @Autowired private BankingMessageRepository bankingMessageRepository;
  @Autowired private SavingFundPaymentRepository savingFundPaymentRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private ApplicationEvents applicationEvents;
  @Autowired private LedgerService ledgerService;
  @Autowired private FundBankLedger fundBankLedger;
  @Autowired private BankStatementExtractor extractor;
  @Autowired private JdbcClient jdbcClient;

  @DynamicPropertySource
  static void archiveFundAccounts(DynamicPropertyRegistry registry) {
    var archiveDir = System.getenv("SEB_STATEMENT_ARCHIVE_DIR");
    if (archiveDir == null) {
      return;
    }
    for (var fund : ARCHIVE_FUNDS) {
      var iban = ibanFromEarliestStatement(Path.of(archiveDir, fund.name()));
      registry.add("fund-accounts.funds." + fund.name() + ".cash-account", () -> iban);
    }
    registry.add("seb-gateway.registrar-ibans", () -> System.getenv("SEB_GATEWAY_REGISTRAR_IBANS"));
  }

  @Test
  void fullArchiveReplaysIntoAReconciledLedgerAndIsIdempotent() throws IOException {
    for (var fund : ARCHIVE_FUNDS) {
      replayFundAscending(fund);
    }

    assertAllMessagesProcessedCleanly();
    assertThat(unmatchedReconciliations()).isZero();
    assertThat(savingFundPaymentRepository.findAll()).isEmpty();
    var transactionCountAfterFirstPass = ledgerTransactionCount();
    logClassificationBreakdown();

    for (var fund : ARCHIVE_FUNDS) {
      for (var file : statementFiles(fund)) {
        persistMessage(Files.readString(file));
      }
    }
    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    assertAllMessagesProcessedCleanly();
    assertThat(unmatchedReconciliations()).isZero();
    assertThat(ledgerTransactionCount()).isEqualTo(transactionCountAfterFirstPass);
    for (var fund : ARCHIVE_FUNDS) {
      assertThat(cashBalance(fund))
          .isEqualByComparingTo(closingBalance(lastStatement(fund)).balance());
      assertThat(fundBankLedger.countUnresolvedUnclassifiedEntries(fund)).isZero();
    }
  }

  private void replayFundAscending(TulevaFund fund) throws IOException {
    BigDecimal previousClose = null;
    var matchedBefore = matchedReconciliations(fund);
    var files = statementFiles(fund);
    for (var file : files) {
      var rawXml = Files.readString(file);
      var statement = extractor.extractFromHistoricStatement(rawXml, ESTONIAN_ZONE);
      var opening = openingBalance(statement);
      var closing = closingBalance(statement);
      if (previousClose != null) {
        assertThat(opening.balance())
            .as("opening balance continuity: fund=%s, file=%s", fund, file.getFileName())
            .isEqualByComparingTo(previousClose);
      }
      previousClose = closing.balance();

      persistMessage(rawXml);
      eventPublisher.publishEvent(new ProcessBankMessagesRequested());

      assertThat(cashBalance(fund))
          .as("ledger matches closing balance: fund=%s, file=%s", fund, file.getFileName())
          .isEqualByComparingTo(closing.balance());
    }
    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntries(fund))
        .as("unresolved suspense entries: fund=%s", fund)
        .isZero();
    assertThat(matchedReconciliations(fund) - matchedBefore)
        .as("matched reconciliations: fund=%s", fund)
        .isEqualTo(files.size());
  }

  private List<Path> statementFiles(TulevaFund fund) throws IOException {
    var fundDir = Path.of(System.getenv("SEB_STATEMENT_ARCHIVE_DIR"), fund.name());
    try (Stream<Path> paths = Files.list(fundDir)) {
      return paths
          .filter(path -> path.getFileName().toString().matches("\\d{4}-.*\\.xml"))
          .sorted()
          .toList();
    }
  }

  private BankStatement lastStatement(TulevaFund fund) throws IOException {
    var files = statementFiles(fund);
    return extractor.extractFromHistoricStatement(Files.readString(files.getLast()), ESTONIAN_ZONE);
  }

  private static String ibanFromEarliestStatement(Path fundDir) {
    try (Stream<Path> paths = Files.list(fundDir)) {
      var earliest =
          paths
              .filter(path -> path.getFileName().toString().matches("\\d{4}-.*\\.xml"))
              .sorted()
              .findFirst()
              .orElseThrow();
      var matcher = IBAN_PATTERN.matcher(Files.readString(earliest));
      if (!matcher.find()) {
        throw new IllegalStateException("No IBAN found in statement: file=" + earliest);
      }
      return matcher.group(1);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read statement archive: dir=" + fundDir, e);
    }
  }

  private BankStatementBalance openingBalance(BankStatement statement) {
    return balanceOfType(statement, OPEN);
  }

  private BankStatementBalance closingBalance(BankStatement statement) {
    return balanceOfType(statement, CLOSE);
  }

  private BankStatementBalance balanceOfType(
      BankStatement statement, BankStatementBalance.StatementBalanceType type) {
    return statement.getBalances().stream()
        .filter(balance -> balance.type() == type)
        .findFirst()
        .orElseThrow();
  }

  private BigDecimal cashBalance(TulevaFund fund) {
    return ledgerService.getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, fund).getBalance();
  }

  private long matchedReconciliations(TulevaFund fund) {
    return applicationEvents.stream(ReconciliationCompletedEvent.class)
        .filter(event -> event.bankAccount().fund() == fund)
        .filter(ReconciliationCompletedEvent::matched)
        .count();
  }

  private long unmatchedReconciliations() {
    return applicationEvents.stream(ReconciliationCompletedEvent.class)
        .filter(event -> !event.matched())
        .count();
  }

  private void assertAllMessagesProcessedCleanly() {
    var messages = bankingMessageRepository.findAll();
    assertThat(messages).allSatisfy(message -> assertThat(message.getFailedAt()).isNull());
    assertThat(messages).allSatisfy(message -> assertThat(message.getProcessedAt()).isNotNull());
  }

  private long ledgerTransactionCount() {
    return jdbcClient.sql("select count(*) from ledger.transaction").query(Long.class).single();
  }

  private void logClassificationBreakdown() {
    jdbcClient
        .sql(
            """
            select t.transaction_type, count(*) as transactions
            from ledger.transaction t group by t.transaction_type order by count(*) desc
            """)
        .query((rs, i) -> rs.getString(1) + "=" + rs.getLong(2))
        .list()
        .forEach(row -> log.info("Archive classification: {}", row));
  }

  private void persistMessage(String xml) {
    var messageId = UUID.randomUUID().toString();
    bankingMessageRepository.save(
        BankingMessage.builder()
            .bankType(SEB)
            .requestId(messageId)
            .trackingId(messageId)
            .rawResponse(xml)
            .timezone("Europe/Tallinn")
            .build());
  }
}
