package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class EPIFundValueRetriever implements ComparisonIndexRetriever {
  public static final String KEY = "EPI";

  private final RestTemplate restTemplate;

  private static final String EPI_URL =
      "https://www.pensionikeskus.ee/en/statistics/ii-pillar/epi-charts/";

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    String url = getDownloadUrlForRange(startDate, endDate);
    return restTemplate.execute(url, HttpMethod.GET, getRequestCallback(), getResponseExtractor());
  }

  private String getDownloadUrlForRange(LocalDate startDate, LocalDate endDate) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    return UriComponentsBuilder.fromHttpUrl(EPI_URL)
        .queryParam("date_from", startDate.format(formatter))
        .queryParam("date_to", endDate.format(formatter))
        .queryParam("download", "xls")
        .build()
        .toUriString();
  }

  private ResponseExtractor<List<FundValue>> getResponseExtractor() {
    return response -> {
      if (response.getStatusCode() == HttpStatus.OK) {
        InputStream body = response.getBody();
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_16));
        Stream<String> lines = reader.lines().skip(1); // skip tsv header
        return parseLines(lines);
      } else {
        log.warn(
            "Calling Pensionikeskus for EPI values returned response code: "
                + response.getStatusCode());
        return emptyList();
      }
    };
  }

  private List<FundValue> parseLines(Stream<String> lines) {
    return lines.map(this::parseLine).flatMap(Optional::stream).collect(toList());
  }

  private Optional<FundValue> parseLine(String line) {
    String[] parts = line.split("\t");
    if (parts.length != 3) {
      log.warn("EPI response line did not have 3 tab-separated parts, so parsing failed");
      return Optional.empty();
    }

    String fund = parts[1];
    if (!"EPI".equals(fund)) {
      return Optional.empty();
    }

    Optional<LocalDate> date = parseDate(parts[0]);
    Optional<BigDecimal> value = parseAmount(parts[2]);

    if (date.isEmpty() || value.isEmpty()) {
      return Optional.empty();
    }

    if (value.get().compareTo(ZERO) <= 0) {
      return Optional.empty();
    }

    return Optional.of(new FundValue(KEY, date.get(), value.get()));
  }

  private Optional<LocalDate> parseDate(String date) {
    try {
      return Optional.of(LocalDate.parse(date));
    } catch (DateTimeParseException e) {
      log.warn("Failed to parse date out of a line of epi fund values response");
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
      log.warn("Failed to parse value out of a line of epi fund values response");
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
