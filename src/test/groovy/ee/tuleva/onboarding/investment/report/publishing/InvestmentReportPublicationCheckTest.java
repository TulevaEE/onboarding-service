package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.NotChecked;
import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.NotLinked;
import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.Outdated;
import ee.tuleva.onboarding.investment.report.publishing.ReportPublication.Published;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressPageReader;
import java.time.Duration;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class InvestmentReportPublicationCheckTest {

  private static final String API_BASE = "https://tuleva.ee/wp-json/wp/v2";
  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final String TUK75_SEPTEMBER =
      "https://tuleva.ee/wp-content/uploads/2026/10/"
          + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-09.pdf";
  private static final String TUK00_SEPTEMBER =
      "https://tuleva.ee/wp-content/uploads/2026/10/"
          + "tuleva-maailma-volakirjade-pensionifondi-investeeringute-aruanne-2026-09.pdf";
  private static final String TUK00_AUGUST =
      "https://tuleva.ee/wp-content/uploads/2026/09/"
          + "Tuleva-Maailma-Volakirjade-Pensionifondi-investeeringute-aruanne-2026-08.pdf";
  private static final String TUV100_SEPTEMBER =
      "https://tuleva.ee/wp-content/uploads/2026/10/"
          + "tuleva-iii-samba-pensionifondi-investeeringute-aruanne-2026-09.pdf";

  private MockRestServiceServer server;
  private InvestmentReportPublicationCheck check;

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder().baseUrl(API_BASE);
    server = MockRestServiceServer.bindTo(builder).build();
    check =
        new InvestmentReportPublicationCheck(
            new WordPressPageReader(builder.build(), retryTemplate()));
  }

  @Test
  void everyPensionFundPageLinkingThatMonthsReport_isPublished_andTheSavingsFundIsNotChecked() {
    pageLinks("tuleva-maailma-aktsiate-pensionifond", 37853, TUK75_SEPTEMBER);
    pageLinks("tuleva-maailma-volakirjade-pensionifond", 37856, TUK00_SEPTEMBER);
    pageLinks("tuleva-iii-samba-pensionifond", 37859, TUV100_SEPTEMBER);

    assertThat(check.check(SEPTEMBER))
        .containsExactly(
            new Published(TUK75, TUK75_SEPTEMBER),
            new Published(TUK00, TUK00_SEPTEMBER),
            new Published(TUV100, TUV100_SEPTEMBER));
    server.verify();
  }

  @Test
  void aPageStillLinkingThePreviousMonthsReport_isOutdated() {
    pageLinks("tuleva-maailma-aktsiate-pensionifond", 37853, TUK75_SEPTEMBER);
    pageLinks("tuleva-maailma-volakirjade-pensionifond", 36990, TUK00_AUGUST);
    pageLinks("tuleva-iii-samba-pensionifond", 37859, TUV100_SEPTEMBER);

    assertThat(check.check(SEPTEMBER))
        .containsExactly(
            new Published(TUK75, TUK75_SEPTEMBER),
            new Outdated(TUK00, TUK00_AUGUST),
            new Published(TUV100, TUV100_SEPTEMBER));
    server.verify();
  }

  @Test
  void aPageWhoseReportFieldIsEmpty_linksNoReport() {
    pageLinks("tuleva-maailma-aktsiate-pensionifond", 37853, TUK75_SEPTEMBER);
    pageLinks("tuleva-maailma-volakirjade-pensionifond", 37856, TUK00_SEPTEMBER);
    pageLinksNothing("tuleva-iii-samba-pensionifond");

    assertThat(check.check(SEPTEMBER))
        .containsExactly(
            new Published(TUK75, TUK75_SEPTEMBER),
            new Published(TUK00, TUK00_SEPTEMBER),
            new NotLinked(TUV100));
    server.verify();
  }

  @Test
  void aPageThatCannotBeRead_isNotChecked_andTheOtherPagesAreStillChecked() {
    server
        .expect(requestTo(pageRequest("tuleva-maailma-aktsiate-pensionifond")))
        .andRespond(withResourceNotFound());
    pageLinks("tuleva-maailma-volakirjade-pensionifond", 37856, TUK00_SEPTEMBER);
    pageLinks("tuleva-iii-samba-pensionifond", 37859, TUV100_SEPTEMBER);

    var publications = check.check(SEPTEMBER);

    assertThat(publications.getFirst()).isInstanceOf(NotChecked.class);
    assertThat(publications.getFirst().fund()).isEqualTo(TUK75);
    assertThat(publications.subList(1, 3))
        .containsExactly(
            new Published(TUK00, TUK00_SEPTEMBER), new Published(TUV100, TUV100_SEPTEMBER));
    server.verify();
  }

  private void pageLinks(String slug, int attachmentId, String reportUrl) {
    server
        .expect(requestTo(pageRequest(slug)))
        .andRespond(
            withSuccess(
                "[{\"id\": 17533, \"acf\": {\"investment_report_file\": " + attachmentId + "}}]",
                APPLICATION_JSON));
    server
        .expect(requestTo(API_BASE + "/media/" + attachmentId + "?_fields=source_url"))
        .andRespond(withSuccess("{\"source_url\": \"" + reportUrl + "\"}", APPLICATION_JSON));
  }

  private void pageLinksNothing(String slug) {
    server
        .expect(requestTo(pageRequest(slug)))
        .andRespond(
            withSuccess(
                "[{\"id\": 20938, \"acf\": {\"investment_report_file\": null}}]",
                APPLICATION_JSON));
  }

  private static String pageRequest(String slug) {
    return API_BASE + "/pages?slug=" + slug + "&_fields=id,acf";
  }

  private static RetryTemplate retryTemplate() {
    return new RetryTemplate(
        RetryPolicy.builder()
            .includes(HttpServerErrorException.class, ResourceAccessException.class)
            .excludes(HttpClientErrorException.class)
            .maxRetries(2)
            .delay(Duration.ofMillis(1))
            .build());
  }
}
