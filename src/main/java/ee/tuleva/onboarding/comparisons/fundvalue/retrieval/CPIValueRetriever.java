package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static java.util.stream.Collectors.toList;

@Slf4j
@Service
public class CPIValueRetriever implements ComparisonIndexRetriever {
    public static final String KEY = "CPI";

    private static String SOURCE_URL = "https://ec.europa.eu/eurostat/estat-navtree-portlet-prod/BulkDownloadListing?sort=1&file=data%2Fprc_hicp_midx.tsv.gz";

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
        List<FundValue> cpiValues = getCPIValues();
        return cpiValues.stream().filter(fundValue -> {
            LocalDate date = fundValue.getDate();
            return (startDate.isBefore(date) || startDate.equals(date)) && (endDate.isAfter(date) || endDate.equals(date));
        }).collect(toList());
    }

    private List<FundValue> getCPIValues() {
        try {
            return downloadCPIValues();
        } catch (IOException e) {
            throw new IllegalStateException("Could not get CPI values", e);
        }
    }

    private List<FundValue> downloadCPIValues() throws IOException {
        URL url = new URL(SOURCE_URL);
        URLConnection conn = url.openConnection();

        GZIPInputStream into = new GZIPInputStream(conn.getInputStream());

        String line;
        BufferedReader in = new BufferedReader(new InputStreamReader(into));

        String headingLine = "unit,coicop,geo";
        String indexEE = "I96,CP00,EE\t";

        List<String[]> lines = new ArrayList<>();
        while ((line = in.readLine()) != null) {
            if (Stream.of(headingLine, indexEE).anyMatch(line::startsWith)) {
                lines.add(line.split("\t"));
            }
        }

        // Create 2d table
        String[][] table = new String[lines.size()][0];
        lines.toArray(table);

        List<FundValue> cpiValues = new ArrayList<>();

        for (int i = 0; i < table[0].length - 1; i++) {
            String month = table[0][1 + i];
            String cpi = table[1][1 + i].replace(" ", "");

            month = month.replace("M", " ");
            month = month + "01";
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy MM d");
            LocalDate date = LocalDate.parse(month, formatter);

            try {
                if (!cpi.startsWith(":")) {
                    BigDecimal cpiValue = new BigDecimal(cpi);
                    cpiValues.add(new FundValue(KEY, date, cpiValue));
                }
            } catch (NumberFormatException e) {
                log.error("Could not convert CPI value to BigDecimal: {}", cpi);
            }
        }

        return cpiValues;
    }
}
