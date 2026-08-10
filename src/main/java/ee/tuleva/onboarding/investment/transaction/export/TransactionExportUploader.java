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

  private static final DateTimeFormatter MONTH =
      DateTimeFormatter.ofPattern("MM").withZone(ZoneOffset.UTC);

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

    ExportFile.brokerFiles()
        .forEach(
            exportFile -> {
              var content = exports.get(exportFile.metadataKey());
              if (content != null && content.length > 0) {
                var fileName = exportFile.fileName(fund, timestamp);
                var url = driveClient.uploadFile(monthFolder, fileName, content);
                urls.put(exportFile.metadataKey(), url);
              }
            });

    return Map.copyOf(urls);
  }
}
