package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.investment.kid.KidPublicationRequest.MonthToPublish;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KidPublicationAdminController.class)
@Import(AdminTokenValidator.class)
@TestPropertySource(properties = {"admin.api-token=admin-token", "admin.ops-token=ops-token"})
@WithMockUser
class KidPublicationAdminControllerTest {

  private static final YearMonth JANUARY = YearMonth.of(2026, 1);
  private static final YearMonth FEBRUARY = YearMonth.of(2026, 2);
  private static final KidPublicationRequest REQUEST =
      new KidPublicationRequest(
          "portfolio-manager",
          List.of(new MonthToPublish(JANUARY, 41L), new MonthToPublish(FEBRUARY, 42L)));
  private static final String REQUEST_JSON =
      """
      {
        "publishedBy": "portfolio-manager",
        "months": [
          {"month": "2026-01", "calculationId": 41},
          {"month": "2026-02", "calculationId": 42}
        ]
      }
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private KidPublicationService kidPublicationService;

  @Test
  void publishesTheNamedCalculationsForTheOpsTokenAndAnswersWithThePublications() throws Exception {
    var publishedAt = Instant.parse("2026-12-01T08:30:00Z");
    given(kidPublicationService.publish(TKF100, REQUEST))
        .willReturn(
            List.of(
                new KidPublication(TKF100, JANUARY, 41, "portfolio-manager", publishedAt),
                new KidPublication(TKF100, FEBRUARY, 42, "portfolio-manager", publishedAt)));

    mockMvc
        .perform(
            post("/admin/kid/TKF100/publications")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(REQUEST_JSON))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    [
                      {"fund": "TKF100", "month": "2026-01", "calculationId": 41,
                       "publishedBy": "portfolio-manager",
                       "publishedAt": "2026-12-01T08:30:00Z"},
                      {"fund": "TKF100", "month": "2026-02", "calculationId": 42,
                       "publishedBy": "portfolio-manager",
                       "publishedAt": "2026-12-01T08:30:00Z"}
                    ]
                    """,
                    STRICT));
  }

  @Test
  void rejectsAnUnknownTokenWithoutPublishing() throws Exception {
    mockMvc
        .perform(
            post("/admin/kid/TKF100/publications")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType(APPLICATION_JSON)
                .content(REQUEST_JSON))
        .andExpect(status().isUnauthorized());

    verify(kidPublicationService, never()).publish(any(), any());
  }

  @Test
  void reportsAMonthThatCannotBePublishedAsAConflict() throws Exception {
    willThrow(new KidPublicationRefusedException("KID month not published, already published"))
        .given(kidPublicationService)
        .publish(TKF100, REQUEST);

    mockMvc
        .perform(
            post("/admin/kid/TKF100/publications")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(REQUEST_JSON))
        .andExpect(status().isConflict());
  }

  @Test
  void reportsARequestNamingAMonthTwiceAsABadRequest() throws Exception {
    willThrow(new InvalidKidRequestException("A month named twice"))
        .given(kidPublicationService)
        .publish(TKF100, REQUEST);

    mockMvc
        .perform(
            post("/admin/kid/TKF100/publications")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(REQUEST_JSON))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
        {"publishedBy": " ", "months": [{"month": "2026-01", "calculationId": 41}]}
        """,
        """
        {"publishedBy": "portfolio-manager", "months": []}
        """,
        """
        {"publishedBy": "portfolio-manager", "months": [{"month": "2026-01"}]}
        """,
        """
        {"publishedBy": "portfolio-manager", "months": [{"calculationId": 41}]}
        """
      })
  void rejectsARequestWithoutAPublisherOrAMonthOrACalculation(String body) throws Exception {
    mockMvc
        .perform(
            post("/admin/kid/TKF100/publications")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());

    verify(kidPublicationService, never()).publish(any(), any());
  }
}
