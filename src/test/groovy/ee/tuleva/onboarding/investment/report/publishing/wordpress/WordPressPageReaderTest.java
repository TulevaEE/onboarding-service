package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import static ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Page.FUND;
import static ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Page.SHARED;
import static ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Problem.MISSING;
import static ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Problem.NO_EFFECTIVE_DATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class WordPressPageReaderTest {

  private static final String API_BASE = "https://tuleva.ee/wp-json/wp/v2";
  private static final String SLUG = "tuleva-maailma-aktsiate-pensionifond";
  private static final String PAGE_REQUEST = API_BASE + "/pages?slug=" + SLUG + "&_fields=id,acf";
  private static final String MEDIA_REQUEST = API_BASE + "/media/37853?_fields=source_url";
  private static final String DISCLOSURE_GAPS_REQUEST =
      API_BASE + "/pages?slug=" + SLUG + "&_fields=id,tuleva_disclosure_gaps";
  private static final String REPORT_URL =
      "https://tuleva.ee/wp-content/uploads/2026/10/"
          + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-09.pdf";

  private MockRestServiceServer server;
  private WordPressPageReader reader;

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder().baseUrl(API_BASE);
    server = MockRestServiceServer.bindTo(builder).build();
    reader = new WordPressPageReader(builder.build(), retryTemplate());
  }

  @Test
  void returnsTheFileThePagesReportFieldLinks() {
    server
        .expect(requestTo(PAGE_REQUEST))
        .andExpect(method(GET))
        .andRespond(
            json(
                """
                [{"id": 17533, "acf": {"prospectus_file": null, "investment_report_file": 37853}}]
                """));
    server
        .expect(requestTo(MEDIA_REQUEST))
        .andExpect(method(GET))
        .andRespond(json("{\"source_url\": \"" + REPORT_URL + "\"}"));

    assertThat(reader.investmentReportUrl(SLUG)).contains(REPORT_URL);
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"37853\"",
        "\"37853.0\"",
        "37853.0",
        "{\"ID\": 37853, \"url\": \"ignored\"}",
        "{\"id\": 37853}",
        "[37853]",
        "[{\"id\": 37853}]"
      })
  void readsTheAttachmentIdFromEveryShapeTheFieldCanTake(String fieldValue) {
    server
        .expect(requestTo(PAGE_REQUEST))
        .andRespond(
            json("[{\"id\": 17533, \"acf\": {\"investment_report_file\": " + fieldValue + "}}]"));
    server
        .expect(requestTo(MEDIA_REQUEST))
        .andRespond(json("{\"source_url\": \"" + REPORT_URL + "\"}"));

    assertThat(reader.investmentReportUrl(SLUG)).contains(REPORT_URL);
    server.verify();
  }

  @Test
  void refusesAnAttachmentObjectWithNoId() {
    server
        .expect(requestTo(PAGE_REQUEST))
        .andRespond(
            json(
                "[{\"id\": 17533, \"acf\": {\"investment_report_file\": {\"url\": \""
                    + REPORT_URL
                    + "\"}}}]"));

    assertThatThrownBy(() -> reader.investmentReportUrl(SLUG))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "\"\"", "false", "[]"})
  void returnsNothingWhileThePagesReportFieldIsEmpty(String emptyFieldValue) {
    server
        .expect(requestTo(PAGE_REQUEST))
        .andRespond(
            json(
                "[{\"id\": 17533, \"acf\": {\"investment_report_file\": "
                    + emptyFieldValue
                    + "}}]"));

    assertThat(reader.investmentReportUrl(SLUG)).isEmpty();
    server.verify();
  }

  @Test
  void refusesAPageThatDoesNotExposeTheReportField() {
    server
        .expect(requestTo(PAGE_REQUEST))
        .andRespond(json("[{\"id\": 17533, \"acf\": {\"prospectus_file\": null}}]"));

    assertThatThrownBy(() -> reader.investmentReportUrl(SLUG))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesAPageWithNoFieldsOverRest() {
    server.expect(requestTo(PAGE_REQUEST)).andRespond(json("[{\"id\": 17533, \"acf\": []}]"));

    assertThatThrownBy(() -> reader.investmentReportUrl(SLUG))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesASlugThatMatchesNoPage() {
    server.expect(requestTo(PAGE_REQUEST)).andRespond(json("[]"));

    assertThatThrownBy(() -> reader.investmentReportUrl(SLUG))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesASlugThatMatchesSeveralPages() {
    server
        .expect(requestTo(PAGE_REQUEST))
        .andRespond(
            json(
                """
                [{"id": 17533, "acf": {"investment_report_file": 37853}},
                 {"id": 17534, "acf": {"investment_report_file": 37854}}]
                """));

    assertThatThrownBy(() -> reader.investmentReportUrl(SLUG))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesAReportFieldThatHoldsNoAttachmentId() {
    server
        .expect(requestTo(PAGE_REQUEST))
        .andRespond(
            json(
                "[{\"id\": 17533, \"acf\": {\"investment_report_file\": \""
                    + REPORT_URL
                    + "\"}}]"));

    assertThatThrownBy(() -> reader.investmentReportUrl(SLUG))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesAnAttachmentWithNoSourceUrl() {
    server
        .expect(requestTo(PAGE_REQUEST))
        .andRespond(json("[{\"id\": 17533, \"acf\": {\"investment_report_file\": 37853}}]"));
    server.expect(requestTo(MEDIA_REQUEST)).andRespond(json("{}"));

    assertThatThrownBy(() -> reader.investmentReportUrl(SLUG))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void retriesAServerErrorBeforeReadingThePage() {
    server.expect(requestTo(PAGE_REQUEST)).andRespond(withServerError());
    server
        .expect(requestTo(PAGE_REQUEST))
        .andRespond(json("[{\"id\": 17533, \"acf\": {\"investment_report_file\": null}}]"));

    assertThat(reader.investmentReportUrl(SLUG)).isEmpty();
    server.verify();
  }

  @Test
  void returnsTheDisclosureGapsThePageLists() {
    server
        .expect(requestTo(DISCLOSURE_GAPS_REQUEST))
        .andExpect(method(GET))
        .andRespond(
            json(
                """
                [{"id": 17533, "tuleva_disclosure_gaps": [
                  {"field": "model_portfolio_file", "problem": "missing", "page": "fund"},
                  {"field": "sustainability_risks_file", "problem": "missing", "page": "shared"},
                  {"field": "terms_upcoming_file", "problem": "no_effective_date", "page": "fund"}
                ]}]
                """));

    assertThat(reader.disclosureGaps(SLUG))
        .containsExactly(
            new DisclosureGap("model_portfolio_file", MISSING, FUND),
            new DisclosureGap("sustainability_risks_file", MISSING, SHARED),
            new DisclosureGap("terms_upcoming_file", NO_EFFECTIVE_DATE, FUND));
    server.verify();
  }

  @Test
  void returnsNoDisclosureGapsOnceThePageListsNone() {
    server
        .expect(requestTo(DISCLOSURE_GAPS_REQUEST))
        .andRespond(json("[{\"id\": 17533, \"tuleva_disclosure_gaps\": []}]"));

    assertThat(reader.disclosureGaps(SLUG)).isEmpty();
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"id\": 17533}",
        "{\"id\": 17533, \"tuleva_disclosure_gaps\": null}",
        "{\"id\": 17533, \"tuleva_disclosure_gaps\": {}}",
        "{\"id\": 17533, \"tuleva_disclosure_gaps\": \"none\"}"
      })
  void refusesAPageThatDoesNotListItsDisclosureGaps(String page) {
    server.expect(requestTo(DISCLOSURE_GAPS_REQUEST)).andRespond(json("[" + page + "]"));

    assertThatThrownBy(() -> reader.disclosureGaps(SLUG)).isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"model_portfolio_file\"",
        "{\"problem\": \"missing\", \"page\": \"fund\"}",
        "{\"field\": \"model_portfolio_file\", \"page\": \"fund\"}",
        "{\"field\": \"model_portfolio_file\", \"problem\": \"missing\"}",
        "{\"field\": null, \"problem\": \"missing\", \"page\": \"fund\"}",
        "{\"field\": \"prospectus_file\", \"problem\": \"expired\", \"page\": \"fund\"}",
        "{\"field\": \"prospectus_file\", \"problem\": \"missing\", \"page\": \"other\"}"
      })
  void refusesADisclosureGapItDoesNotKnow(String gap) {
    server
        .expect(requestTo(DISCLOSURE_GAPS_REQUEST))
        .andRespond(json("[{\"id\": 17533, \"tuleva_disclosure_gaps\": [" + gap + "]}]"));

    assertThatThrownBy(() -> reader.disclosureGaps(SLUG)).isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  private static ResponseCreator json(String body) {
    return withSuccess(body, APPLICATION_JSON);
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
