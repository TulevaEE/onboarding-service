package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.banking.seb.Seb.SEB_GATEWAY_TIME_ZONE;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.BankType;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.ProcessBankMessagesRequested;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.savings.fund.PaymentReturningJob;
import ee.tuleva.onboarding.savings.fund.PaymentVerificationJob;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Manual integration test for processing real SEB bank XML messages.
 *
 * <p>Supported input formats:
 *
 * <ul>
 *   <li>CSV database dump with raw_response column (banking_message_dump.csv)
 * </ul>
 *
 * <p>To export from database: {@code COPY banking_message TO '/tmp/dump.csv' CSV HEADER}
 *
 * <p>The statement date is automatically extracted from the XML's closing balance (CLBD) date.
 */
@SebIntegrationTest
@TestPropertySource(
    properties = {
      "seb-gateway.accounts.DEPOSIT_EUR=EE711010220306707220",
      "seb-gateway.accounts.WITHDRAWAL_EUR=EE801010220306711229",
      "seb-gateway.accounts.FUND_INVESTMENT_EUR=EE861010220306591229"
    })
@Disabled("Manual test - requires real bank XML files in src/test/resources/banking/seb/real-data/")
class SebEndToEndPaymentProcessingManualIntegrationTest {

  @Autowired private SavingFundPaymentRepository paymentRepository;
  @Autowired private BankingMessageRepository bankingMessageRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private UserRepository userRepository;
  @Autowired private LedgerService ledgerService;
  @Autowired private SavingsFundLedger savingsFundLedger;
  @Autowired private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Autowired private PaymentVerificationJob paymentVerificationJob;
  @Autowired private SebAccountConfiguration sebAccountConfiguration;
  @Autowired private PaymentReturningJob paymentReturningJob;
  @MockitoBean private SebGatewayClient sebGatewayClient;

  // Path to real XML files - update filename as needed
  private static final String REAL_DATA_PATH = "banking/seb/real-data/";

  private LocalDate statementDate;
  private Instant now;

  @BeforeEach
  void setUp() {
    // Clock will be set after loading XML and extracting the statement date
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void processStatementsFromCsvDump_shouldReconcileSuccessfully() throws IOException {
    // Load XMLs from CSV database dump (e.g., exported via: COPY banking_message TO 'dump.csv' CSV
    // HEADER)
    List<String> xmlStatements = loadXmlsFromCsv("banking_message5.prod.export.csv");

    if (xmlStatements.isEmpty()) {
      System.out.println("No XML statements found in CSV");
      return;
    }

    System.out.println(
        "=== Processing " + xmlStatements.size() + " statements from CSV (in original order) ===");

    // Track which accounts we've set up initial balances for
    // Track closing balances per account
    var closingBalances = new java.util.HashMap<String, BigDecimal>();

    int statementIndex = 0;
    for (String xml : xmlStatements) {
      statementIndex++;

      String accountIban = extractAccountIban(xml);
      var accountType = sebAccountConfiguration.getAccountType(accountIban);

      // Extract statement date and set clock so ledger entries match the statement period
      statementDate = extractStatementDate(xml);
      now = statementDate.atTime(LocalTime.NOON).atZone(SEB_GATEWAY_TIME_ZONE).toInstant();
      ClockHolder.setClock(Clock.fixed(now, ZoneId.of("UTC")));

      createTestUsersFromXml(xml);
      persistMessage(xml);

      eventPublisher.publishEvent(new ProcessBankMessagesRequested());

      // Run payment processing jobs to create ledger entries
      paymentVerificationJob.runJob();
      paymentReturningJob.runJob();

      extractClosingBalance(xml).ifPresent(balance -> closingBalances.put(accountIban, balance));

      String accountName = accountType != null ? accountType.name() : "UNKNOWN";
      String messageType = xml.contains("camt.053") ? "camt.053" : "camt.052";
      String closingInfo =
          extractClosingBalance(xml).map(b -> ", closing: " + b).orElse(" (no closing balance)");
      System.out.println(
          "["
              + statementIndex
              + "/"
              + xmlStatements.size()
              + "] "
              + messageType
              + " "
              + accountName
              + " ("
              + accountIban
              + ") date: "
              + statementDate
              + closingInfo);
    }

    // Final verification - check each account
    System.out.println("\n=== Final Reconciliation ===");
    var payments = paymentRepository.findAll();
    System.out.println("Total payments: " + payments.size());

    // Payment status summary table
    System.out.println("\n=== Payment Status Summary ===");
    System.out.printf("%-15s %10s %15s%n", "Status", "Count", "Amount");
    System.out.println("-".repeat(42));
    payments.stream()
        .collect(
            java.util.stream.Collectors.groupingBy(
                SavingFundPayment::getStatus, java.util.stream.Collectors.toList()))
        .entrySet()
        .stream()
        .sorted(java.util.Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              var status = entry.getKey();
              var paymentList = entry.getValue();
              var count = paymentList.size();
              var totalAmount =
                  paymentList.stream()
                      .map(SavingFundPayment::getAmount)
                      .reduce(BigDecimal.ZERO, BigDecimal::add);
              System.out.printf("%-15s %10d %15.2f%n", status, count, totalAmount);
            });
    System.out.println("-".repeat(42));

    // Print full ledger state
    System.out.println("\n=== Ledger State ===");
    for (var systemAccount : SystemAccount.values()) {
      LedgerAccount account = ledgerService.getSystemAccount(systemAccount);
      System.out.println(
          systemAccount
              + ": balance="
              + account.getBalance()
              + ", entries="
              + account.getEntries().size());
    }

    Instant endOfLastStatement =
        statementDate.atTime(LocalTime.MAX).atZone(SEB_GATEWAY_TIME_ZONE).toInstant();

    boolean allReconciled = true;
    for (var entry : closingBalances.entrySet()) {
      String iban = entry.getKey();
      BigDecimal expectedBalance = entry.getValue();
      var accountType = sebAccountConfiguration.getAccountType(iban);

      if (accountType == null) {
        System.out.println("SKIP: Unknown account " + iban);
        continue;
      }

      LedgerAccount ledgerAccount = ledgerService.getSystemAccount(accountType.getLedgerAccount());
      BigDecimal ledgerBalance = ledgerAccount.getBalanceAt(endOfLastStatement);

      boolean matches = ledgerBalance.compareTo(expectedBalance) == 0;
      String status = matches ? "OK" : "MISMATCH";

      System.out.println(
          accountType
              + ": expected="
              + expectedBalance
              + ", ledger="
              + ledgerBalance
              + " ["
              + status
              + "]");

      if (!matches) {
        allReconciled = false;
      }
    }

    assertThat(allReconciled).as("All accounts should reconcile").isTrue();
    System.out.println("\nReconciliation: SUCCESS");
  }

