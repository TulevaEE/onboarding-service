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
@RequiredArgsConstructor
public class GlobalStockIndexRetriever implements ComparisonIndexRetriever {
    public static final String KEY = "GLOBAL_STOCK_INDEX";

    @Value("${morningstar.username}")
    String ftpUsername;

    @Value("${morningstar.password}")
    String ftpPassword;

    @Value("${morningstar.host}")
    String ftpHost;

    private static final String PATH = "/Daily/DMRI/XI_MSTAR/";
    private static final String SEC_ID = "F00000VN9N";

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
        ArrayList<FundValue> fundValues = new ArrayList<>();
        FtpClient ftpClient = new FtpClient(ftpHost, ftpUsername, ftpPassword);

        try {
            ftpClient.open();
            Collection<String> fileNames = ftpClient.listFiles(GlobalStockIndexRetriever.PATH);

            for(LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1))
            {
                String dateString = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                String fileName = fileNames.stream()
                    .filter(string -> string.contains(dateString))
                    .findAny()
                    .orElse(null);

                if(fileName != null) {
                    InputStream fileStream = ftpClient.downloadFileStream(GlobalStockIndexRetriever.PATH + fileName);
                    Optional<DailyRecord> optionalRecord = findInZip(fileStream, GlobalStockIndexRetriever.SEC_ID);
                    if(optionalRecord.isPresent()) {
                        DailyRecord record = optionalRecord.get();
                        fundValues.add(new FundValue(
                            GlobalStockIndexRetriever.KEY,
                            date,
                            record.value
                        ));
                    }
                    fileStream.close();
                }
            }
            ftpClient.close();
        } catch(IOException e) {
            log.error(e.getMessage());
        }

        return fundValues;
    }

    private Optional<DailyRecord> findInZip(InputStream stream, String secId) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(stream);

        // Just get one file and we are done.
        ZipEntry entry = zipStream.getNextEntry();

        if(entry != null) {
            return findInCSV(zipStream, secId);
        } else {
            return Optional.empty();
        }
    }

    private Optional<DailyRecord> findInCSV(InputStream stream, String secId) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8));
        Stream<String> lines = reader.lines();
        reader.close();
        return lines.map(this::parseLine)
            .filter((DailyRecord record) -> record.secID.equals(secId))
            .findFirst();
    }

    private DailyRecord parseLine(String line) {
        String[] parts = line.split(",");

        return new DailyRecord(parts[0], new BigDecimal(parts[parts.length - 1]));
    }

    @lombok.Value
    private static class DailyRecord {
        String secID;
        BigDecimal value;
    }
}