package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.investment.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.position.AccountType.NAV;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.*;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.user.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.SneakyThrows;
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
  @SneakyThrows
  void calculatedNavMatchesManualCalculation(Path csvFile) {
    var csvData = parseCsv(csvFile);
    var calculationDate = parseCalculationDateFromFilename(csvFile);

    setupLedgerBalances(csvData, calculationDate);
    setupFundPosition(csvData.navDate);
    entityManager.flush();

    var result = navCalculationService.calculate(TKF100, calculationDate);

    assertThat(result.cashPosition()).isEqualByComparingTo(csvData.cashPosition);
    assertThat(result.receivables()).isEqualByComparingTo(csvData.tradeReceivables);
    assertThat(result.payables()).isEqualByComparingTo(csvData.tradePayables.negate());
    assertThat(result.managementFeeAccrual())
        .isEqualByComparingTo(csvData.managementFeeAccrual.negate());
    assertThat(result.depotFeeAccrual()).isEqualByComparingTo(csvData.depotFeeAccrual.negate());
    assertThat(result.unitsOutstanding().setScale(3, HALF_UP))
        .isEqualByComparingTo(csvData.unitsOutstanding.setScale(3, HALF_UP));
    assertThat(result.pendingSubscriptions()).isEqualByComparingTo(ZERO);
    assertThat(result.pendingRedemptions()).isEqualByComparingTo(ZERO);
    assertThat(result.blackrockAdjustment()).isEqualByComparingTo(ZERO);
    assertThat(result.navPerUnit().setScale(3, HALF_UP))
        .isEqualByComparingTo(csvData.expectedNavPerUnit.setScale(3, HALF_UP));
  }

  @SneakyThrows
  static Stream<Path> navCsvFiles() {
    var directory = Path.of("src/test/resources/nav-test-data");
    return Files.list(directory)
        .filter(path -> path.getFileName().toString().startsWith("TKF NAV"))
        .sorted();
  }

  private void setupLedgerBalances(CsvData csvData, LocalDate calculationDate) {
    createSystemAccountBalance(CASH_POSITION, csvData.cashPosition, EUR);
    createSecuritiesUnitBalances(csvData);
    insertEodhdPrices(calculationDate);
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

  private void createSecuritiesUnitBalances(CsvData csvData) {
    csvData.securitiesUnitsByIsin.forEach(
        (isin, quantity) -> {
          BigDecimal units = quantity.setScale(5, HALF_UP);

          var securitiesUnitsAccount =
              LedgerAccount.builder()
                  .name(SECURITIES_UNITS.getAccountName(isin))
                  .purpose(LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT)
                  .accountType(SECURITIES_UNITS.getAccountType())
                  .assetType(SECURITIES_UNITS.getAssetType())
                  .build();
          entityManager.persist(securitiesUnitsAccount);

          var securitiesUnitsEquityAccount =
              LedgerAccount.builder()
                  .name(SECURITIES_UNITS_EQUITY.getAccountName(isin))
                  .purpose(LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT)
                  .accountType(SECURITIES_UNITS_EQUITY.getAccountType())
                  .assetType(SECURITIES_UNITS_EQUITY.getAssetType())
                  .build();
          entityManager.persist(securitiesUnitsEquityAccount);

          var transaction =
              LedgerTransaction.builder()
                  .transactionType(ADJUSTMENT)
                  .transactionDate(Instant.now())
                  .build();

          var entry =
              LedgerEntry.builder()
                  .amount(units)
                  .assetType(FUND_UNIT)
                  .account(securitiesUnitsAccount)
                  .transaction(transaction)
                  .build();

          var counterEntry =
              LedgerEntry.builder()
                  .amount(units.negate())
                  .assetType(FUND_UNIT)
                  .account(securitiesUnitsEquityAccount)
                  .transaction(transaction)
                  .build();

          transaction.getEntries().add(entry);
          transaction.getEntries().add(counterEntry);
          entityManager.persist(transaction);
        });
  }

  @SneakyThrows
  private void insertEodhdPrices(LocalDate calculationDate) {
    var priceLines =
        Files.readAllLines(Path.of("src/test/resources/nav-test-data/eodhd-prices.csv"));
    for (int i = 1; i < priceLines.size(); i++) {
      String[] parts = priceLines.get(i).split(",");
      LocalDate priceDate = LocalDate.parse(parts[1]);
      if (priceDate.isBefore(calculationDate)) {
        entityManager
            .createNativeQuery(
                "INSERT INTO index_values (key, date, value, provider, updated_at) VALUES (:key, :date, :value, 'EODHD', CURRENT_TIMESTAMP)")
            .setParameter("key", parts[0])
            .setParameter("date", priceDate)
            .setParameter("value", new BigDecimal(parts[2]))
            .executeUpdate();
      }
    }
  }

  private void createFundUnitsOutstandingBalance(BigDecimal unitsOutstanding) {
    LedgerAccount fundUnitsOutstandingAccount =
        ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING);
    LedgerAccount userFundUnitsAccount = ledgerService.getUserAccount(testUser, FUND_UNITS);

    BigDecimal units = unitsOutstanding.setScale(5, HALF_UP);

    var transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .build();

    var systemEntry =
        LedgerEntry.builder()
            .amount(units)
            .assetType(FUND_UNIT)
            .account(fundUnitsOutstandingAccount)
            .transaction(transaction)
            .build();

    var userEntry =
        LedgerEntry.builder()
            .amount(units.negate())
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
            .accountType(NAV)
            .accountName("Net Asset Value")
            .accountId("EE0000003283")
            .quantity(ONE)
            .marketPrice(ONE)
            .currency("EUR")
            .marketValue(ONE)
            .build();
    fundPositionRepository.save(position);
  }

  private LocalDate parseCalculationDateFromFilename(Path csvFile) {
    String filename = csvFile.getFileName().toString();
    String datePart = filename.replaceAll("\\D", "");
    var formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
    return LocalDate.parse(datePart, formatter);
  }

  @SneakyThrows
  private CsvData parseCsv(Path csvFile) {
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
        case "SECURITY" -> {
          securitiesSum = securitiesSum.add(marketValue);
          String isin = fields[4];
          if (!isin.isEmpty() && quantity.signum() != 0) {
            data.securitiesUnitsByIsin.put(isin, quantity);
          }
        }
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
    Map<String, BigDecimal> securitiesUnitsByIsin = new HashMap<>();
    BigDecimal tradeReceivables = ZERO;
    BigDecimal tradePayables = ZERO;
    BigDecimal managementFeeAccrual = ZERO;
    BigDecimal depotFeeAccrual = ZERO;
    BigDecimal unitsOutstanding = ZERO;
    BigDecimal expectedNavPerUnit = ZERO;
  }
}