  private List<String> loadXmlsFromCsv(String csvFilename) throws IOException {
    Path csvPath = new ClassPathResource(REAL_DATA_PATH + csvFilename).getFile().toPath();
    List<String> xmlStatements = new ArrayList<>();

    CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();

    try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
        CSVParser parser = new CSVParser(reader, format)) {

      for (var record : parser) {
        String rawResponse = record.get("raw_response");
        if (rawResponse != null && rawResponse.contains("<Document")) {
          xmlStatements.add(rawResponse);
        }
      }
    }

    System.out.println("Loaded " + xmlStatements.size() + " XML statements from " + csvFilename);
    return xmlStatements;
  }

  private LocalDate extractStatementDate(String xml) {
    // Try camt.053 format first: extract date from closing balance (CLBD) element
    Pattern clbdPattern =
        Pattern.compile("<Cd>CLBD</Cd>.*?<Dt>\\s*<Dt>([\\d-]+)</Dt>", Pattern.DOTALL);
    Matcher clbdMatcher = clbdPattern.matcher(xml);
    if (clbdMatcher.find()) {
      return LocalDate.parse(clbdMatcher.group(1));
    }

    // Fall back to camt.052 format: extract from ToDtTm (end of report period)
    Pattern toDtPattern = Pattern.compile("<ToDtTm>([\\d-]+)T", Pattern.DOTALL);
    Matcher toDtMatcher = toDtPattern.matcher(xml);
    if (toDtMatcher.find()) {
      return LocalDate.parse(toDtMatcher.group(1));
    }

    throw new IllegalStateException("Could not extract statement date from XML");
  }

  private String extractAccountIban(String xml) {
    // Extract IBAN from <Acct><Id><IBAN> element
    Pattern pattern = Pattern.compile("<Acct>\\s*<Id>\\s*<IBAN>(EE\\d+)</IBAN>", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(xml);

    if (matcher.find()) {
      return matcher.group(1);
    }

    throw new IllegalStateException("Could not extract account IBAN from XML");
  }

  private Optional<BigDecimal> extractClosingBalance(String xml) {
    // Try CLBD first (camt.053 end-of-day)
    var clbd = extractBalance(xml, "CLBD");
    if (clbd.isPresent()) {
      return clbd;
    }
    // Fall back to ITBD (camt.052 intraday)
    return extractBalance(xml, "ITBD");
  }

  private Optional<BigDecimal> extractBalance(String xml, String balanceType) {
    // Pattern to find balance by type (OPBD or CLBD)
    // camt.052 (intraday) doesn't have these, only camt.053 (end-of-day)
    String regex =
        "<Cd>"
            + balanceType
            + "</Cd>.*?<Amt[^>]*>([\\d.]+)</Amt>\\s*<CdtDbtInd>(CRDT|DBIT)</CdtDbtInd>";
    Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
    Matcher matcher = pattern.matcher(xml);

    if (matcher.find()) {
      BigDecimal amount = new BigDecimal(matcher.group(1));
      String indicator = matcher.group(2);
      // DBIT means negative balance
      return Optional.of("DBIT".equals(indicator) ? amount.negate() : amount);
    }

    return Optional.empty();
  }

  private void createTestUsersFromXml(String xml) {
    // Extract personal codes from XML (pattern: <Id>XXXXXXXXXXX</Id> inside <PrvtId>)
    Pattern pattern =
        Pattern.compile(
            "<PrvtId>\\s*<Othr>\\s*<Id>(\\d{11})</Id>", Pattern.DOTALL | Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(xml);

    while (matcher.find()) {
      String personalCode = matcher.group(1);
      createTestUserIfNotExists(personalCode);
    }
  }

  private void createTestUserIfNotExists(String personalCode) {
    if (userRepository.findByPersonalCode(personalCode).isPresent()) {
      return;
    }

    User user =
        userRepository.save(
            User.builder()
                .firstName("Test")
                .lastName("User " + personalCode.substring(0, 4))
                .personalCode(personalCode)
                .email("test-" + personalCode + "@example.com")
                .phoneNumber("+372 5555 0000")
                .build());

    savingsFundOnboardingRepository.saveOnboardingStatus(user.getPersonalCode(), COMPLETED);
    System.out.println("Created test user: personalCode=" + personalCode);
  }

  private void persistMessage(String xml) {
    BankingMessage message =
        BankingMessage.builder()
            .bankType(BankType.SEB)
            .requestId("real-data-test-" + System.currentTimeMillis())
            .trackingId("real-data-test")
            .rawResponse(xml)
            .timezone(SEB_GATEWAY_TIME_ZONE.getId())
            .receivedAt(now)
            .build();
    bankingMessageRepository.save(message);
  }
}
