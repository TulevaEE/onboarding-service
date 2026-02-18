package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.fund.TulevaFund.*;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.FT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.time.TestClockHolder.now;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

class TransactionExportServiceTest {

  private final TransactionExportService service = new TransactionExportService();

  @Test
  void generateOrdersExport_createsValidXlsx() throws Exception {
    var batch = buildBatch();

    var order1 = buildOrder(batch, TUV100, "IE00A", BUY, ETF, SEB, new BigDecimal("100000"), 500L);
    var order2 = buildOrder(batch, TUV100, "IE00B", SELL, ETF, SEB, new BigDecimal("50000"), 250L);
    var order3 = buildOrder(batch, TUV100, "LU00C", BUY, FUND, SEB, new BigDecimal("75000"), null);

    byte[] xlsx = service.generateOrdersExport(List.of(order1, order2, order3));

    assertThat(xlsx).isNotEmpty();

    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
      var sheet = workbook.getSheetAt(0);
      assertThat(sheet.getSheetName()).isEqualTo("Orders");

      var headerRow = sheet.getRow(0);
      assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("Fund");
      assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("ISIN");
      assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("Type");
      assertThat(headerRow.getCell(3).getStringCellValue()).isEqualTo("Instrument");
      assertThat(headerRow.getCell(4).getStringCellValue()).isEqualTo("Venue");
      assertThat(headerRow.getCell(5).getStringCellValue()).isEqualTo("Amount");
      assertThat(headerRow.getCell(6).getStringCellValue()).isEqualTo("Quantity");
      assertThat(headerRow.getCell(7).getStringCellValue()).isEqualTo("Settlement Date");

      var dataRow1 = sheet.getRow(1);
      assertThat(dataRow1.getCell(0).getStringCellValue()).isEqualTo("TUV100");
      assertThat(dataRow1.getCell(1).getStringCellValue()).isEqualTo("IE00A");
      assertThat(dataRow1.getCell(2).getStringCellValue()).isEqualTo("BUY");
      assertThat(dataRow1.getCell(5).getStringCellValue()).isEqualTo("100000");
      assertThat((long) dataRow1.getCell(6).getNumericCellValue()).isEqualTo(500L);

      assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(4);
    }
  }

  @Test
  void generateOrdersExport_withEmptyOrders_createsHeaderOnly() throws Exception {
    byte[] xlsx = service.generateOrdersExport(List.of());

    assertThat(xlsx).isNotEmpty();

    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
      var sheet = workbook.getSheetAt(0);
      assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1);
    }
  }

  @Test
  void generateSebFundExport_filtersOnlyFundOrdersAndFormatsCorrectly() throws Exception {
    var batch = buildBatch();

    var fundOrder =
        buildOrder(batch, TKF100, "LU00FUND", BUY, FUND, SEB, new BigDecimal("75000"), null);
    var etfOrder =
        buildOrder(batch, TKF100, "IE00ETF", BUY, ETF, SEB, new BigDecimal("100000"), 500L);

    var labelsByIsin = Map.of("LU00FUND", "SEB Fund X");

    byte[] xlsx = service.generateSebFundExport(List.of(fundOrder, etfOrder), labelsByIsin);

    assertThat(xlsx).isNotEmpty();

    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
      var sheet = workbook.getSheetAt(0);
      assertThat(sheet.getSheetName()).isEqualTo("Indeksfondid SEB");

      assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("ORDER");
      assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Fund units");

      var headerRow = sheet.getRow(2);
      assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("Portfelli tähis");
      assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("Issuer Of Order");
      assertThat(headerRow.getCell(9).getStringCellValue()).isEqualTo("Transaction Type");
      assertThat(headerRow.getCell(12).getStringCellValue()).isEqualTo("Security Name");
      assertThat(headerRow.getCell(13).getStringCellValue()).isEqualTo("ISIN");
      assertThat(headerRow.getCell(16).getStringCellValue()).isEqualTo("Transaction Sum");

      var dataRow = sheet.getRow(3);
      assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("Tuleva Täiendav Kogumisfond");
      assertThat(dataRow.getCell(3).getStringCellValue()).isEqualTo("VP68168");
      assertThat(dataRow.getCell(4).getStringCellValue()).isEqualTo("EE861010220306591229");
      assertThat(dataRow.getCell(5).getStringCellValue()).isEqualTo("EUR");
      assertThat(dataRow.getCell(9).getStringCellValue()).isEqualTo("SUBS");
      assertThat(dataRow.getCell(12).getStringCellValue()).isEqualTo("SEB Fund X");
      assertThat(dataRow.getCell(13).getStringCellValue()).isEqualTo("LU00FUND");
      assertThat(dataRow.getCell(16).getNumericCellValue()).isEqualTo(75000.0);

      assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(4);
    }
  }

  @Test
  void generateSebFundExport_usesSellTransactionType() throws Exception {
    var batch = buildBatch();
    var sellOrder =
        buildOrder(batch, TUV100, "LU00FUND", SELL, FUND, SEB, new BigDecimal("30000"), null);

    byte[] xlsx =
        service.generateSebFundExport(List.of(sellOrder), Map.of("LU00FUND", "SEB Fund X"));

    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
      var dataRow = workbook.getSheetAt(0).getRow(3);
      assertThat(dataRow.getCell(9).getStringCellValue()).isEqualTo("REDP");
    }
  }

  @Test
  void generateSebEtfExport_filtersSebEtfOrdersAndFormatsCorrectly() throws Exception {
    var batch = buildBatch();

    var sebEtfOrder =
        buildOrder(batch, TKF100, "IE00ETF", BUY, ETF, SEB, new BigDecimal("100000"), 500L);
    var ftEtfOrder =
        buildOrder(batch, TKF100, "IE00FT", BUY, ETF, FT, new BigDecimal("200000"), 1000L);
    var fundOrder =
        buildOrder(batch, TKF100, "LU00FUND", BUY, FUND, SEB, new BigDecimal("75000"), null);

    var ricByIsin = Map.of("IE00ETF", "ESGM.DE");

    byte[] xlsx =
        service.generateSebEtfExport(List.of(sebEtfOrder, ftEtfOrder, fundOrder), ricByIsin);

    assertThat(xlsx).isNotEmpty();

    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
      var sheet = workbook.getSheetAt(0);
      assertThat(sheet.getSheetName()).isEqualTo("SEB ETF");

      var titleRow = sheet.getRow(0);
      assertThat(titleRow.getCell(0).getStringCellValue()).isEqualTo("SEB");

      var headerRow = sheet.getRow(1);
      assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("Client Name");
      assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("Account external no.");
      assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("Instruction ISIN");
      assertThat(headerRow.getCell(3).getStringCellValue()).isEqualTo("RIC");
      assertThat(headerRow.getCell(4).getStringCellValue()).isEqualTo("Instruction type");
      assertThat(headerRow.getCell(5).getStringCellValue()).isEqualTo("Net amount");
      assertThat(headerRow.getCell(6).getStringCellValue()).isEqualTo("Quantity");
      assertThat(headerRow.getCell(7).getStringCellValue()).isEqualTo("Type");

      var dataRow = sheet.getRow(2);
      assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("Tuleva Täiendav Kogumisfond");
      assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("VP68168");
      assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("IE00ETF");
      assertThat(dataRow.getCell(3).getStringCellValue()).isEqualTo("ESGM.DE");
      assertThat(dataRow.getCell(4).getStringCellValue()).isEqualTo("MOC");
      assertThat((long) dataRow.getCell(6).getNumericCellValue()).isEqualTo(500L);
      assertThat(dataRow.getCell(7).getStringCellValue()).isEqualTo("BUY");

      assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);
    }
  }

  @Test
  void generateFtEtfExport_aggregatesAcrossFundsAndFormatsCorrectly() throws Exception {
    var batch = buildBatch();

    var tkfOrder =
        buildOrder(batch, TKF100, "IE00FT", BUY, ETF, FT, new BigDecimal("100000"), 400L);
    var tukOrder = buildOrder(batch, TUK75, "IE00FT", BUY, ETF, FT, new BigDecimal("200000"), 800L);
    var tuvOrder =
        buildOrder(batch, TUV100, "IE00FT", BUY, ETF, FT, new BigDecimal("150000"), 600L);
    var sebOrder =
        buildOrder(batch, TKF100, "IE00SEB", BUY, ETF, SEB, new BigDecimal("50000"), 200L);

    var labelsByIsin = Map.of("IE00FT", "iShares ESG MSCI");
    var bbgByIsin = Map.of("IE00FT", "ESGM GY");

    byte[] xlsx =
        service.generateFtEtfExport(
            List.of(tkfOrder, tukOrder, tuvOrder, sebOrder), labelsByIsin, bbgByIsin);

    assertThat(xlsx).isNotEmpty();

    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
      var sheet = workbook.getSheetAt(0);
      assertThat(sheet.getSheetName()).isEqualTo("FT ETF");

      var titleRow = sheet.getRow(0);
      assertThat(titleRow.getCell(0).getStringCellValue()).isEqualTo("FT");

      var headerRow = sheet.getRow(1);
      assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("ETF Name");
      assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("BBG");
      assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("ISIN");
      assertThat(headerRow.getCell(3).getStringCellValue()).isEqualTo("Type");
      assertThat(headerRow.getCell(4).getStringCellValue()).isEqualTo("QTY");
      assertThat(headerRow.getCell(5).getStringCellValue())
          .isEqualTo("Approximate total size in EUR (for control purposes)");
      assertThat(headerRow.getCell(6).getStringCellValue())
          .isEqualTo("TULEVA ADDITIONAL INVESTMENT FUND");
      assertThat(headerRow.getCell(7).getStringCellValue())
          .isEqualTo("TULEVA MAAILMA AKTSIATE PENSIONIFOND");
      assertThat(headerRow.getCell(8).getStringCellValue())
          .isEqualTo("TULEVA III SAMBA PENSIONIFOND");

      var dataRow = sheet.getRow(2);
      assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("iShares ESG MSCI");
      assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("ESGM GY");
      assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("IE00FT");
      assertThat(dataRow.getCell(3).getStringCellValue()).isEqualTo("BUY");
      assertThat((long) dataRow.getCell(4).getNumericCellValue()).isEqualTo(1800L);
      assertThat(dataRow.getCell(5).getNumericCellValue()).isEqualTo(450000.0);
      assertThat((long) dataRow.getCell(6).getNumericCellValue()).isEqualTo(400L);
      assertThat((long) dataRow.getCell(7).getNumericCellValue()).isEqualTo(800L);
      assertThat((long) dataRow.getCell(8).getNumericCellValue()).isEqualTo(600L);

      var totalRow = sheet.getRow(3);
      assertThat(totalRow.getCell(0).getStringCellValue())
          .isEqualTo("Approximate total order size:");
      assertThat(totalRow.getCell(5).getNumericCellValue()).isEqualTo(450000.0);

      assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(4);
    }
  }

  @Test
  void generateFtEtfExport_withEmptyOrders_returnsEmptySheet() throws Exception {
    byte[] xlsx = service.generateFtEtfExport(List.of(), Map.of(), Map.of());

    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
      var sheet = workbook.getSheetAt(0);
      assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);
    }
  }

  private TransactionBatch buildBatch() {
    return TransactionBatch.builder()
        .id(1L)
        .status(BatchStatus.SENT)
        .createdBy("system")
        .createdAt(now)
        .build();
  }

  private TransactionOrder buildOrder(
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
        .orderQuantity(quantity)
        .orderTimestamp(now)
        .expectedSettlementDate(LocalDate.of(2026, 1, 20))
        .createdAt(now)
        .build();
  }
}
