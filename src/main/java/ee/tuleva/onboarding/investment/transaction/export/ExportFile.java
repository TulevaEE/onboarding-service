package ee.tuleva.onboarding.investment.transaction.export;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

public enum ExportFile {
  GENERIC_ORDERS("xlsxExport", Constants.XLSX_MIME_TYPE, "xlsx"),
  SEB_FUND(
      "sebFundXlsx",
      Constants.CSV_MIME_TYPE,
      "csv",
      "SEB indeksfondid",
      (fund, timestamp) ->
          "SEB_%s_indeksfondid_%s.csv"
              .formatted(fund.getCode(), Constants.TIMESTAMP.format(timestamp))),
  SEB_ETF(
      "sebEtfXlsx",
      Constants.XLSX_MIME_TYPE,
      "xlsx",
      "SEB ETF",
      (fund, timestamp) ->
          "SEB_%s_ETF_tehingud_%s.xlsx"
              .formatted(fund.getCode(), Constants.TIMESTAMP.format(timestamp))),
  FT_ETF(
      "ftEtfXlsx",
      Constants.XLSX_MIME_TYPE,
      "xlsx",
      "FT ETF",
      (fund, timestamp) ->
          "FT_%s_ETF_orders_%s.xlsx"
              .formatted(fund.getCode(), Constants.TIMESTAMP.format(timestamp))),
  UUID_WORKBOOK(
      "uuidWorkbookXlsx",
      Constants.XLSX_MIME_TYPE,
      "xlsx",
      "UUID workbook",
      (fund, timestamp) ->
          "Tehingud_UUID_%s.xlsx".formatted(Constants.UUID_WORKBOOK_TIMESTAMP.format(timestamp)));

  private static final class Constants {
    static final String XLSX_MIME_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String CSV_MIME_TYPE = "text/csv";

    static final DateTimeFormatter TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH_mm_ss").withZone(ZoneOffset.UTC);
    static final DateTimeFormatter UUID_WORKBOOK_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").withZone(ZoneOffset.UTC);
  }

  private final String metadataKey;
  private final String mimeType;
  private final String extension;
  private final @Nullable String driveLabel;
  private final @Nullable BiFunction<TulevaFund, Instant, String> fileNameGenerator;

  ExportFile(String metadataKey, String mimeType, String extension) {
    this(metadataKey, mimeType, extension, null, null);
  }

  ExportFile(
      String metadataKey,
      String mimeType,
      String extension,
      @Nullable String driveLabel,
      @Nullable BiFunction<TulevaFund, Instant, String> fileNameGenerator) {
    this.metadataKey = metadataKey;
    this.mimeType = mimeType;
    this.extension = extension;
    this.driveLabel = driveLabel;
    this.fileNameGenerator = fileNameGenerator;
  }

  public static Optional<ExportFile> byMetadataKey(String metadataKey) {
    return Arrays.stream(values()).filter(file -> file.metadataKey.equals(metadataKey)).findFirst();
  }

  public static List<ExportFile> brokerFiles() {
    return Arrays.stream(values()).filter(file -> file.fileNameGenerator != null).toList();
  }

  public String metadataKey() {
    return metadataKey;
  }

  public String mimeType() {
    return mimeType;
  }

  public String driveLabel() {
    if (driveLabel == null) {
      throw new IllegalStateException("Export file has no drive label: exportFile=" + name());
    }
    return driveLabel;
  }

  public String fileName(TulevaFund fund, Instant timestamp) {
    if (fileNameGenerator == null) {
      throw new IllegalStateException("Export file has no broker file name: exportFile=" + name());
    }
    return fileNameGenerator.apply(fund, timestamp);
  }

  public String downloadFileName(Long batchId) {
    return "batch-%d-%s.%s".formatted(batchId, metadataKey, extension);
  }
}
