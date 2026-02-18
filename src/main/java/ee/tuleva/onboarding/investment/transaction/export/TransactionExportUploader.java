package ee.tuleva.onboarding.investment.transaction.export;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class TransactionExportUploader {

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH_mm_ss").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter MONTH_YEAR =
      DateTimeFormatter.ofPattern("MM.yyyy").withZone(ZoneOffset.UTC);

  private static final Map<String, String> FILE_NAME_PATTERNS =
      Map.of(
          "sebFundXlsx", "SEB_%s_indeksfondid_%s.xlsx",
          "sebEtfXlsx", "SEB_%s_ETF_tehingud_%s.xlsx",
          "ftEtfXlsx", "FT_%s_ETF_orders_%s.xlsx");

  private final GoogleDriveClient driveClient;

  public Map<String, String> uploadExports(
      String rootFolderId, TulevaFund fund, Instant timestamp, Map<String, byte[]> exports) {
    if (exports.isEmpty()) {
      return Map.of();
    }

    var year = timestamp.atZone(ZoneOffset.UTC).getYear();
    var yearFolder = driveClient.getOrCreateFolder(rootFolderId, year + "_tehingud");
    var monthFolder = driveClient.getOrCreateFolder(yearFolder, MONTH_YEAR.format(timestamp));

    var fileTimestamp = TIMESTAMP.format(timestamp);
    Map<String, String> urls = new HashMap<>();

    FILE_NAME_PATTERNS.forEach(
        (exportKey, namePattern) -> {
          var content = exports.get(exportKey);
          if (content != null && content.length > 0) {
            var fileName = namePattern.formatted(fund.getCode(), fileTimestamp);
            var url = driveClient.uploadFile(monthFolder, fileName, content);
            urls.put(exportKey, url);
          }
        });

    return Map.copyOf(urls);
  }
}
