package ee.tuleva.onboarding.fund.fees;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jspecify.annotations.Nullable;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
class PensionikeskusFeeComparisonClient {

  private static final String FUND_COLUMN = "Fond";
  private static final String FEE_COLUMN = "Valitsemistasu";
  private static final char NON_BREAKING_SPACE = (char) 0xA0;

  private final RestClient restClient;
  private final RetryTemplate retryTemplate;
  private final PensionikeskusFeesProperties properties;

  PensionikeskusFeeComparisonClient(
      RestClient pensionikeskusFeesRestClient,
      RetryTemplate pensionikeskusFeesRetryTemplate,
      PensionikeskusFeesProperties properties) {
    this.restClient = pensionikeskusFeesRestClient;
    this.retryTemplate = pensionikeskusFeesRetryTemplate;
    this.properties = properties;
  }

  List<PensionikeskusFeeRow> fetchManagementFees(int pillar) {
    String url = feeComparisonUrl(pillar);
    String html = retryTemplate.invoke(() -> download(url));
    return parse(html, url);
  }

  private String feeComparisonUrl(int pillar) {
    return switch (pillar) {
      case 2 -> properties.secondPillarFeeComparisonUrl();
      case 3 -> properties.thirdPillarFeeComparisonUrl();
      default -> throw new IllegalArgumentException("Unsupported pillar: pillar=" + pillar);
    };
  }

  private String download(String url) {
    String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
    if (body == null) {
      throw new IllegalStateException("Empty fee comparison response: url=" + url);
    }
    return body;
  }

  private List<PensionikeskusFeeRow> parse(String html, String url) {
    Document document = Jsoup.parse(html);
    for (Element table : document.select("table")) {
      Element headerRow = headerRow(table);
      if (headerRow == null) {
        continue;
      }
      Elements headerCells = headerRow.select("th, td");
      int fundIndex = indexOf(headerCells, FUND_COLUMN);
      int feeIndex = indexOf(headerCells, FEE_COLUMN);
      if (fundIndex < 0 || feeIndex < 0) {
        continue;
      }
      List<PensionikeskusFeeRow> rows = parseRows(table, fundIndex, feeIndex);
      if (rows.isEmpty()) {
        throw new IllegalStateException("Fee comparison table has no parseable rows: url=" + url);
      }
      return rows;
    }
    throw new IllegalStateException(
        "Fee comparison response has no table with a Valitsemistasu column: url=" + url);
  }

  private List<PensionikeskusFeeRow> parseRows(Element table, int fundIndex, int feeIndex) {
    List<PensionikeskusFeeRow> rows = new ArrayList<>();
    for (Element row : table.select("tr")) {
      Elements cells = row.select("td");
      if (cells.isEmpty()) {
        continue;
      }
      parseRow(cells, fundIndex, feeIndex).ifPresent(rows::add);
    }
    return rows;
  }

  private Optional<PensionikeskusFeeRow> parseRow(Elements cells, int fundIndex, int feeIndex) {
    if (cells.size() <= Math.max(fundIndex, feeIndex)) {
      log.error("Skipping malformed fee comparison row: cellCount={}", cells.size());
      return Optional.empty();
    }
    String fundName = cells.get(fundIndex).text();
    String rawFee = cells.get(feeIndex).text();
    try {
      return Optional.of(PensionikeskusFeeRow.of(fundName, rawFee));
    } catch (NumberFormatException e) {
      log.error("Skipping unparseable fee comparison row: fund={}, value={}", fundName, rawFee);
      return Optional.empty();
    }
  }

  private static @Nullable Element headerRow(Element table) {
    for (Element row : table.select("tr")) {
      if (!row.select("th").isEmpty()) {
        return row;
      }
    }
    return null;
  }

  private static int indexOf(Elements headerCells, String columnName) {
    for (int i = 0; i < headerCells.size(); i++) {
      if (normalize(headerCells.get(i).text()).equals(columnName)) {
        return i;
      }
    }
    return -1;
  }

  private static String normalize(String text) {
    return text.replace(NON_BREAKING_SPACE, ' ').strip();
  }
}
