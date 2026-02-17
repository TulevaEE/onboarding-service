package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.investment.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.*;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.user.User;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Transactional
class NavCalculationIntegrationTest {

  @Autowired NavCalculationService navCalculationService;
  @Autowired LedgerService ledgerService;
  @Autowired FundPositionRepository fundPositionRepository;
  @Autowired EntityManager entityManager;

  User testUser = sampleUser().personalCode("38001010001").build();

  @ParameterizedTest
  @MethodSource("navCsvFiles")
  void calculatedNavMatchesManualCalculation(Path csvFile) throws IOException {
    var csvData = parseCsv(csvFile);
    var calculationDate = parseCalculationDateFromFilename(csvFile);

    setupLedgerBalances(csvData);
    setupFundPosition(csvData.navDate);
    entityManager.flush();

    var result = navCalculationService.calculate(TKF100, calculationDate);

    assertThat(result.navPerUnit().setScale(4, RoundingMode.HALF_UP))
        .isEqualByComparingTo(csvData.expectedNavPerUnit.setScale(4, RoundingMode.HALF_UP));
  }

  static Stream<Path> navCsvFiles() throws IOException {
    var directory = Path.of("src/test/resources/banking/seb/real-data/nav");
    if (!Files.exists(directory)) {
      return Stream.empty();
    }
    return Files.list(directory).filter(path -> path.toString().endsWith(".csv")).sorted();
  }

  private void setupLedgerBalances(CsvData csvData) {
    createSystemAccountBalance(CASH_POSITION, csvData.cashPosition, EUR);
    createSystemAccountBalance(SECURITIES_VALUE, csvData.securitiesValue, EUR);
    createSystemAccountBalance(TRADE_RECEIVABLES, csvData.tradeReceivables, EUR);
    createSystemAccountBalance(TRADE_PAYABLES, csvData.tradePayables, EUR);
    createSystemAccountBalance(MANAGEMENT_FEE_ACCRUAL, csvData.managementFeeAccrual, EUR);
    createSystemAccountBalance(DEPOT_FEE_ACCRUAL, csvData.depotFeeAccrual, EUR);
    createSystemAccountBalance(BLACKROCK_ADJUSTMENT, ZERO, EUR);
    createFundUnitsOutstandingBalance(csvData.unitsOutstanding);
  }

  private void createSystemAccountBalance(
      SystemAccount systemAccount, BigDecimal amount, AssetType assetType) {
    if (amount.compareTo(ZERO) == 0) {
      ledgerService.getSystemAccount(systemAccount);
      ledgerService.getSystemAccount(NAV_EQUITY);
      return;
    }

    LedgerAccount account = ledgerService.getSystemAccount(systemAccount);
    LedgerAccount navEquity = ledgerService.getSystemAccount(NAV_EQUITY);

    var transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .build();

    var entry =
        LedgerEntry.builder()
            .amount(amount)
            .assetType(assetType)
            .account(account)
            .transaction(transaction)
            .build();

    var counterEntry =
        LedgerEntry.builder()
            .amount(amount.negate())
            .assetType(assetType)
            .account(navEquity)
            .transaction(transaction)
            .build();

    transaction.getEntries().add(entry);
    transaction.getEntries().add(counterEntry);
    entityManager.persist(transaction);
  }

  private void createFundUnitsOutstandingBalance(BigDecimal unitsOutstanding) {
    LedgerAccount fundUnitsOutstandingAccount =
        ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING);
    LedgerAccount userFundUnitsAccount = ledgerService.getUserAccount(testUser, FUND_UNITS);

    BigDecimal negatedUnits = unitsOutstanding.negate().setScale(5, RoundingMode.HALF_UP);

    var transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .build();

    var systemEntry =
        LedgerEntry.builder()
            .amount(negatedUnits)
            .assetType(FUND_UNIT)
            .account(fundUnitsOutstandingAccount)
            .transaction(transaction)
            .build();

    var userEntry =
        LedgerEntry.builder()
            .amount(negatedUnits.negate())
            .assetType(FUND_UNIT)
            .account(userFundUnitsAccount)
            .transaction(transaction)
            .build();

    transaction.getEntries().add(systemEntry);
    transaction.getEntries().add(userEntry);
    entityManager.persist(transaction);
  }

  private void setupFundPosition(LocalDate reportingDate) {
    var position =
        FundPosition.builder()
            .reportingDate(reportingDate)
            .fund(TKF100)
            .accountType(AccountType.NAV)
            .accountName("Net Asset Value")
            .accountId("EE0000003283")
            .quantity(BigDecimal.ONE)
            .marketPrice(BigDecimal.ONE)
            .currency("EUR")
            .marketValue(BigDecimal.ONE)
            .build();
    fundPositionRepository.save(position);
  }

  private LocalDate parseCalculationDateFromFilename(Path csvFile) {
    String filename = csvFile.getFileName().toString();
    String datePart = filename.replaceAll("\\D", "");
    var formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
    return LocalDate.parse(datePart, formatter);
  }

  private CsvData parseCsv(Path csvFile) throws IOException {
    List<String> lines = Files.readAllLines(csvFile);
    var data = new CsvData();

    BigDecimal securitiesSum = ZERO;

    for (int i = 1; i < lines.size(); i++) {
      String[] fields = lines.get(i).split(",");
      String accountType = fields[2];
      String accountName = fields[3];
      BigDecimal quantity = new BigDecimal(fields[5]);
      BigDecimal marketPrice = new BigDecimal(fields[6]);
      BigDecimal marketValue = new BigDecimal(fields[8]);

      if (data.navDate == null) {
        data.navDate = LocalDate.parse(fields[0]);
      }

      switch (accountType) {
        case "CASH" -> data.cashPosition = marketValue;
        case "SECURITY" -> securitiesSum = securitiesSum.add(marketValue);
        case "RECEIVABLES" -> {
          if (accountName.startsWith("Total receivables")) {
            data.tradeReceivables = marketValue;
          }
        }
        case "LIABILITY" -> {
          if (accountName.startsWith("Total payables")) {
            data.tradePayables = marketValue;
          }
        }
        case "LIABILITY_FEE" -> {
          if (accountName.startsWith("Management fee")) {
            data.managementFeeAccrual = marketValue;
          } else if (accountName.startsWith("Custody fee")) {
            data.depotFeeAccrual = marketValue;
          }
        }
        case "UNITS" -> {
          data.unitsOutstanding = quantity;
          data.expectedNavPerUnit = marketPrice;
        }
      }
    }

    data.securitiesValue = securitiesSum;
    return data;
  }

  private static class CsvData {
    LocalDate navDate;
    BigDecimal cashPosition = ZERO;
    BigDecimal securitiesValue = ZERO;
    BigDecimal tradeReceivables = ZERO;
    BigDecimal tradePayables = ZERO;
    BigDecimal managementFeeAccrual = ZERO;
    BigDecimal depotFeeAccrual = ZERO;
    BigDecimal unitsOutstanding = ZERO;
    BigDecimal expectedNavPerUnit = ZERO;
  }
}
