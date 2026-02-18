package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.investment.position.FundPositionImportService;
import ee.tuleva.onboarding.investment.position.parser.SebFundPositionParser;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.ledger.*;
import ee.tuleva.onboarding.user.User;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Transactional
class NavPipelineIntegrationTest {

  static final Path NAV_CSV_DIR = Path.of("src/test/resources/nav-test-data");

  @Autowired InvestmentReportService investmentReportService;
  @Autowired SebFundPositionParser sebFundPositionParser;
  @Autowired FundPositionImportService fundPositionImportService;
  @Autowired NavPositionLedger navPositionLedger;
  @Autowired NavFeeAccrualLedger navFeeAccrualLedger;
  @Autowired NavCalculationService navCalculationService;
  @Autowired NavPublisher navPublisher;
  @Autowired LedgerService ledgerService;
  @Autowired EntityManager entityManager;

  User testUser = sampleUser().personalCode("38001010001").build();

  @ParameterizedTest
  @MethodSource("testPairs")
  @SneakyThrows
  void fullPipelineProducesExpectedNav(NavTestPair pair) {
    var navData = parseNavCsv(pair.navCsvFile);

    importPositionReport(pair.positionReportFile, navData.navDate);
    recordPositionsToLedger(navData);
    recordFeeAccruals(navData);
    insertPrices(pair.calculationDate);
    issueFundUnits(navData.unitsOutstanding);
    entityManager.flush();

    var result = navCalculationService.calculate(TKF100, pair.calculationDate);
    navPublisher.publish(result);

    assertThat(result.cashPosition()).isEqualByComparingTo(navData.cashPosition);
    assertThat(result.receivables()).isEqualByComparingTo(navData.tradeReceivables);
    assertThat(result.payables()).isEqualByComparingTo(navData.tradePayables.negate());
    assertThat(result.managementFeeAccrual())
        .isEqualByComparingTo(navData.managementFeeAccrual.negate());
    assertThat(result.depotFeeAccrual()).isEqualByComparingTo(navData.depotFeeAccrual.negate());
    assertThat(result.unitsOutstanding().setScale(3, HALF_UP))
        .isEqualByComparingTo(navData.unitsOutstanding.setScale(3, HALF_UP));
    assertThat(result.pendingSubscriptions()).isEqualByComparingTo(ZERO);
    assertThat(result.pendingRedemptions()).isEqualByComparingTo(ZERO);
    assertThat(result.blackrockAdjustment()).isEqualByComparingTo(ZERO);
    assertThat(result.navPerUnit().setScale(4, HALF_UP))
        .isEqualByComparingTo(navData.expectedNavPerUnit.setScale(4, HALF_UP));
  }

  @SneakyThrows
  static Stream<NavTestPair> testPairs() {
    return Files.list(NAV_CSV_DIR)
        .filter(path -> path.getFileName().toString().startsWith("TKF NAV"))
        .sorted()
        .map(NavPipelineIntegrationTest::toTestPair)
        .flatMap(Optional::stream);
  }

  private static Optional<NavTestPair> toTestPair(Path navCsvFile) {
    LocalDate navDate = parseNavDate(navCsvFile);
    LocalDate calculationDate = parseCalculationDateFromFilename(navCsvFile);
    Path positionReport = NAV_CSV_DIR.resolve(navDate + "_positions.csv");
    if (Files.exists(positionReport)) {
      return Optional.of(new NavTestPair(navCsvFile, positionReport, navDate, calculationDate));
    }
    return Optional.empty();
  }

  private static LocalDate parseCalculationDateFromFilename(Path csvFile) {
    String filename = csvFile.getFileName().toString();
    String datePart = filename.replaceAll("\\D", "");
    return LocalDate.parse(datePart, DateTimeFormatter.ofPattern("ddMMyyyy"));
  }

  @SneakyThrows
  private static LocalDate parseNavDate(Path navCsvFile) {
    List<String> lines = Files.readAllLines(navCsvFile);
    String[] fields = lines.get(1).split(",");
    return LocalDate.parse(fields[0]);
  }

  @SneakyThrows
  private void importPositionReport(Path positionReportFile, LocalDate reportDate) {
    byte[] csvBytes = Files.readAllBytes(positionReportFile);
    var report =
        investmentReportService.saveReport(
            SEB, POSITIONS, reportDate, new ByteArrayInputStream(csvBytes), ';', 5, Map.of());
    var positions = sebFundPositionParser.parse(report.getRawData(), reportDate);
    fundPositionImportService.importPositions(positions);
  }

  private void recordPositionsToLedger(NavCsvData navData) {
    navPositionLedger.recordPositions(
        TKF100.name(),
        navData.navDate,
        navData.securitiesUnitsByIsin,
        navData.cashPosition,
        navData.tradeReceivables,
        navData.tradePayables);
  }

  private void recordFeeAccruals(NavCsvData navData) {
    if (navData.managementFeeAccrual.signum() != 0) {
      navFeeAccrualLedger.recordFeeAccrual(
          TKF100.name(),
          navData.navDate,
          MANAGEMENT_FEE_ACCRUAL,
          navData.managementFeeAccrual.negate());
    }
    if (navData.depotFeeAccrual.signum() != 0) {
      navFeeAccrualLedger.recordFeeAccrual(
          TKF100.name(), navData.navDate, DEPOT_FEE_ACCRUAL, navData.depotFeeAccrual.negate());
    }
  }

  @SneakyThrows
  private void insertPrices(LocalDate calculationDate) {
    var priceLines = Files.readAllLines(Path.of("src/test/resources/nav-test-data/prices.csv"));
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

  private void issueFundUnits(BigDecimal unitsOutstanding) {
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

  @SneakyThrows
  private NavCsvData parseNavCsv(Path csvFile) {
    List<String> lines = Files.readAllLines(csvFile);
    var data = new NavCsvData();

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
          String isin = fields[4];
          if (!isin.isEmpty() && quantity.signum() != 0) {
            data.securitiesUnitsByIsin.put(isin, quantity.setScale(5, HALF_UP));
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

    return data;
  }

  record NavTestPair(
      Path navCsvFile, Path positionReportFile, LocalDate navDate, LocalDate calculationDate) {
    @Override
    public String toString() {
      return navCsvFile.getFileName().toString();
    }
  }

  private static class NavCsvData {
    LocalDate navDate;
    BigDecimal cashPosition = ZERO;
    Map<String, BigDecimal> securitiesUnitsByIsin = new java.util.HashMap<>();
    BigDecimal tradeReceivables = ZERO;
    BigDecimal tradePayables = ZERO;
    BigDecimal managementFeeAccrual = ZERO;
    BigDecimal depotFeeAccrual = ZERO;
    BigDecimal unitsOutstanding = ZERO;
    BigDecimal expectedNavPerUnit = ZERO;
  }
}
