package ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock;

import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.ComparisonIndexRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.ftp.FtpClient;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!dev")
public class GlobalStockIndexRetriever implements ComparisonIndexRetriever {
  public static final String KEY = "GLOBAL_STOCK_INDEX";
  public static final String PROVIDER = "MORNINGSTAR";
  private static final String PATH = "/Daily/DMRI/XI_MSTAR/";
  private static final String SECURITY_ID = "F00000VN9N";

  private final FtpClient morningstarFtpClient;

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    Map<String, MonthRecord> monthRecordMap = new HashMap<>();

    try {
      log.debug("Opening connection to ftp server");

      morningstarFtpClient.open();

      log.debug("Opened connection");
      log.debug("Retrieving list of files in FTP path");

      List<String> fileNames = morningstarFtpClient.listFiles(PATH);

      log.debug("Retrieved list of files: {}", fileNames);

      for (LocalDate date = startDate;
          date.isBefore(endDate) || date.isEqual(endDate);
          date = date.plusDays(1)) {
        String dateString = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName =
            fileNames.stream()
                .filter(string -> string.endsWith(dateString + ".zip"))
                .findAny()
                .orElse(null);

        if (fileName == null) {
          continue;
        }

        try {
          log.debug("Downloading " + PATH + fileName);
          InputStream fileStream = morningstarFtpClient.downloadFileStream(PATH + fileName);
          Optional<MonthRecord> optionalRecord = findInZip(fileStream, SECURITY_ID);
          optionalRecord.ifPresent(monthRecord -> updateMonthRecord(monthRecordMap, monthRecord));
          fileStream.close();
          log.debug("Downloaded " + PATH + fileName);

          log.debug("Waiting for pending commands");
          morningstarFtpClient.completePendingCommand();
          log.debug("Finished all pending commands");
        } catch (RuntimeException e) {
          log.error("Unable to parse file: " + fileName, e);
        }
      }
    } catch (IOException e) {
      log.error("Unable to retrieve values for range " + startDate + ", " + endDate, e);
    } finally {
      try {
        morningstarFtpClient.close();
      } catch (IOException e) {
        log.error("Unable to close FTP connection", e);
      }
    }
    return extractValuesFromRecords(monthRecordMap);
  }

  private List<FundValue> extractValuesFromRecords(Map<String, MonthRecord> monthRecords) {
    log.debug("Extracting values from record dictionary");
    List<FundValue> fundValues = new ArrayList<>();
    var now = Instant.now();

    for (MonthRecord record : monthRecords.values()) {
      List<String> recordValues = record.getValues();
      for (int day = 0; day < recordValues.size(); day++) {
        String dayValue = recordValues.get(day);

        if (dayValue != null && !dayValue.isEmpty()) {
          DateTimeFormatter formatter =
              new DateTimeFormatterBuilder()
                  .appendPattern("yyyyMM")
                  .parseDefaulting(ChronoField.DAY_OF_MONTH, day + 1)
                  .toFormatter();

          LocalDate date = LocalDate.parse(record.monthId, formatter);
          fundValues.add(new FundValue(KEY, date, new BigDecimal(dayValue), PROVIDER, now));
        }
      }
    }
    log.debug("Extracted fund values: {}", fundValues);
    return fundValues;
  }

  private void updateMonthRecord(Map<String, MonthRecord> monthRecords, MonthRecord record) {
    log.debug("Update daily record");
    MonthRecord oldRecord = monthRecords.get(record.monthId);
    if (oldRecord != null) {
      oldRecord.update(record);
    } else {
      monthRecords.put(record.monthId, record);
    }
  }

  private Optional<MonthRecord> findInZip(InputStream stream, String securityId)
      throws IOException {
    log.debug("Opening zip stream");
    try (ZipInputStream zipStream = new ZipInputStream(stream)) {
      // Just get one file and we are done.
      log.debug("Get first zip entry");
      ZipEntry entry = zipStream.getNextEntry();

      if (entry != null) {
        return findInCSV(zipStream, securityId);
      } else {
        return Optional.empty();
      }
    }
  }

  private Optional<MonthRecord> findInCSV(InputStream stream, String securityId)
      throws IOException {
    log.debug("Opening file entry");
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
      log.debug("Opened file entry");
      return reader
          .lines()
          .map(this::parseLine)
          .filter((MonthRecord record) -> record.securityId.equals(securityId))
          .findFirst();
    }
  }

  private MonthRecord parseLine(String line) {
    log.trace("Parsing line: " + line);
    String[] parts = line.split(",", -1);
    if (parts.length > 2)
      return new MonthRecord(parts[0], parts[1], Arrays.asList(parts).subList(2, parts.length));
    else return MonthRecord.emptyRecord();
  }

  private static class MonthRecord {
    @Getter private String securityId;
    @Getter private String monthId;
    @Getter private List<String> values;

    MonthRecord(String securityId, String monthId, List<String> values) {
      this.securityId = securityId;
      this.monthId = monthId;
      this.values = new ArrayList<>(values);
    }

    public static MonthRecord emptyRecord() {
      return new MonthRecord("", "", new ArrayList<>());
    }

    public void update(MonthRecord other) {
      int otherValuesSize = other.values.size();
      int valuesSize = values.size();

      for (int i = 0; i < otherValuesSize; i++) {
        if (!other.values.get(i).isEmpty() && i < valuesSize) {
          values.set(i, other.values.get(i));
        }
      }
    }
  }
}
