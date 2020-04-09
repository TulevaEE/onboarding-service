package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.*;
import ee.tuleva.onboarding.ftp.FTPClientFactory;
import ee.tuleva.onboarding.ftp.FtpClient;
import lombok.*;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.*;

import java.io.*;
import java.math.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.stream.*;
import java.util.zip.*;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
@Service
public class GlobalStockIndexRetriever implements ComparisonIndexRetriever {
    public static final String KEY = "GLOBAL_STOCK_INDEX";
    private static final String PATH = "/Daily/DMRI/XI_MSTAR/";
    private static final String SECURITY_ID = "F00000VN9N";

    private final FTPClientFactory ftpClientFactory;

    GlobalStockIndexRetriever(@Autowired FTPClientFactory ftpClientFactory) {
        this.ftpClientFactory = ftpClientFactory;
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
        Map<String, DailyRecord> monthRecordMap = new HashMap<>();
        FtpClient ftpClient = ftpClientFactory.createClient();
        try {
            ftpClient.open();
            Collection<String> fileNames = ftpClient.listFiles(GlobalStockIndexRetriever.PATH);
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
                    InputStream fileStream = ftpClient.downloadFileStream(GlobalStockIndexRetriever.PATH + fileName);
                    Optional<DailyRecord> optionalRecord = findInZip(fileStream, GlobalStockIndexRetriever.SECURITY_ID);
                    optionalRecord.ifPresent(dailyRecord -> pushDailyRecord(monthRecordMap, dailyRecord));
                    fileStream.close();
                    ftpClient.completePendingCommand();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } finally {
            try {
                ftpClient.close();
            } catch (IOException ignored) {
            }
        }
        return extractValuesFromRecords(monthRecordMap, startDate, endDate);
    }

    private List<FundValue> extractValuesFromRecords(Map<String, DailyRecord> monthRecords, LocalDate startDate, LocalDate endDate) {
        List<FundValue> fundValues = new ArrayList<>();

        for (LocalDate date = startDate; date.isBefore(endDate) || date.isEqual(endDate); date = date.plusDays(1)) {
            String monthID = date.format(DateTimeFormatter.ofPattern("yyyyMM"));
            int dayOfMonth = date.getDayOfMonth();

            DailyRecord record = monthRecords.get(monthID);

            if (record == null)
                continue;

            String value = record.values.get(dayOfMonth - 1);
            if (!value.isEmpty()) {
                fundValues.add(new FundValue(GlobalStockIndexRetriever.KEY, date, new BigDecimal(value)));
            }
        }
        return fundValues;
    }

    private void pushDailyRecord(Map<String, DailyRecord> recordMap, DailyRecord record) {
        DailyRecord oldRecord = recordMap.get(record.monthID);
        if(oldRecord != null) {
            oldRecord.update(record);
        } else {
            recordMap.put(record.monthID, record);
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
            Stream<String> lines = reader.lines();
            return lines.map(this::parseLine)
                .filter((DailyRecord record) -> record.securityID.equals(securityId))
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
        private String securityID;
        @Getter
        private String monthID;
        @Getter
        private List<String> values;

        DailyRecord(String securityID, String monthID, Collection<String> _values) {
            this.securityID = securityID;
            this.monthID = monthID;
            values = new ArrayList<>(_values);
        }

        public void update(DailyRecord other) {
            if(!other.securityID.equals(securityID)
                || !other.monthID.equals(monthID))
                return;

            for (int i = 0; i < other.values.size(); i++) {
                if (!other.values.get(i).isEmpty()) {
                    values.set(i, other.values.get(i));
                }
            }
        }
    }
}