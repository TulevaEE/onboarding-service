package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import com.rollbar.Rollbar;
import ee.tuleva.onboarding.comparisons.fundvalue.ComparisonFund;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;

@Slf4j
@Service
@RequiredArgsConstructor
public class EPIFundValueRetriever implements FundValueRetriever {

    private final Environment environment;
    private final RestTemplate restTemplate;

    @Value("${logging.rollbar.accessToken:#{null}}")
    private String accessToken;

    private static final String EPI_URL = "http://www.pensionikeskus.ee/en/statistics/ii-pillar/epi-charts/";

    @Override
    public ComparisonFund getRetrievalFund() {
        return ComparisonFund.EPI;
    }

    @Override
    public List<FundValue> retrieveValuesForRange(Instant startDate, Instant endDate) {
        String url = getDownloadUrlForRange(startDate, endDate);
        return restTemplate.execute(url, HttpMethod.GET, getRequestCallback(), getResponseExtractor());
    }

    private String getDownloadUrlForRange(Instant startTime, Instant endTime) {
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy");
        String startDate = format.format(Date.from(startTime));
        String endDate = format.format(Date.from(endTime));

        return UriComponentsBuilder
                .fromHttpUrl(EPI_URL)
                .queryParam("date_from", startDate)
                .queryParam("date_to", endDate)
                .queryParam("download", "xls")
                .build()
                .toUriString();
    }

    private ResponseExtractor<List<FundValue>> getResponseExtractor() {
        return response -> {
            if (response.getStatusCode() == HttpStatus.OK) {
                InputStream body = response.getBody();
                BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_16));
                Stream<String> lines = reader.lines().skip(1); // skip tsv header
                return parseLines(lines);
            } else {
                warn("Calling Pensionikeskus for EPI values returned response code: " + response.getStatusCode());
                return emptyList();
            }
        };
    }

    private List<FundValue> parseLines(Stream<String> lines) {
        return lines
                .map(this::parseLine)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<FundValue> parseLine(String line) {
        String[] parts = line.split("\t");
        if (parts.length != 3) {
            warn("EPI response line did not have 3 tab-separated parts, so parsing failed");
            return Optional.empty();
        }

        String fund = parts[1];
        if (!"EPI".equals(fund)) {
            return Optional.empty();
        }

        Optional<Instant> time = parseTime(parts[0]);
        Optional<BigDecimal> value = parseAmount(parts[2]);

        if (!time.isPresent() || !value.isPresent()) {
            return Optional.empty();
        }

        return Optional.of(FundValue.builder()
                .comparisonFund(ComparisonFund.EPI)
                .time(time.get())
                .value(value.get())
                .build());
    }

    private Optional<Instant> parseTime(String time) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        try {
            return Optional.of(format.parse(time).toInstant());
        } catch (ParseException e) {
            warn("Failed to parse time out of a line of epi fund values response");
            return Optional.empty();
        }
    }

    private Optional<BigDecimal> parseAmount(String amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.0#", symbols);
        decimalFormat.setParseBigDecimal(true);
        try {
            return Optional.of((BigDecimal) decimalFormat.parse(amount));
        } catch (ParseException e) {
            warn("Failed to parse value out of a line of epi fund values response");
            return Optional.empty();
        }
    }

    private RequestCallback getRequestCallback() {
        return request -> request
                .getHeaders()
                .setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN));
    }

    private void warn(String message) {
        log.warn(message);
        new Rollbar(accessToken, environment.getActiveProfiles()[0])
            .platform(System.getProperty("java.version"))
            .warning(message);
    }
}
