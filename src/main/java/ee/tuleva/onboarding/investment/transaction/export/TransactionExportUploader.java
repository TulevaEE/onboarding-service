package ee.tuleva.onboarding.investment.transaction.export;

import static java.util.stream.Collectors.toUnmodifiableMap;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class TransactionExportUploader {

  private final GoogleDriveClient driveClient;

  public Map<String, String> uploadExports(
      String rootFolderId, LocalDate tradeDate, Instant sentAt, Map<ExportFile, byte[]> files) {
    if (files.isEmpty()) {
      return Map.of();
    }
    var yearFolder =
        driveClient.getOrCreateFolder(rootFolderId, String.valueOf(tradeDate.getYear()));
    var monthFolder =
        driveClient.getOrCreateFolder(yearFolder, "%02d".formatted(tradeDate.getMonthValue()));
    return files.entrySet().stream()
        .collect(
            toUnmodifiableMap(
                file -> file.getKey().metadataKey(),
                file ->
                    driveClient.uploadFile(
                        monthFolder, file.getKey().fileName(sentAt), file.getValue())));
  }
}
