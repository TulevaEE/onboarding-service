package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.common.*;
import ee.tuleva.onboarding.comparisons.fundvalue.*;
import lombok.*;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${morningstar.username}")
    private String ftpUsername;

    @Value("${morningstar.password}")
    private String ftpPassword;

    @Value("${morningstar.host}")
    private String ftpHost;

    @Value("${morningstar.port}")
    private int ftpPort;

    private static final String PATH = "/Daily/DMRI/XI_MSTAR/";
    private static final String secID = "F00000VN9N";

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
        ArrayList<DailyRecord> records = new ArrayList<>();

        FtpClient ftpClient = new FtpClient(ftpHost, ftpUsername, ftpPassword, ftpPort);
        try {
            ftpClient.open();
            Collection<String> fileNames = ftpClient.listFiles(GlobalStockIndexRetriever.PATH);
            for(LocalDate date = startDate; date.isBefore(endDate) || date.isEqual(endDate); date = date.plusDays(1))
            {
                String dateString = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                String fileName = fileNames.stream()
                    .filter(string -> string.endsWith(dateString + ".zip"))
                    .findAny()
                    .orElse(null);

                if(fileName == null) {
                    continue;
                }

                try{
                    InputStream fileStream = ftpClient.downloadFileStream(GlobalStockIndexRetriever.PATH + fileName);
                    Optional<DailyRecord> optionalRecord = findInZip(fileStream, GlobalStockIndexRetriever.secID);
                    optionalRecord.ifPresent(dailyRecord -> pushDailyRecord(records, dailyRecord));
                    fileStream.close();
                    ftpClient.completePendingCommand();
                } catch(IOException ignored) {

                }
            }
        } catch(IOException e) {
            log.error(e.getMessage());
        } finally {
            try {
                ftpClient.close();
            } catch(IOException ignored) {}
        }
        return extractValuesFromRecords(records, startDate, endDate);
    }

    private List<FundValue> extractValuesFromRecords(List<DailyRecord> records, LocalDate startDate, LocalDate endDate) {
        ArrayList<FundValue> fundValues = new ArrayList<>();

        for(LocalDate date = startDate; date.isBefore(endDate) || date.isEqual(endDate); date = date.plusDays(1)) {
            String monthID = date.format(DateTimeFormatter.ofPattern("yyyyMM"));
            int dayOfMonth = date.getDayOfMonth();

            DailyRecord record = records.stream()
                .filter(r -> r.secID.equals(secID) && r.monthID.equals(monthID))
                .findAny()
                .orElse(null);

            if(record == null)
                continue;

            String value = record.values.get(dayOfMonth - 1);
            if(!value.isEmpty()) {
                fundValues.add(new FundValue(GlobalStockIndexRetriever.KEY, date, new BigDecimal(value)));
            }
        }
        return fundValues;
    }

    private void pushDailyRecord(List<DailyRecord> records, DailyRecord record) {
        int recordIdx = records.indexOf(record);
        if(recordIdx != -1) {
            records.get(recordIdx).update(record);
        } else {
            records.add(record);
        }
    }

    private Optional<DailyRecord> findInZip(InputStream stream, String secId) throws IOException {
        try(ZipInputStream zipStream = new ZipInputStream(stream)) {
            // Just get one file and we are done.
            ZipEntry entry = zipStream.getNextEntry();

            if(entry != null) {
                return findInCSV(zipStream, secId);
            } else {
                return Optional.empty();
            }
        }
    }

    private Optional<DailyRecord> findInCSV(InputStream stream, String secId) throws IOException {
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
            Stream<String> lines = reader.lines();
            return lines.map(this::parseLine)
                .filter((DailyRecord record) -> record.secID.equals(secId))
                .findFirst();
        }
    }

    private DailyRecord parseLine(String line) {
        String[] parts = line.split(",", -1);
        int partLength = parts.length;
        return new DailyRecord(parts[0], parts[1], Arrays.asList(parts).subList(2, partLength));
    }

    private static class DailyRecord {
        @Getter private String secID;
        @Getter private String monthID;
        @Getter private ArrayList<String> values;

        DailyRecord(String _secID, String _monthID, Collection<String> _values) {
            secID = _secID;
            monthID = _monthID;
            values = new ArrayList<>(_values);
        }

        public void update(DailyRecord other) {
            if(!other.equals(this)) return;

            for(int i = 0; i < other.values.size(); i++) {
                if(!other.values.get(i).isEmpty()) {
                    values.set(i, other.values.get(i));
                }
            }
        }

        @Override
        public boolean equals(Object o) {

            // If the object is compared with itself then return true
            if (o == this) {
                return true;
            }

        /* Check if o is an instance of Complex or not
          "null instanceof [type]" also returns false */
            if (!(o instanceof DailyRecord)) {
                return false;
            }

            // typecast o to Complex so that we can compare data members
            DailyRecord d = (DailyRecord) o;

            // Compare the data members and return accordingly
            return d.secID.equals(GlobalStockIndexRetriever.secID)
                && d.monthID.equals(monthID);
        }

    }
}