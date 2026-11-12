package ee.tuleva.onboarding.investment.fundpage;

import static ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Page.FUND;
import static ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Page.SHARED;
import static ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Problem.MISSING;
import static ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Problem.NO_EFFECTIVE_DATE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_HTML;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.investment.fundpage.FundPageDisclosures.Complete;
import ee.tuleva.onboarding.investment.fundpage.FundPageDisclosures.Gaps;
import ee.tuleva.onboarding.investment.fundpage.FundPageDisclosures.NotChecked;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressPageReader;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class FundPageDisclosureCheckTest {

  private static final String API_BASE = "https://tuleva.ee/wp-json/wp/v2";
  private static final String TUK75_PAGE = "tuleva-maailma-aktsiate-pensionifond";
  private static final String TUK00_PAGE = "tuleva-maailma-volakirjade-pensionifond";
  private static final String TUV100_PAGE = "tuleva-iii-samba-pensionifond";
  private static final String TKF100_PAGE = "tuleva-taiendav-kogumisfond-dokumendid";
  private static final String NO_GAPS = "[]";

  private MockRestServiceServer server;
  private FundPageDisclosureCheck check;

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder().baseUrl(API_BASE);
    server = MockRestServiceServer.bindTo(builder).build();
    check = new FundPageDisclosureCheck(new WordPressPageReader(builder.build(), retryTemplate()));
  }

  @Test
  void everyFundPageListingNoGaps_isComplete() {
    pageLists(TUK75_PAGE, NO_GAPS);
    pageLists(TUK00_PAGE, NO_GAPS);
    pageLists(TUV100_PAGE, NO_GAPS);
    pageLists(TKF100_PAGE, NO_GAPS);

    assertThat(check.check())
        .containsExactly(
            new Complete(TUK75), new Complete(TUK00), new Complete(TUV100), new Complete(TKF100));
    server.verify();
  }

  @Test
  void aFundPageListingGaps_hasThoseGaps_andTheOtherPagesStayComplete() {
    pageLists(TUK75_PAGE, NO_GAPS);
    pageLists(TUK00_PAGE, NO_GAPS);
    pageLists(TUV100_PAGE, NO_GAPS);
    pageLists(
        TKF100_PAGE,
        """
        [{"field": "sustainability_risks_file", "problem": "missing", "page": "shared"},
         {"field": "terms_upcoming_file", "problem": "no_effective_date", "page": "fund"}]
        """);

    assertThat(check.check())
        .containsExactly(
            new Complete(TUK75),
            new Complete(TUK00),
            new Complete(TUV100),
            new Gaps(
                TKF100,
                List.of(
                    new DisclosureGap("sustainability_risks_file", MISSING, SHARED),
                    new DisclosureGap("terms_upcoming_file", NO_EFFECTIVE_DATE, FUND))));
    server.verify();
  }

  @Test
  void aFundPageWhoseOnlyGapIsTheInvestmentReport_isComplete_asTheReportPublicationCheckCoversIt() {
    pageLists(
        TUK75_PAGE,
        """
        [{"field": "investment_report_file", "problem": "missing", "page": "fund"}]
        """);
    pageLists(TUK00_PAGE, NO_GAPS);
    pageLists(TUV100_PAGE, NO_GAPS);
    pageLists(TKF100_PAGE, NO_GAPS);

    assertThat(check.check())
        .containsExactly(
            new Complete(TUK75), new Complete(TUK00), new Complete(TUV100), new Complete(TKF100));
    server.verify();
  }

  @Test
  void tkf100sMissingInvestmentReport_staysAGap_asTheReportPublicationCheckDoesNotCoverIt() {
    pageLists(TUK75_PAGE, NO_GAPS);
    pageLists(TUK00_PAGE, NO_GAPS);
    pageLists(TUV100_PAGE, NO_GAPS);
    pageLists(
        TKF100_PAGE,
        """
        [{"field": "investment_report_file", "problem": "missing", "page": "fund"}]
        """);

    assertThat(check.check())
        .containsExactly(
            new Complete(TUK75),
            new Complete(TUK00),
            new Complete(TUV100),
            new Gaps(TKF100, List.of(new DisclosureGap("investment_report_file", MISSING, FUND))));
    server.verify();
  }

  @Test
  void aFundPageThatDoesNotListItsGapsYet_isNotChecked_ratherThanComplete() {
    server
        .expect(requestTo(pageRequest(TUK75_PAGE)))
        .andRespond(withSuccess("[{\"id\": 17533}]", APPLICATION_JSON));
    pageLists(TUK00_PAGE, NO_GAPS);
    pageLists(TUV100_PAGE, NO_GAPS);
    pageLists(TKF100_PAGE, NO_GAPS);

    var pages = check.check();

    assertThat(pages.getFirst())
        .isInstanceOfSatisfying(
            NotChecked.class, notChecked -> assertThat(notChecked.fund()).isEqualTo(TUK75));
    assertThat(pages.subList(1, 4))
        .containsExactly(new Complete(TUK00), new Complete(TUV100), new Complete(TKF100));
    server.verify();
  }

  @Test
  void aFundPageThatCannotBeRead_isNotChecked_andTheOtherPagesAreStillChecked() {
    pageLists(TUK75_PAGE, NO_GAPS);
    server.expect(requestTo(pageRequest(TUK00_PAGE))).andRespond(withResourceNotFound());
    pageLists(TUV100_PAGE, NO_GAPS);
    pageLists(TKF100_PAGE, NO_GAPS);

    var pages = check.check();

    assertThat(pages.get(1))
        .isInstanceOfSatisfying(
            NotChecked.class, notChecked -> assertThat(notChecked.fund()).isEqualTo(TUK00));
    assertThat(List.of(pages.get(0), pages.get(2), pages.get(3)))
        .containsExactly(new Complete(TUK75), new Complete(TUV100), new Complete(TKF100));
    server.verify();
  }

  @Test
  void aFundPageAnsweringWithALongErrorBody_isNotCheckedWithAShortReason() {
    server
        .expect(requestTo(pageRequest(TUK75_PAGE)))
        .andRespond(
            withStatus(UNAUTHORIZED).contentType(TEXT_HTML).body("<html>" + "x".repeat(5000)));
    pageLists(TUK00_PAGE, NO_GAPS);
    pageLists(TUV100_PAGE, NO_GAPS);
    pageLists(TKF100_PAGE, NO_GAPS);

    assertThat(check.check().getFirst())
        .isInstanceOfSatisfying(
            NotChecked.class,
            notChecked -> assertThat(notChecked.reason()).hasSizeLessThanOrEqualTo(201));
    server.verify();
  }

  private void pageLists(String slug, String gaps) {
    server
        .expect(requestTo(pageRequest(slug)))
        .andRespond(
            withSuccess(
                "[{\"id\": 17533, \"tuleva_disclosure_gaps\": " + gaps + "}]", APPLICATION_JSON));
  }

  private static String pageRequest(String slug) {
    return API_BASE + "/pages?slug=" + slug + "&_fields=id,tuleva_disclosure_gaps";
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
