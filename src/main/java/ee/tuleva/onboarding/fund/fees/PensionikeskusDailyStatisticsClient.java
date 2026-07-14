package ee.tuleva.onboarding.fund.fees;

import static java.nio.charset.StandardCharsets.UTF_16;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
class PensionikeskusDailyStatisticsClient {

  private static final String FUND_COLUMN = "Fond";
  private static final String FEE_COLUMN = "Tasud %";

  private final RestClient restClient;
  private final RetryTemplate retryTemplate;
  private final PensionikeskusFeesProperties properties;

  PensionikeskusDailyStatisticsClient(
      RestClient pensionikeskusFeesRestClient,
      RetryTemplate pensionikeskusFeesRetryTemplate,
      PensionikeskusFeesProperties properties) {
    this.restClient = pensionikeskusFeesRestClient;
    this.retryTemplate = pensionikeskusFeesRetryTemplate;
    this.properties = properties;
  }

  List<PensionikeskusFeeRow> fetchOngoingCharges(int pillar) {
    String url = statisticsUrl(pillar) + "?download=xls";
    byte[] body = retryTemplate.invoke(() -> download(url));
    return parse(new String(body, UTF_16), url);
  }

  private String statisticsUrl(int pillar) {
    return switch (pillar) {
      case 2 -> properties.secondPillarStatisticsUrl();
      case 3 -> properties.thirdPillarStatisticsUrl();
      default -> throw new IllegalArgumentException("Unsupported pillar: pillar=" + pillar);
    };
  }

  private byte[] download(String url) {
    byte[] body = restClient.get().uri(URI.create(url)).retrieve().body(byte[].class);
    if (body == null) {
      throw new IllegalStateException("Empty statistics response: url=" + url);
    }
    return body;
  }

  private List<PensionikeskusFeeRow> parse(String content, String url) {
    List<String> lines = content.lines().toList();
    if (lines.isEmpty()) {
      throw new IllegalStateException("Empty statistics response: url=" + url);
    }
    String[] header = lines.getFirst().split("\t", -1);
    int fundIndex = indexOf(header, FUND_COLUMN);
    int feeIndex = indexOf(header, FEE_COLUMN);
    if (fundIndex < 0 || feeIndex < 0) {
      throw new IllegalStateException(
          "Statistics response missing required columns: url="
              + url
              + ", header="
              + lines.getFirst());
    }
    List<PensionikeskusFeeRow> rows = new ArrayList<>();
    for (String line : lines.subList(1, lines.size())) {
      parseRow(line, fundIndex, feeIndex).ifPresent(rows::add);
    }
    if (rows.isEmpty()) {
      throw new IllegalStateException("Statistics response has no parseable rows: url=" + url);
    }
    return List.copyOf(rows);
  }

  private Optional<PensionikeskusFeeRow> parseRow(String line, int fundIndex, int feeIndex) {
    String[] columns = line.split("\t", -1);
    if (columns.length <= Math.max(fundIndex, feeIndex)) {
      log.error("Skipping malformed statistics row: line={}", line);
      return Optional.empty();
    }
    try {
      return Optional.of(PensionikeskusFeeRow.of(columns[fundIndex], columns[feeIndex]));
    } catch (NumberFormatException e) {
      log.error("Skipping unparseable statistics row: line={}", line);
      return Optional.empty();
    }
  }

  private static int indexOf(String[] header, String columnName) {
    for (int i = 0; i < header.length; i++) {
      if (header[i].strip().equals(columnName)) {
        return i;
      }
    }
    return -1;
  }
}
