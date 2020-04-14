package ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.ComparisonIndexRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.ftp.FtpClient;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalStockIndexRetriever implements ComparisonIndexRetriever {
    public static final String KEY = "GLOBAL_STOCK_INDEX";
    private static final String PATH = "/Daily/DMRI/XI_MSTAR/";
    private static final String SECURITY_ID = "F00000VN9N";

    private final FtpClient morningstarFtpClient;

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
        Map<String, DailyRecord> monthRecordMap = new HashMap<>();

        try {
            log.debug("Opening connection to ftp server");

            morningstarFtpClient.open();

            log.debug("Opened connection");
            log.debug("Retrieving list of files in FTP path");

            List<String> fileNames = morningstarFtpClient.listFiles(PATH);

            log.debug("Retrieved list of files: {}", fileNames);

            for (LocalDate date = startDate; date.isBefore(endDate) || date.isEqual(endDate); date = date.plusDays(1)) {
                String dateString = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                String fileName = fileNames.stream()
                    .filter(string -> string.endsWith(dateString + ".zip"))
                    .findAny()
                    .orElse(null);

                if (fileName == null) {
                    continue;
                }

                try {
                    log.debug("Downloading " + PATH + fileName);
                    InputStream fileStream = morningstarFtpClient.downloadFileStream(PATH + fileName);
                    Optional<DailyRecord> optionalRecord = findInZip(fileStream, SECURITY_ID);
                    optionalRecord.ifPresent(dailyRecord -> pushDailyRecord(monthRecordMap, dailyRecord));
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
            log.error(e.getMessage(), e);
        } finally {
            try {
                morningstarFtpClient.close();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        return extractValuesFromRecords(monthRecordMap, startDate, endDate);
    }

    private List<FundValue> extractValuesFromRecords(Map<String, DailyRecord> monthRecords, LocalDate startDate, LocalDate endDate) {
        log.debug("Extracting values from record dictionary");
        List<FundValue> fundValues = new ArrayList<>();

        for (LocalDate date = startDate; date.isBefore(endDate) || date.isEqual(endDate); date = date.plusDays(1)) {
            String monthId = date.format(DateTimeFormatter.ofPattern("yyyyMM"));
            int dayOfMonth = date.getDayOfMonth();

            DailyRecord record = monthRecords.get(monthId);

            if (record == null)
                continue;

            String value = record.values.get(dayOfMonth - 1);
            if (!value.isEmpty()) {
                fundValues.add(new FundValue(KEY, date, new BigDecimal(value)));
            }
        }
        log.debug("Extracted fund values: {}", fundValues);
        return fundValues;
    }

    private void pushDailyRecord(Map<String, DailyRecord> monthRecords, DailyRecord record) {
        log.debug("Update daily record");
        DailyRecord oldRecord = monthRecords.get(record.monthId);
        if (oldRecord != null) {
            oldRecord.update(record);
        } else {
            monthRecords.put(record.monthId, record);
        }
    }

    private Optional<DailyRecord> findInZip(InputStream stream, String securityId) throws IOException {
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

    private Optional<DailyRecord> findInCSV(InputStream stream, String securityId) throws IOException {
        log.debug("Opening file entry");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
            log.debug("Opened file entry");
            return reader.lines().map(this::parseLine)
                .filter((DailyRecord record) -> record.securityId.equals(securityId))
                .findFirst();
        }
    }

    private DailyRecord parseLine(String line) {
        try {
            log.trace("Parsing line: " + line);
            String[] parts = line.split(",", -1);
            if(parts.length > 2)
                return new DailyRecord(parts[0], parts[1], Arrays.asList(parts).subList(2, parts.length));
            else
                return DailyRecord.emptyRecord();
        } catch(RuntimeException e) {
            throw new RuntimeException("Unable to parse line: " + line, e);
        }
    }

    private static class DailyRecord {
        @Getter
        private String securityId;
        @Getter
        private String monthId;
        @Getter
        private List<String> values;

        DailyRecord(String securityId, String monthId, List<String> values) {
            this.securityId = securityId;
            this.monthId = monthId;
            this.values = new ArrayList<>(values);
        }
        public static DailyRecord emptyRecord() {
            return new DailyRecord("", "", new ArrayList<>());
        }
        public void update(DailyRecord other) {
            if (!other.securityId.equals(securityId) || !other.monthId.equals(monthId)) {
                return;
            }

            for (int i = 0; i < other.values.size(); i++) {
                if (!other.values.get(i).isEmpty()) {
                    values.set(i, other.values.get(i));
                }
            }
        }
    }
}