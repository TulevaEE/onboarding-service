package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.auth.UserFixture;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionLedgerService;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.*;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Transactional
class MultiFundNavVerificationTest {

  static final Path NAV_CSV_DIR = Path.of("src/test/resources/nav-test-data");

  @Autowired NavCalculationService navCalculationService;
  @Autowired NavPositionLedger navPositionLedger;
  @Autowired FundPositionLedgerService fundPositionLedgerService;
  @Autowired FundPositionRepository fundPositionRepository;
  @Autowired LedgerService ledgerService;
  @Autowired EntityManager entityManager;
  @Autowired JdbcClient jdbcClient;

  User testUser = UserFixture.sampleUser().personalCode("38001010001").build();

  @ParameterizedTest
  @MethodSource("multiFundTestCases")
  @SneakyThrows
  void navCalculationMatchesCsvExpectation(MultiFundTestCase testCase) {
    Instant ledgerTime =
        testCase.data.navDate.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();
    ClockHolder.setClock(Clock.fixed(ledgerTime, ZoneId.of("UTC")));
    try {
      setupPositionsInLedger(testCase.data);
      insertFeeRates(testCase.data.fund, testCase.data.navDate);
      setupUnitsOutstanding(testCase.data);
      insertPricesFromCsv(testCase.data);
      entityManager.flush();
      entityManager.clear();

      var result = navCalculationService.calculate(testCase.data.fund, testCase.calculationDate);

      assertThat(result.cashPosition()).isEqualByComparingTo(testCase.data.cashPosition);
      assertThat(result.receivables()).isEqualByComparingTo(testCase.data.tradeReceivables);
      assertThat(result.payables()).isEqualByComparingTo(testCase.data.tradePayables.negate());
      assertThat(result.pendingSubscriptions())
          .isEqualByComparingTo(testCase.data.pendingSubscriptions);
      assertThat(result.managementFeeAccrual()).isNotNegative();
      assertThat(result.depotFeeAccrual()).isNotNegative();
      assertThat(result.unitsOutstanding().setScale(3, HALF_UP))
          .isEqualByComparingTo(testCase.data.unitsOutstanding.setScale(3, HALF_UP));
      assertThat(result.securitiesValue().subtract(testCase.data.securitiesTotal).abs())
          .as("Securities rounding tolerance (cross-system)")
          .isLessThan(new BigDecimal("1.00"));
      assertThat(result.aum()).as("AUM is positive").isPositive();
      assertThat(result.navPerUnit()).as("NAV per unit is positive").isPositive();
    } finally {
      ClockHolder.setDefaultClock();
    }
  }

  @Test
  @SneakyThrows
  void navCalculation_doesNotDoubleCountReceivablesWhenUsingProductionLedgerPath() {
    Path csvFile = NAV_CSV_DIR.resolve("TUV100 NAV arvutamine 03032026.csv");
    FundNavCsvData data = parseMultiFundCsv(csvFile, TUV100);
    LocalDate calculationDate = LocalDate.of(2026, 3, 3);

    Instant ledgerTime = data.navDate.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();
    ClockHolder.setClock(Clock.fixed(ledgerTime, ZoneId.of("UTC")));
    try {
      data.positions.forEach(fundPositionRepository::save);
      entityManager.flush();

      fundPositionLedgerService.recordPositionsToLedger(TUV100, data.navDate);

      insertFeeRates(TUV100, data.navDate);
      setupUnitsOutstanding(data);
      insertPricesFromCsv(data);
      entityManager.flush();
      entityManager.clear();

      var result = navCalculationService.calculate(TUV100, calculationDate);

      assertThat(result.receivables()).isEqualByComparingTo(data.tradeReceivables);
      assertThat(result.pendingSubscriptions()).isEqualByComparingTo(data.pendingSubscriptions);
      assertThat(result.aum()).as("AUM should not double-count receivables").isPositive();
    } finally {
      ClockHolder.setDefaultClock();
    }
  }

  private void setupPositionsInLedger(FundNavCsvData data) {
    data.positions.forEach(fundPositionRepository::save);
    entityManager.flush();

    var securityPositions =
        fundPositionRepository.findByNavDateAndFundAndAccountType(
            data.navDate, data.fund, SECURITY);
    var securitiesUnits = new HashMap<String, BigDecimal>();
    securityPositions.stream()
        .filter(p -> p.getAccountId() != null)
        .forEach(p -> securitiesUnits.put(p.getAccountId(), p.getQuantity()));

    navPositionLedger.recordPositions(
        data.fund,
        data.navDate,
        securitiesUnits,
        data.cashPosition,
        data.tradeReceivables,
        data.tradePayables);
  }

