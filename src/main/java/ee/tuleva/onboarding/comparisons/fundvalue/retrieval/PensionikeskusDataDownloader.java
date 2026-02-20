package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.*;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class PensionikeskusDataDownloader {
  public static final String PROVIDER = "PENSIONIKESKUS";

  private final RestTemplate restTemplate;
  private static final DateTimeFormatter DEFAULT_DATE_FORMATTER = ISO_LOCAL_DATE;
  private static final DecimalFormat DEFAULT_DECIMAL_FORMAT;

  static {
    var symbols = new DecimalFormatSymbols();
    symbols.setDecimalSeparator(',');
    DEFAULT_DECIMAL_FORMAT = new DecimalFormat("#,##0.0#", symbols);
    DEFAULT_DECIMAL_FORMAT.setParseBigDecimal(true);
  }

  public PensionikeskusDataDownloader(RestTemplateBuilder restTemplateBuilder) {
    this.restTemplate = restTemplateBuilder.build();
  }

  @Builder
  public record CsvParserConfig(
      String keyPrefix,
      Integer
          keyColumn, // Optional: if provided, computed key = keyPrefix + "_" + column[keyColumn]
      Integer filterColumn,
      String filterValue,
      int valueColumn) {}

  public List<FundValue> downloadData(
      String baseUrl, LocalDate startDate, LocalDate endDate, CsvParserConfig config) {
    var url = buildUrl(baseUrl, startDate, endDate);
    return restTemplate.execute(url, GET, requestCallback(), responseExtractor(config));
  }

  private String buildUrl(String baseUrl, LocalDate startDate, LocalDate endDate) {
    var dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    return UriComponentsBuilder.fromUriString(baseUrl)
        .queryParam("date_from", startDate.format(dateFormatter))
        .queryParam("date_to", endDate.format(dateFormatter))
        .queryParam("download", "xls")
        .build()
        .toUriString();
  }

  private RequestCallback requestCallback() {
    return request ->
        request
            .getHeaders()
            .setAccept(List.of(MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN));
  }

  private ResponseExtractor<List<FundValue>> responseExtractor(CsvParserConfig config) {
    return response -> {
      if (response.getStatusCode() != OK) {
        return List.of();
      }
      InputStream body = response.getBody();
      try (var reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_16))) {
        Stream<String> lines = reader.lines().skip(1);
        return lines.map(line -> parseLine(line, config)).flatMap(Optional::stream).toList();
      }
    };
  }

  private Optional<FundValue> parseLine(String line, CsvParserConfig config) {
    try {
      String[] columns = line.split("\t");
      if (!hasRequiredColumns(columns, config)) {
        log.error("Insufficient columns in line: {}", line);
        return Optional.empty();
      }
      LocalDate date = parseDate(columns[0], line);
      if (!matchesFilter(columns, config)) {
        return Optional.empty();
      }
      BigDecimal value = parseNumericValue(columns, config, line);
      if (value.compareTo(BigDecimal.ZERO) <= 0) {
        log.error("Non-positive value in line: {}", line);
        return Optional.empty();
      }
      String computedKey = config.keyPrefix();
      if (config.keyColumn() != null) {
        computedKey = config.keyPrefix() + "_" + columns[config.keyColumn()].trim();
      }
      return Optional.of(new FundValue(computedKey, date, value, PROVIDER, Instant.now()));
    } catch (Exception e) {
      log.error("Failed to parse line: {}. Error: {}", line, e.getMessage());
      return Optional.empty();
    }
  }

  private boolean hasRequiredColumns(String[] columns, CsvParserConfig config) {
    int requiredColumns =
        Math.max(config.valueColumn(), config.filterColumn() != null ? config.filterColumn() : 0)
            + 1;
    if (config.keyColumn() != null) {
      requiredColumns = Math.max(requiredColumns, config.keyColumn() + 1);
    }
    return columns.length >= requiredColumns;
  }

  private LocalDate parseDate(String dateStr, String line) {
    try {
      return LocalDate.parse(dateStr.trim(), DEFAULT_DATE_FORMATTER);
    } catch (DateTimeParseException e) {
      log.error("Date parse error for '{}' in line: {}", dateStr, line);
      throw e;
    }
  }

  private boolean matchesFilter(String[] columns, CsvParserConfig config) {
    if (config.filterColumn() == null) return true;
    return columns[config.filterColumn()].trim().equals(config.filterValue());
  }

  @SneakyThrows
  private BigDecimal parseNumericValue(String[] columns, CsvParserConfig config, String line) {
    String valueStr = columns[config.valueColumn()].trim();
    try {
      return (BigDecimal) DEFAULT_DECIMAL_FORMAT.parse(valueStr);
    } catch (ParseException e) {
      log.error("Numeric parse error for '{}' in line: {}", valueStr, line);
      throw e;
    }
  }
}
