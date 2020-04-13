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
            morningstarFtpClient.open();
            Collection<String> fileNames = morningstarFtpClient.listFiles(PATH);
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
                    InputStream fileStream = morningstarFtpClient.downloadFileStream(PATH + fileName);
                    Optional<DailyRecord> optionalRecord = findInZip(fileStream, SECURITY_ID);
                    optionalRecord.ifPresent(dailyRecord -> pushDailyRecord(monthRecordMap, dailyRecord));
                    fileStream.close();
                    morningstarFtpClient.completePendingCommand();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
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
        return fundValues;
    }

    private void pushDailyRecord(Map<String, DailyRecord> monthRecords, DailyRecord record) {
        DailyRecord oldRecord = monthRecords.get(record.monthId);
        if (oldRecord != null) {
            oldRecord.update(record);
        } else {
            monthRecords.put(record.monthId, record);
        }
    }

    private Optional<DailyRecord> findInZip(InputStream stream, String securityId) throws IOException {
        try (ZipInputStream zipStream = new ZipInputStream(stream)) {
            // Just get one file and we are done.
            ZipEntry entry = zipStream.getNextEntry();

            if (entry != null) {
                return findInCSV(zipStream, securityId);
            } else {
                return Optional.empty();
            }
        }
    }

    private Optional<DailyRecord> findInCSV(InputStream stream, String securityId) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
            return reader.lines().map(this::parseLine)
                .filter((DailyRecord record) -> record.securityId.equals(securityId))
                .findFirst();
        }
    }

    private DailyRecord parseLine(String line) {
        String[] parts = line.split(",", -1);
        int partLength = parts.length;
        return new DailyRecord(parts[0], parts[1], Arrays.asList(parts).subList(2, partLength));
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