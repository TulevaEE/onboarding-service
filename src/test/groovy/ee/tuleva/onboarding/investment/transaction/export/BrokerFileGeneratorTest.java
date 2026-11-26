package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.FT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.FT_ETF;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.SEB_ETF;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.SEB_FUND;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.UUID_WORKBOOK;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.fund.FundAccounts;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRefusedException;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

class BrokerFileGeneratorTest {

  private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

  private final TransactionExportService service = new TransactionExportService(fundAccounts());
  private final BrokerFileGenerator generator = new BrokerFileGenerator(service);

  private static FundAccounts fundAccounts() {
    var accounts = new EnumMap<TulevaFund, FundAccounts.Account>(TulevaFund.class);
    accounts.put(TKF100, new FundAccounts.Account("EE001234567890123499", "VP00004", "gw"));
    accounts.put(TUV100, new FundAccounts.Account("EE001234567890123410", "VP00003", "gw"));
    accounts.put(TUK75, new FundAccounts.Account("EE001234567890123475", "VP00001", "gw"));
    accounts.put(TUK00, new FundAccounts.Account("EE001234567890123400", "VP00002", "gw"));
    var fundAccounts = new FundAccounts();
    fundAccounts.setFunds(accounts);
    return fundAccounts;
  }

  @Test
  void generateBrokerFiles_makesOneFilePerVenueWithOrders_acrossEveryFundOfTheDay()
      throws Exception {
    var batch = buildBatch();
    var tradeDate = LocalDate.of(2026, 1, 15);
    var tukEtf = buildOrder(batch, TUK75, "IE00FT", BUY, ETF, FT, new BigDecimal("200000"), 800L);
    var tuvEtf = buildOrder(batch, TUV100, "IE00FT", BUY, ETF, FT, new BigDecimal("150000"), 600L);
    var tuk00Fund =
        buildOrder(batch, TUK00, "LU00C", BUY, FUND, SEB, new BigDecimal("75000"), null);
    var orders = List.of(tukEtf, tuvEtf, tuk00Fund);
    Map<String, String> labels = Map.of("IE00FT", "iShares ESG MSCI");
    Map<String, String> bbgTickers = Map.of("IE00FT", "ESGM GY");

    Map<ExportFile, byte[]> files =
        generator.generate(orders, labels, Map.of(), bbgTickers, tradeDate);

    assertThat(files).containsOnlyKeys(FT_ETF, SEB_FUND, UUID_WORKBOOK);
    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(files.get(FT_ETF)))) {
      var theOneEtfRow = workbook.getSheetAt(0).getRow(1);
      assertThat(theOneEtfRow.getCell(6).getStringCellValue()).isEqualTo("IE00FT");
      assertThat(theOneEtfRow.getCell(2).getNumericCellValue()).isEqualTo(1400.0);
      assertThat(theOneEtfRow.getCell(9).getNumericCellValue()).isEqualTo(800.0);
      assertThat(theOneEtfRow.getCell(10).getNumericCellValue()).isEqualTo(600.0);
    }
    assertThat(csvLines(files.get(SEB_FUND)))
        .isEqualTo(csvLines(service.generateSebFundExport(orders, labels)));
  }

  @Test
  void generateBrokerFiles_withoutOrders_makesNoFileAtAll() {
    assertThat(
            generator.generate(List.of(), Map.of(), Map.of(), Map.of(), LocalDate.of(2026, 1, 15)))
        .isEmpty();
  }

  @Test
  void generateBrokerFiles_aSebEtfOrderAlone_makesTheSebEtfFileAndTheUuidWorkbook() {
    var sebEtf =
        buildOrder(buildBatch(), TKF100, "IE00SEB", BUY, ETF, SEB, new BigDecimal("50000"), 200L);

    assertThat(
            generator.generate(
                List.of(sebEtf), Map.of(), Map.of(), Map.of(), LocalDate.of(2026, 1, 15)))
        .containsOnlyKeys(SEB_ETF, UUID_WORKBOOK);
  }

  @Test
  void generate_aFundOrderAtFt_isRefusedBeforeAnyFile_soNoOrderGoesToTwoDesks() {
    var fundAtFt =
        buildOrder(buildBatch(), TUK00, "LU00C", BUY, FUND, FT, new BigDecimal("75000"), null);
    var etfAtFt = buildOrder(buildBatch(), TUK75, "IE00FT", BUY, ETF, FT, new BigDecimal("1"), 1L);

    assertThatThrownBy(
            () ->
                generator.generate(
                    List.of(etfAtFt, fundAtFt),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    LocalDate.of(2026, 1, 15)))
        .isInstanceOf(TransactionBatchRefusedException.class);
  }

  private static List<String> csvLines(byte[] csv) {
    return new String(csv, UTF_8).lines().toList();
  }

  private static TransactionBatch buildBatch() {
    return TransactionBatch.builder().id(1L).createdBy("system").createdAt(NOW).build();
  }

  private static TransactionOrder buildOrder(
      TransactionBatch batch,
      TulevaFund fund,
      String isin,
      TransactionType type,
      InstrumentType instrumentType,
      OrderVenue venue,
      BigDecimal amount,
      Long quantity) {
    return TransactionOrder.builder()
        .batch(batch)
        .fund(fund)
        .instrumentIsin(isin)
        .transactionType(type)
        .instrumentType(instrumentType)
        .orderVenue(venue)
        .orderAmount(amount)
        .orderQuantity(quantity == null ? null : BigDecimal.valueOf(quantity))
        .orderUuid(UUID.randomUUID())
        .orderTimestamp(NOW)
        .expectedSettlementDate(LocalDate.of(2026, 1, 20))
        .createdAt(NOW)
        .build();
  }
}
