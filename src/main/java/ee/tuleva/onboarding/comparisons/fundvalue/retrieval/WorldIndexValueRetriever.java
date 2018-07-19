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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorldIndexValueRetriever implements FundValueRetriever {

    private final Environment environment;
    private final RestTemplate restTemplate;

    @Value("${logging.rollbar.accessToken:#{null}}")
    private String accessToken;

    private static final String SOURCE_URL = "https://docs.google.com/spreadsheets/d/125aXusxnf-Mij-4D4W5Qnneb8H4chjebnIMpyrAfRkI/gviz/tq?tqx=out:csv&gid=619370394";

    @Override
    public ComparisonFund getRetrievalFund() {
        return ComparisonFund.WORLD_INDEX;
    }

    @Override
    public List<FundValue> retrieveValuesForRange(Instant startDate, Instant endDate) {
        return restTemplate.execute(SOURCE_URL, HttpMethod.GET, getRequestCallback(), getResponseExtractor(startDate, endDate));
    }

    private ResponseExtractor<List<FundValue>> getResponseExtractor(Instant startDate, Instant endDate) {
        return response -> {
            if (response.getStatusCode() == HttpStatus.OK) {
                InputStream body = response.getBody();
                BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
                Stream<String> lines = reader.lines().skip(3);
                return parseLines(lines, startDate, endDate);
            } else {
                warn("Failed to fetch World Index values");
                return Collections.emptyList();
            }
        };
    }

    private List<FundValue> parseLines(Stream<String> lines, Instant startDate, Instant endDate) {
        return lines
            .map(this::parseLine)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter((FundValue value) -> {
                Instant time = value.getTime();
                return (startDate.isBefore(time) || startDate.equals(time)) && (endDate.isAfter(time) || endDate.equals(time));
            })
            .collect(Collectors.toList());
    }

    private Optional<FundValue> parseLine(String line) {
        log.info("Parsing line: [{}].", line);
        String[] parts = line.split("\",\"");
        if (parts.length < 6) {
            warn("Invalid line: Less than 6 columns.");
            return Optional.empty();
        }
        parts[0] = parts[0].replaceFirst("^\"", "");
        parts[parts.length-1] = parts[parts.length-1].replaceFirst("\"$", "");

        log.debug("Parts({}): {}", parts.length, parts);

        Optional<Instant> time = parseTime(parts[0]);
        Optional<BigDecimal> value = parseAmount(parts[5]);

        if (!time.isPresent() || !value.isPresent()) {
            return Optional.empty();
        }

        return Optional.of(FundValue.builder()
            .comparisonFund(ComparisonFund.WORLD_INDEX)
            .time(time.get())
            .value(value.get())
            .build());
    }

    private Optional<Instant> parseTime(String time) {
        SimpleDateFormat format = new SimpleDateFormat("dd-MMM-yyyy");
        try {
            return Optional.of(format.parse(time).toInstant());
        } catch (ParseException e) {
            warn("Failed to parse time");
            return Optional.empty();
        }
    }

    private Optional<BigDecimal> parseAmount(String amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.0#", symbols);
        decimalFormat.setParseBigDecimal(true);
        try {
            return Optional.of((BigDecimal) decimalFormat.parse(amount));
        } catch (ParseException e) {
            warn("Failed to parse amount");
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
