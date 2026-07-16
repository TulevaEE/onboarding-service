package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.fund.TulevaFund.*;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static java.math.BigDecimal.ZERO;
import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class TransactionExportService {

  private static final String[] GENERIC_HEADERS = {
    "Fund", "ISIN", "Type", "Instrument", "Venue", "Amount", "Quantity", "Settlement Date"
  };

  private static final String[] SEB_FUND_HEADERS = {
    "Original reference",
    "Client name",
    "Securities Acc ",
    "Cash Acc",
    "Currency",
    "Transaction Type",
    "Trade Date",
    "Settlement Date",
    "Security Name",
    "ISIN",
    "Quantity",
    "Price",
    "Transaction Sum",
    "Transaction fee",
    "Total Sum"
  };

  private static final String CSV_DELIMITER = ";";

  private static final String[] SEB_ETF_HEADERS = {
    "Client Name",
    "Account external no.",
    "Instruction ISIN",
    "RIC",
    "Instruction type",
    "Net amount",
    "Quantity",
    "Type"
  };

  private static final String[] FT_ETF_HEADERS = {
    "ETF Name",
    "BBG",
    "ISIN",
    "Type",
    "QTY",
    "Approximate total size in EUR (for control purposes)",
    "TULEVA ADDITIONAL INVESTMENT FUND",
    "TULEVA MAAILMA AKTSIATE PENSIONIFOND",
    "TULEVA III SAMBA PENSIONIFOND"
  };

  public byte[] generateOrdersExport(List<TransactionOrder> orders) {
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Orders");
      createRow(sheet, 0, GENERIC_HEADERS);

      int rowIndex = 1;
      for (var order : orders) {
        createGenericOrderRow(sheet, rowIndex++, order);
      }

      autoSizeColumns(sheet, GENERIC_HEADERS.length);
      return toByteArray(workbook);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to generate XLSX export", e);
    }
  }

  public byte[] generateSebFundExport(
      List<TransactionOrder> orders, Map<String, String> labelsByIsin) {
    List<TransactionOrder> fundOrders =
        orders.stream().filter(order -> order.getInstrumentType() == FUND).toList();

    List<String> lines = new ArrayList<>();
    lines.add(sebFundPreambleRow("ORDER"));
    lines.add(sebFundPreambleRow("Fund units"));
    lines.add(String.join(CSV_DELIMITER, SEB_FUND_HEADERS));
    for (var order : fundOrders) {
      lines.add(sebFundDataRow(order, labelsByIsin));
    }

    return String.join("\n", lines).getBytes(UTF_8);
  }

  private String sebFundPreambleRow(String label) {
    String[] cells = new String[SEB_FUND_HEADERS.length];
    Arrays.fill(cells, "");
    cells[1] = label;
    return String.join(CSV_DELIMITER, cells);
  }

  private String sebFundDataRow(TransactionOrder order, Map<String, String> labelsByIsin) {
    boolean isSell = order.getTransactionType() != BUY;
    String[] cells = new String[SEB_FUND_HEADERS.length];
    Arrays.fill(cells, "");
    cells[0] = order.getOrderUuid().toString();
    cells[1] = order.getFund().getDisplayName();
    cells[2] = order.getFund().getSecuritiesAccount();
    cells[3] = order.getFund().getCashAccount();
    cells[4] = "EUR";
    cells[5] = isSell ? "REDM" : "SUBS";
    cells[8] = labelsByIsin.getOrDefault(order.getInstrumentIsin(), "");
    cells[9] = order.getInstrumentIsin();
    if (isSell) {
      if (order.getOrderQuantity() != null) {
        cells[10] = order.getOrderQuantity().toPlainString();
      }
    } else {
      if (order.getOrderAmount() != null) {
        cells[12] = order.getOrderAmount().toPlainString();
      }
    }
    return String.join(CSV_DELIMITER, cells);
  }

  public byte[] generateSebEtfExport(List<TransactionOrder> orders, Map<String, String> ricByIsin) {
    List<TransactionOrder> sebEtfOrders =
        orders.stream()
            .filter(order -> order.getOrderVenue() == SEB && order.getInstrumentType() == ETF)
            .toList();

    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("SEB ETF");

      createRow(sheet, 0, SEB_ETF_HEADERS);

      int rowIndex = 1;
      for (var order : sebEtfOrders) {
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(order.getFund().getDisplayName());
        row.createCell(1).setCellValue(order.getFund().getSecuritiesAccount());
        row.createCell(2).setCellValue(order.getInstrumentIsin());
        row.createCell(3).setCellValue(ricByIsin.getOrDefault(order.getInstrumentIsin(), ""));
        row.createCell(4).setCellValue("MOC");
        if (order.getOrderQuantity() != null) {
          row.createCell(6).setCellValue(order.getOrderQuantity().doubleValue());
        }
        row.createCell(7).setCellValue(titleCase(order.getTransactionType()));
      }

      autoSizeColumns(sheet, SEB_ETF_HEADERS.length);
      return toByteArray(workbook);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to generate SEB ETF export", e);
    }
  }

  public byte[] generateFtEtfExport(
      List<TransactionOrder> orders,
      Map<String, String> labelsByIsin,
      Map<String, String> bbgByIsin) {
    List<TransactionOrder> ftOrders =
        orders.stream().filter(order -> order.getOrderVenue() == OrderVenue.FT).toList();

    Map<String, AggregatedFtOrder> aggregated = aggregateFtOrders(ftOrders);

    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("FT ETF");

      createRow(sheet, 0, "FT");
      createRow(sheet, 1, FT_ETF_HEADERS);

      int rowIndex = 2;
      BigDecimal grandTotal = ZERO;

      for (var entry : aggregated.entrySet()) {
        var aggregation = entry.getValue();
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(labelsByIsin.getOrDefault(aggregation.isin, ""));
        row.createCell(1).setCellValue(bbgByIsin.getOrDefault(aggregation.isin, ""));
        row.createCell(2).setCellValue(aggregation.isin);
        row.createCell(3).setCellValue(aggregation.transactionType.name());
        row.createCell(4).setCellValue(aggregation.totalQuantity.doubleValue());
        row.createCell(5).setCellValue(aggregation.totalAmount.doubleValue());
        row.createCell(6)
            .setCellValue(aggregation.quantityByFund.getOrDefault(TKF100, ZERO).doubleValue());
        row.createCell(7)
            .setCellValue(aggregation.quantityByFund.getOrDefault(TUK75, ZERO).doubleValue());
        row.createCell(8)
            .setCellValue(aggregation.quantityByFund.getOrDefault(TUV100, ZERO).doubleValue());
        grandTotal = grandTotal.add(aggregation.totalAmount);
      }

      Row totalRow = sheet.createRow(rowIndex);
      totalRow.createCell(0).setCellValue("Approximate total order size:");
      totalRow.createCell(5).setCellValue(grandTotal.doubleValue());

      autoSizeColumns(sheet, FT_ETF_HEADERS.length);
      return toByteArray(workbook);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to generate FT ETF export", e);
    }
  }

  private Map<String, AggregatedFtOrder> aggregateFtOrders(List<TransactionOrder> ftOrders) {
    Map<String, AggregatedFtOrder> aggregated = new LinkedHashMap<>();

    for (var order : ftOrders) {
      String key = order.getInstrumentIsin() + ":" + order.getTransactionType().name();
      aggregated
          .computeIfAbsent(
              key,
              k -> new AggregatedFtOrder(order.getInstrumentIsin(), order.getTransactionType()))
          .add(order);
    }

    return aggregated;
  }

  private void createGenericOrderRow(Sheet sheet, int rowIndex, TransactionOrder order) {
    Row row = sheet.createRow(rowIndex);
    row.createCell(0).setCellValue(order.getFund().name());
    row.createCell(1).setCellValue(order.getInstrumentIsin());
    row.createCell(2).setCellValue(order.getTransactionType().name());
    row.createCell(3).setCellValue(order.getInstrumentType().name());
    row.createCell(4).setCellValue(order.getOrderVenue().name());

    if (order.getOrderAmount() != null) {
      row.createCell(5).setCellValue(order.getOrderAmount().toPlainString());
    }

    if (order.getOrderQuantity() != null) {
      row.createCell(6).setCellValue(order.getOrderQuantity().toPlainString());
    }

    if (order.getExpectedSettlementDate() != null) {
      row.createCell(7).setCellValue(order.getExpectedSettlementDate().toString());
    }
  }

  private String titleCase(TransactionType transactionType) {
    return transactionType == BUY ? "Buy" : "Sell";
  }

  private void createRow(Sheet sheet, int rowIndex, String... values) {
    Row row = sheet.createRow(rowIndex);
    for (int i = 0; i < values.length; i++) {
      row.createCell(i).setCellValue(values[i]);
    }
  }

  private void autoSizeColumns(Sheet sheet, int columnCount) {
    for (int i = 0; i < columnCount; i++) {
      sheet.autoSizeColumn(i);
    }
  }

  private byte[] toByteArray(Workbook workbook) throws IOException {
    try (var outputStream = new ByteArrayOutputStream()) {
      workbook.write(outputStream);
      return outputStream.toByteArray();
    }
  }

  private static class AggregatedFtOrder {
    final String isin;
    final TransactionType transactionType;
    BigDecimal totalQuantity = ZERO;
    BigDecimal totalAmount = ZERO;
    final Map<TulevaFund, BigDecimal> quantityByFund = new LinkedHashMap<>();

    AggregatedFtOrder(String isin, TransactionType transactionType) {
      this.isin = isin;
      this.transactionType = transactionType;
    }

    void add(TransactionOrder order) {
      if (order.getOrderQuantity() != null) {
        totalQuantity = totalQuantity.add(order.getOrderQuantity());
        quantityByFund.merge(order.getFund(), order.getOrderQuantity(), BigDecimal::add);
      }
      if (order.getOrderAmount() != null) {
        totalAmount = totalAmount.add(order.getOrderAmount());
      }
    }
  }
}