  private void insertFeeRates(TulevaFund fund, LocalDate validFrom) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fee_rate (fund_code, fee_type, annual_rate, valid_from, created_by)
            VALUES (:fundCode, 'MANAGEMENT', :annualRate, :validFrom, 'TEST')
            """)
        .param("fundCode", fund.name())
        .param("annualRate", new BigDecimal("0.0029"))
        .param("validFrom", validFrom)
        .update();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fee_rate (fund_code, fee_type, annual_rate, valid_from, created_by)
            VALUES (:fundCode, 'DEPOT', :annualRate, :validFrom, 'TEST')
            """)
        .param("fundCode", fund.name())
        .param("annualRate", new BigDecimal("0.00035"))
        .param("validFrom", validFrom)
        .update();
  }

  private void setupUnitsOutstanding(FundNavCsvData data) {
    LedgerAccount fundUnitsAccount =
        ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, data.fund);
    LedgerAccount userFundUnitsAccount =
        ledgerService.getPartyAccount(testUser.getPersonalCode(), PERSON, FUND_UNITS);

    BigDecimal units = data.unitsOutstanding.setScale(5, HALF_UP);
    Instant transactionDate = data.navDate.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();

    var transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(transactionDate)
            .build();

    var systemEntry =
        LedgerEntry.builder()
            .amount(units)
            .assetType(FUND_UNIT)
            .account(fundUnitsAccount)
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

  private static final Map<String, String> ISIN_TO_TICKER =
      Map.ofEntries(
          Map.entry("IE00BFNM3G45", "SGAS.XETRA"),
          Map.entry("IE00BFNM3D14", "SLMC.XETRA"),
          Map.entry("IE00BFNM3L97", "SGAJ.XETRA"),
          Map.entry("IE00BMDBMY19", "ESGM.XETRA"),
          Map.entry("IE00BJZ2DC62", "XRSM.XETRA"),
          Map.entry("LU0476289540", "D5BH.XETRA"),
          Map.entry("IE000O58J820", "V3YA.XETRA"),
          Map.entry("LU1291099718", "EEUX.XETRA"),
          Map.entry("LU1291106356", "PAC.XETRA"),
          Map.entry("LU1291102447", "EJAP.XETRA"),
          Map.entry("IE000F60HVH9", "USAS.PA.EODHD"),
          Map.entry("IE00BFG1TM61", "IE00BFG1TM61.EUFUND"),
          Map.entry("IE0009FT4LX4", "IE0009FT4LX4.EUFUND"),
          Map.entry("IE00BKPTWY98", "IE00BKPTWY98.EUFUND"),
          Map.entry("LU0826455353", "LU0826455353.EUFUND"),
          Map.entry("IE0031080751", "IE0031080751.EUFUND"),
          Map.entry("LU0839970364", "LU0839970364.EUFUND"),
          Map.entry("IE0005032192", "IE0005032192.EUFUND"));

  private void insertPricesFromCsv(FundNavCsvData data) {
    Instant updatedAt = data.navDate.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();
    data.securityPrices.forEach(
        (isin, price) -> {
          String ticker = ISIN_TO_TICKER.get(isin);
          if (ticker != null) {
            entityManager
                .createNativeQuery(
                    "INSERT INTO index_values (key, date, value, provider, updated_at) VALUES (:key, :date, :value, 'EODHD', :updatedAt)")
                .setParameter("key", ticker)
                .setParameter("date", data.navDate)
                .setParameter("value", price)
                .setParameter("updatedAt", java.sql.Timestamp.from(updatedAt))
                .executeUpdate();
          }
        });
  }

  @SneakyThrows
  static List<MultiFundTestCase> multiFundTestCases() {
    List<MultiFundTestCase> cases = new ArrayList<>();
    for (TulevaFund fund : TulevaFund.values()) {
      for (String dateStr : List.of("03032026", "04032026")) {
        String filename = fund.getCode() + " NAV arvutamine " + dateStr + ".csv";
        Path csvFile = NAV_CSV_DIR.resolve(filename);
        if (Files.exists(csvFile)) {
          LocalDate calculationDate =
              LocalDate.of(
                  Integer.parseInt(dateStr.substring(4)),
                  Integer.parseInt(dateStr.substring(2, 4)),
                  Integer.parseInt(dateStr.substring(0, 2)));
          FundNavCsvData data = parseMultiFundCsv(csvFile, fund);
          cases.add(new MultiFundTestCase(fund, calculationDate, data, csvFile));
        }
      }
    }
    return cases;
  }

  @SneakyThrows
  static FundNavCsvData parseMultiFundCsv(Path csvFile, TulevaFund fund) {
    List<String> lines = Files.readAllLines(csvFile);
    var data = new FundNavCsvData();
    data.fund = fund;

    for (int i = 1; i < lines.size(); i++) {
      String line = lines.get(i).trim();
      if (line.isEmpty()) continue;
      String[] fields = line.split(",");
      String accountType = fields[2];
      String accountName = fields[3];
      String accountId = fields.length > 4 ? fields[4] : "";
      BigDecimal quantity = fields[5].isEmpty() ? ZERO : new BigDecimal(fields[5]);
      BigDecimal marketPrice = fields[6].isEmpty() ? ZERO : new BigDecimal(fields[6]);
      String currency = fields[7];
      BigDecimal marketValue = new BigDecimal(fields[8]);

      if (data.navDate == null) {
        data.navDate = LocalDate.parse(fields[0]);
      }

      switch (accountType) {
        case "CASH" -> {
          data.cashPosition = marketValue;
          data.positions.add(
              buildPosition(
                  data.navDate,
                  fund,
                  CASH,
                  accountName,
                  null,
                  quantity,
                  marketPrice,
                  currency,
                  marketValue));
        }
        case "SECURITY" -> {
          data.securitiesTotal = data.securitiesTotal.add(marketValue);
          if (!accountId.isEmpty()) {
            data.securityPrices.put(accountId, marketPrice);
          }
          data.positions.add(
              buildPosition(
                  data.navDate,
                  fund,
                  SECURITY,
                  accountName,
                  accountId.isEmpty() ? null : accountId,
                  quantity,
                  marketPrice,
                  currency,
                  marketValue));
        }
        case "RECEIVABLES" -> {
          if (accountName.startsWith("Total receivables")) {
            data.tradeReceivables = marketValue;
          } else if (accountName.startsWith("Receivables of outstanding units")) {
            data.pendingSubscriptions = marketValue;
          }
          data.positions.add(
              buildPosition(
                  data.navDate,
                  fund,
                  RECEIVABLES,
                  accountName,
                  accountId.isEmpty() ? null : accountId,
                  quantity,
                  marketPrice,
                  currency,
                  marketValue));
        }
        case "LIABILITY" -> {
          if (accountName.startsWith("Total payables")) {
            data.tradePayables = marketValue;
          } else if (accountName.startsWith("Payables of redeemed units")) {
            data.pendingRedemptions = marketValue;
          }
          data.positions.add(
              buildPosition(
                  data.navDate,
                  fund,
                  LIABILITY,
                  accountName,
                  accountId.isEmpty() ? null : accountId,
                  quantity,
                  marketPrice,
                  currency,
                  marketValue));
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
          data.expectedAum = marketValue;
        }
      }
    }

    return data;
  }

  private static FundPosition buildPosition(
      LocalDate navDate,
      TulevaFund fund,
      ee.tuleva.onboarding.investment.position.AccountType accountType,
      String accountName,
      String accountId,
      BigDecimal quantity,
      BigDecimal marketPrice,
      String currency,
      BigDecimal marketValue) {
    return FundPosition.builder()
        .navDate(navDate)
        .fund(fund)
        .accountType(accountType)
        .accountName(accountName)
        .accountId(accountId)
        .quantity(quantity)
        .marketPrice(marketPrice)
        .currency(currency)
        .marketValue(marketValue)
        .createdAt(Instant.now())
        .build();
  }

  record MultiFundTestCase(
      TulevaFund fund, LocalDate calculationDate, FundNavCsvData data, Path csvFile) {
    @Override
    public String toString() {
      return fund.getCode() + " " + calculationDate;
    }
  }

  static class FundNavCsvData {
    TulevaFund fund;
    LocalDate navDate;
    BigDecimal cashPosition = ZERO;
    BigDecimal tradeReceivables = ZERO;
    BigDecimal tradePayables = ZERO;
    BigDecimal pendingSubscriptions = ZERO;
    BigDecimal pendingRedemptions = ZERO;
    BigDecimal managementFeeAccrual = ZERO;
    BigDecimal depotFeeAccrual = ZERO;
    BigDecimal unitsOutstanding = ZERO;
    BigDecimal expectedNavPerUnit = ZERO;
    BigDecimal expectedAum = ZERO;
    BigDecimal securitiesTotal = ZERO;
    final List<FundPosition> positions = new ArrayList<>();
    final Map<String, BigDecimal> securityPrices = new HashMap<>();
  }
}
