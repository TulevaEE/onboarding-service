package ee.tuleva.onboarding.investment.transaction.export;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BrokerFileGenerator {

  private final TransactionExportService exportService;

  public Map<ExportFile, byte[]> generate(
      List<TransactionOrder> orders,
      Map<String, String> labelsByIsin,
      Map<String, String> ricByIsin,
      Map<String, String> bbgByIsin,
      LocalDate tradeDate) {
    Map<ExportFile, byte[]> files = new EnumMap<>(ExportFile.class);
    if (orders.stream().anyMatch(BrokerOrders::isSebFundOrder)) {
      files.put(ExportFile.SEB_FUND, exportService.generateSebFundExport(orders, labelsByIsin));
    }
    if (orders.stream().anyMatch(BrokerOrders::isSebEtfOrder)) {
      files.put(ExportFile.SEB_ETF, exportService.generateSebEtfExport(orders, ricByIsin));
    }
    if (orders.stream().anyMatch(BrokerOrders::isFtOrder)) {
      files.put(
          ExportFile.FT_ETF,
          exportService.generateFtEtfExport(orders, labelsByIsin, bbgByIsin, tradeDate));
    }
    if (orders.stream().anyMatch(BrokerOrders::isUuidWorkbookOrder)) {
      files.put(ExportFile.UUID_WORKBOOK, exportService.generateUuidWorkbook(orders));
    }
    return files;
  }
}
