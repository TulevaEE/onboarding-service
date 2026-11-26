package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public enum ExportFile {
  GENERIC_ORDERS("xlsxExport", Constants.XLSX_MIME_TYPE, "xlsx"),
  SEB_FUND(
      "sebFundXlsx",
      Constants.CSV_MIME_TYPE,
      "csv",
      "SEB indeksfondid",
      sentAt -> "SEB_FUND_ORDERS_%s.csv".formatted(Constants.GAS_DATE.format(sentAt))),
  SEB_ETF(
      "sebEtfXlsx",
      Constants.XLSX_MIME_TYPE,
      "xlsx",
      "SEB ETF",
      sentAt -> "SEB_ETF_tehingud_%s.xlsx".formatted(Constants.GAS_DATE.format(sentAt))),
  FT_ETF(
      "ftEtfXlsx",
      Constants.XLSX_MIME_TYPE,
      "xlsx",
      "FT ETF",
      sentAt -> "FT_ETF_orders_%s.xlsx".formatted(Constants.GAS_DATE.format(sentAt))),
  UUID_WORKBOOK(
      "uuidWorkbookXlsx",
      Constants.XLSX_MIME_TYPE,
      "xlsx",
      "UUID workbook",
      sentAt -> "Tehingud_UUID_%s.xlsx".formatted(Constants.GAS_DATE_AND_MINUTE.format(sentAt)));

  private static final class Constants {
    static final String XLSX_MIME_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String CSV_MIME_TYPE = "text/csv";

    static final DateTimeFormatter GAS_DATE =
        DateTimeFormatter.ofPattern("ddMMyyyy").withZone(ZoneId.of(TIMEZONE));
    static final DateTimeFormatter GAS_DATE_AND_MINUTE =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").withZone(ZoneId.of(TIMEZONE));
  }

  private final String metadataKey;
  private final String mimeType;
  private final String extension;
  private final @Nullable String driveLabel;
  private final @Nullable Function<Instant, String> fileNameGenerator;

  ExportFile(String metadataKey, String mimeType, String extension) {
    this(metadataKey, mimeType, extension, null, null);
  }

  ExportFile(
      String metadataKey,
      String mimeType,
      String extension,
      @Nullable String driveLabel,
      @Nullable Function<Instant, String> fileNameGenerator) {
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

  public String fileName(Instant sentAt) {
    if (fileNameGenerator == null) {
      throw new IllegalStateException("Export file has no broker file name: exportFile=" + name());
    }
    return fileNameGenerator.apply(sentAt);
  }

  boolean takes(TransactionOrder order) {
    return switch (this) {
      case GENERIC_ORDERS -> true;
      case SEB_FUND -> BrokerOrders.isSebFundOrder(order);
      case SEB_ETF -> BrokerOrders.isSebEtfOrder(order);
      case FT_ETF -> BrokerOrders.isFtOrder(order);
      case UUID_WORKBOOK -> BrokerOrders.isUuidWorkbookOrder(order);
    };
  }

  public String downloadFileName(Long batchId) {
    return "batch-%d-%s.%s".formatted(batchId, metadataKey, extension);
  }
}
