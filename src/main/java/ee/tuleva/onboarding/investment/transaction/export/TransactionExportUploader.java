package ee.tuleva.onboarding.investment.transaction.export;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class TransactionExportUploader {

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH_mm_ss").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter UUID_WORKBOOK_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter MONTH =
      DateTimeFormatter.ofPattern("MM").withZone(ZoneOffset.UTC);

  private static final Map<String, BiFunction<TulevaFund, Instant, String>> FILE_NAME_GENERATORS =
      Map.of(
          "sebFundXlsx",
              (fund, timestamp) ->
                  "SEB_%s_indeksfondid_%s.csv"
                      .formatted(fund.getCode(), TIMESTAMP.format(timestamp)),
          "sebEtfXlsx",
              (fund, timestamp) ->
                  "SEB_%s_ETF_tehingud_%s.xlsx"
                      .formatted(fund.getCode(), TIMESTAMP.format(timestamp)),
          "ftEtfXlsx",
              (fund, timestamp) ->
                  "FT_%s_ETF_orders_%s.xlsx".formatted(fund.getCode(), TIMESTAMP.format(timestamp)),
          "uuidWorkbookXlsx",
              (fund, timestamp) ->
                  "Tehingud_UUID_%s.xlsx".formatted(UUID_WORKBOOK_TIMESTAMP.format(timestamp)));

  private final GoogleDriveClient driveClient;

  public Map<String, String> uploadExports(
      String rootFolderId, TulevaFund fund, Instant timestamp, Map<String, byte[]> exports) {
    if (exports.isEmpty()) {
      return Map.of();
    }

    var year = timestamp.atZone(ZoneOffset.UTC).getYear();
    var yearFolder = driveClient.getOrCreateFolder(rootFolderId, String.valueOf(year));
    var monthFolder = driveClient.getOrCreateFolder(yearFolder, MONTH.format(timestamp));

    Map<String, String> urls = new HashMap<>();

    FILE_NAME_GENERATORS.forEach(
        (exportKey, fileNameGenerator) -> {
          var content = exports.get(exportKey);
          if (content != null && content.length > 0) {
            var fileName = fileNameGenerator.apply(fund, timestamp);
            var url = driveClient.uploadFile(monthFolder, fileName, content);
            urls.put(exportKey, url);
          }
        });

    return Map.copyOf(urls);
  }
}
