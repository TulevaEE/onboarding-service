package ee.tuleva.onboarding.investment.report.publishing.github;

import ee.tuleva.onboarding.investment.report.publishing.FundReportMapping;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
@RequiredArgsConstructor
public class GitHubPrClient {

  private static final Pattern VALID_WP_PDF_URL =
      Pattern.compile(
          "^https://tuleva\\.ee/wp-content/uploads/\\d{4}/\\d{2}/[A-Za-z0-9._-]+\\.pdf$");
  private static final Pattern REPORT_LINK_PATTERN =
      Pattern.compile("generate_report_link\\('[^']*'\\)");

  private final RestClient restClient;
  private final String defaultBranch;
  private final Clock clock;

  @SuppressWarnings("unchecked")
  public String createReportPr(Map<FundReportMapping, String> mappingToWpUrl, YearMonth month) {
    var monthAscii = asciiSafe(FundReportMapping.estonianMonth(month.getMonthValue()));
    var branchName =
        "update-investment-reports-%s-%d-%d"
            .formatted(monthAscii, month.getYear(), clock.instant().getEpochSecond());

    log.info("Creating GitHub PR: branch={}", branchName);

    var masterSha = getMasterSha();
    createBranch(branchName, masterSha);

    for (var entry : mappingToWpUrl.entrySet()) {
      var mapping = entry.getKey();
      var wpUrl = entry.getValue();

      if (!VALID_WP_PDF_URL.matcher(wpUrl).matches()) {
        throw new IllegalArgumentException(
            "Invalid WordPress URL for " + mapping.fund().getCode() + ": " + wpUrl);
      }

      updatePhpFile(mapping.phpFilePath(), branchName, wpUrl, mapping.fund().getCode(), month);
    }

    var title =
        "Investeeringute aruanded %s %d"
            .formatted(FundReportMapping.estonianMonth(month.getMonthValue()), month.getYear());
    var body =
        "Uuendatud investeeringute aruannete URL-id:\n\n"
            + mappingToWpUrl.entrySet().stream()
                .map(e -> "- %s: %s".formatted(e.getKey().fund().getCode(), e.getValue()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

    var prResponse =
        restClient
            .post()
            .uri("/pulls")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "title", title,
                    "head", branchName,
                    "base", defaultBranch,
                    "body", body))
            .retrieve()
            .body(Map.class);

    var prUrl = (String) prResponse.get("html_url");
    log.info("GitHub PR created: url={}", prUrl);
    return prUrl;
  }

  @SuppressWarnings("unchecked")
  private String getMasterSha() {
    var response =
        restClient.get().uri("/git/ref/heads/{branch}", defaultBranch).retrieve().body(Map.class);
    var object = (Map<String, Object>) response.get("object");
    return (String) object.get("sha");
  }

  private void createBranch(String branchName, String sha) {
    restClient
        .post()
        .uri("/git/refs")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("ref", "refs/heads/" + branchName, "sha", sha))
        .retrieve()
        .body(Map.class);
  }

  @SuppressWarnings("unchecked")
  private void updatePhpFile(
      String filePath, String branch, String wpUrl, String fundCode, YearMonth month) {
    var fileResponse =
        restClient
            .get()
            .uri("/contents/{path}?ref={branch}", filePath, branch)
            .retrieve()
            .body(Map.class);

    var currentContent =
        new String(Base64.getMimeDecoder().decode((String) fileResponse.get("content")));
    var fileSha = (String) fileResponse.get("sha");

    var matcher = REPORT_LINK_PATTERN.matcher(currentContent);
    if (!matcher.find()) {
      throw new IllegalStateException(
          "generate_report_link() not found in %s — PHP file format may have changed"
              .formatted(filePath));
    }

    var replacement =
        java.util.regex.Matcher.quoteReplacement("generate_report_link('" + wpUrl + "')");
    var newContent = matcher.replaceFirst(replacement);

    var monthName = FundReportMapping.estonianMonth(month.getMonthValue());
    var message =
        "Uuenda %s investeeringute aruande URL (%s %d)"
            .formatted(fundCode, monthName, month.getYear());

    restClient
        .put()
        .uri("/contents/{path}", filePath)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "message", message,
                "content", Base64.getEncoder().encodeToString(newContent.getBytes()),
                "sha", fileSha,
                "branch", branch))
        .retrieve()
        .body(Map.class);

    log.info("Updated PHP file: path={}, fund={}", filePath, fundCode);
  }

  static String asciiSafe(String s) {
    return s.replace("ä", "a")
        .replace("Ä", "A")
        .replace("õ", "o")
        .replace("Õ", "O")
        .replace("ö", "o")
        .replace("Ö", "O")
        .replace("ü", "u")
        .replace("Ü", "U");
  }
}
