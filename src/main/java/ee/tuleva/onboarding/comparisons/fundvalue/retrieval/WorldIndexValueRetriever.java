package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class WorldIndexValueRetriever implements ComparisonIndexRetriever {
  @ToString.Include public static final String KEY = "MARKET";
  public static final String PROVIDER = "GOOGLE_SHEETS";

  private final RestTemplate restTemplate;

  private static final String SOURCE_URL =
      "https://docs.google.com/spreadsheets/d/125aXusxnf-Mij-4D4W5Qnneb8H4chjebnIMpyrAfRkI/gviz/tq?tqx=out:csv&gid=619370394";

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    return restTemplate.execute(
        SOURCE_URL, HttpMethod.GET, getRequestCallback(), getResponseExtractor(startDate, endDate));
  }

  private ResponseExtractor<List<FundValue>> getResponseExtractor(
      LocalDate startDate, LocalDate endDate) {
    return response -> {
      if (response.getStatusCode() == HttpStatus.OK) {
        InputStream body = response.getBody();
        BufferedReader reader = new BufferedReader(new InputStreamReader(body, UTF_8));
        Stream<String> lines = reader.lines().skip(3);
        return parseLines(lines, startDate, endDate);
      } else {
        log.warn("Failed to fetch World Index values");
        return emptyList();
      }
    };
  }

  private List<FundValue> parseLines(Stream<String> lines, LocalDate startDate, LocalDate endDate) {
    return lines
        .map(this::parseLine)
        .flatMap(Optional::stream)
        .filter(
            (FundValue value) -> {
              LocalDate date = value.date();
              return (startDate.isBefore(date) || startDate.equals(date))
                  && (endDate.isAfter(date) || endDate.equals(date));
            })
        .collect(toList());
  }

  private Optional<FundValue> parseLine(String line) {
    log.debug("Parsing line: [{}].", line);
    String[] parts = line.split("\",\"");
    if (parts.length < 6) {
      log.warn("Invalid line: Less than 6 columns.");
      return Optional.empty();
    }
    parts[0] = parts[0].replaceFirst("^\"", "");
    parts[parts.length - 1] = parts[parts.length - 1].replaceFirst("\"$", "");

    log.debug("Parts({}): {}", parts.length, parts);

    Optional<LocalDate> date = parseDate(parts[0]);
    Optional<BigDecimal> value = parseAmount(parts[5]);

    if (date.isEmpty() || value.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new FundValue(KEY, date.get(), value.get(), PROVIDER, Instant.now()));
  }

  private Optional<LocalDate> parseDate(String date) {
    try {
      return Optional.of(LocalDate.parse(date, DateTimeFormatter.ofPattern("d-MMM-yyyy")));
    } catch (DateTimeParseException ignored) {
      try {
        return Optional.of(LocalDate.parse(date, DateTimeFormatter.ofPattern("d-MMMM-yyyy")));
      } catch (DateTimeParseException e) {
        log.warn("Failed to parse date: {}", e.getMessage());
        return Optional.empty();
      }
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
      log.warn("Failed to parse amount");
      return Optional.empty();
    }
  }

  private RequestCallback getRequestCallback() {
    return request ->
        request
            .getHeaders()
            .setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN));
  }
}
