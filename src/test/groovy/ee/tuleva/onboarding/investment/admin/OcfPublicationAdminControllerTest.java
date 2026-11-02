package ee.tuleva.onboarding.investment.admin;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationBasis.EX_POST;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationBasis.MONTHLY_ESTIMATE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.investment.fees.ocf.ExceptionDecision;
import ee.tuleva.onboarding.investment.fees.ocf.KidFigure;
import ee.tuleva.onboarding.investment.fees.ocf.KidReview;
import ee.tuleva.onboarding.investment.fees.ocf.OcfCalculationDecisionService;
import ee.tuleva.onboarding.investment.fees.ocf.OcfCalculationPreview;
import ee.tuleva.onboarding.investment.fees.ocf.OcfExceptionRequest;
import ee.tuleva.onboarding.investment.fees.ocf.OcfGivenPublicationRequest;
import ee.tuleva.onboarding.investment.fees.ocf.OcfKidReviewRequest;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationPreview;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationRefusedException;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationRequest;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OcfPublicationAdminController.class)
@Import({AdminTokenValidator.class, OcfPublicationRefusalAdvice.class})
@TestPropertySource(properties = {"admin.api-token=valid-token", "admin.ops-token=ops-token"})
@WithMockUser
class OcfPublicationAdminControllerTest {

  private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
  private static final String A_FINGERPRINT = "0".repeat(64);
  private static final LocalDate NOVEMBER_1 = LocalDate.of(2026, 11, 1);
  private static final KidFigure KID =
      new KidFigure(
          5L,
          LocalDate.of(2026, 3, 1),
          new BigDecimal("0.00460000"),
          2,
          MONTHLY_ESTIMATE,
          "KID 2026");
  private static final ExceptionDecision EXCEPTION =
      new ExceptionDecision(
          "fund-manager", "the board decided the fee", Instant.parse("2026-11-01T08:00:00Z"));

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OcfPublicationService ocfPublicationService;
  @MockitoBean private OcfCalculationDecisionService ocfCalculationDecisionService;

  private static final OcfPublicationPreview PREVIEW =
      new OcfPublicationPreview(
          "TUK75",
          OCTOBER,
          2,
          false,
          "{\"gaps\":[\"MONTH_END_NAV_NOT_PUBLISHED\"]}",
          A_FINGERPRINT,
          new BigDecimal("0.00460000"),
          new BigDecimal("0.00200000"),
          NOVEMBER_1,
          new BigDecimal("0.00190000"),
          new BigDecimal("0.00450000"),
          new BigDecimal("0.45"),
          2,
          KID,
          EXCEPTION,
          false);

  private static final OcfCalculationPreview CALCULATION =
      new OcfCalculationPreview(
          "TUK75",
          OCTOBER,
          2,
          true,
          "{\"gaps\":[]}",
          A_FINGERPRINT,
          new BigDecimal("0.00450000"),
          new BigDecimal("0.00200000"),
          2,
          KID,
          true,
          null,
          new KidReview(
              "portfolio-manager", "a one-off rebate", Instant.parse("2026-11-05T08:00:00Z")),
          false);

  private static final String PUBLISH_BODY =
      """
      {"fund": "TUK75", "month": "2026-10", "version": 2, "validFrom": "2026-11-01",
       "calculationFingerprint": "%s",
       "publishedOcf": 0.00450000, "publishedIn": "KID 01.11.2026",
       "publishedBy": "portfolio-manager", "basisReason": "the monthly estimate"}
      """
          .formatted(A_FINGERPRINT);

  @Test
  void theCalculationShowsTheMonthBesideTheKidAtThePublishedPrecision() throws Exception {
    given(ocfCalculationDecisionService.preview(TUK75, OCTOBER)).willReturn(CALCULATION);

    mockMvc
        .perform(
            post("/admin/ocf-publications/calculation")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "month": "2026-10"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calculationFingerprint").value(A_FINGERPRINT))
        .andExpect(jsonPath("$.publishedDecimals").value(2))
        .andExpect(jsonPath("$.kidInForceAtMonthEnd.publicationId").value(5))
        .andExpect(jsonPath("$.kidInForceAtMonthEnd.basis").value("MONTHLY_ESTIMATE"))
        .andExpect(jsonPath("$.kidInForceAtMonthEnd.publishedDecimals").value(2))
        .andExpect(jsonPath("$.differsFromTheKid").value(true))
        .andExpect(jsonPath("$.keptOnReview.reviewedBy").value("portfolio-manager"));
  }

  @Test
  void theFundManagersExceptionIsRecordedForTheCalculation() throws Exception {
    given(
            ocfCalculationDecisionService.recordException(
                new OcfExceptionRequest(
                    TUK75, OCTOBER, 2, A_FINGERPRINT, "the board decided the fee", "fund-manager")))
        .willReturn(CALCULATION);

    mockMvc
        .perform(
            post("/admin/ocf-publications/exception")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "month": "2026-10", "version": 2,
                     "calculationFingerprint": "%s",
                     "reason": "the board decided the fee", "decidedBy": "fund-manager"}
                    """
                        .formatted(A_FINGERPRINT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
  }

  @Test
  void anExceptionWithoutAReasonIsRejectedBeforeTheService() throws Exception {
    mockMvc
        .perform(
            post("/admin/ocf-publications/exception")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "month": "2026-10", "version": 2,
                     "calculationFingerprint": "%s", "reason": " ", "decidedBy": "fund-manager"}
                    """
                        .formatted(A_FINGERPRINT)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(ocfCalculationDecisionService);
  }

  @Test
  void aReviewKeepingTheKidIsRecordedForTheCalculation() throws Exception {
    given(
            ocfCalculationDecisionService.recordReview(
                new OcfKidReviewRequest(
                    TUK75, OCTOBER, 2, A_FINGERPRINT, 5L, "a one-off rebate", "portfolio-manager")))
        .willReturn(CALCULATION);

    mockMvc
        .perform(
            post("/admin/ocf-publications/review")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "month": "2026-10", "version": 2,
                     "calculationFingerprint": "%s", "publicationId": 5,
                     "reason": "a one-off rebate", "reviewedBy": "portfolio-manager"}
                    """
                        .formatted(A_FINGERPRINT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keptOnReview.reason").value("a one-off rebate"));
  }

  @Test
  void aReviewThatDoesNotNameTheReviewedKidIsRejectedBeforeTheService() throws Exception {
    mockMvc
        .perform(
            post("/admin/ocf-publications/review")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "month": "2026-10", "version": 2,
                     "calculationFingerprint": "%s",
                     "reason": "a one-off rebate", "reviewedBy": "portfolio-manager"}
                    """
                        .formatted(A_FINGERPRINT)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(ocfCalculationDecisionService);
  }

  @Test
  void aRefusedReviewIsAConflictThatSaysWhy() throws Exception {
    given(ocfCalculationDecisionService.recordReview(any(OcfKidReviewRequest.class)))
        .willThrow(OcfPublicationRefusedException.class);

    mockMvc
        .perform(
            post("/admin/ocf-publications/review")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "month": "2026-10", "version": 2,
                     "calculationFingerprint": "%s", "publicationId": 5,
                     "reason": "a one-off rebate", "reviewedBy": "portfolio-manager"}
                    """
                        .formatted(A_FINGERPRINT)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(TEXT_PLAIN));
  }

  @Test
  void previewWithTheGatewaysOpsTokenShowsTheFigureAndTheExceptionThatWouldBePublished()
      throws Exception {
    given(ocfPublicationService.preview(TUK75, OCTOBER, NOVEMBER_1)).willReturn(PREVIEW);

    mockMvc
        .perform(
            post("/admin/ocf-publications/preview")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "month": "2026-10", "validFrom": "2026-11-01"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.publishedOcfPercent").value(0.45))
        .andExpect(jsonPath("$.publishedDecimals").value(2))
        .andExpect(jsonPath("$.kidInForceBefore.publishedIn").value("KID 2026"))
        .andExpect(jsonPath("$.exception.decidedBy").value("fund-manager"));
  }

  @Test
  void publishWithTheGatewaysOpsTokenRecordsThePublicationWithItsBasisReason() throws Exception {
    given(
            ocfPublicationService.publish(
                new OcfPublicationRequest(
                    TUK75,
                    OCTOBER,
                    2,
                    A_FINGERPRINT,
                    NOVEMBER_1,
                    new BigDecimal("0.00450000"),
                    "KID 01.11.2026",
                    "portfolio-manager",
                    "the monthly estimate")))
        .willReturn(PREVIEW);

    mockMvc
        .perform(
            post("/admin/ocf-publications")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(PUBLISH_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validFrom").value("2026-11-01"));
  }

  @Test
  void aRefusedPublicationIsAConflictThatSaysWhy() throws Exception {
    given(ocfPublicationService.publish(any(OcfPublicationRequest.class)))
        .willThrow(OcfPublicationRefusedException.class);

    mockMvc
        .perform(
            post("/admin/ocf-publications")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(PUBLISH_BODY))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(TEXT_PLAIN));
  }

  @Test
  void aPublicationWithoutABasisReasonIsRejectedBeforeTheService() throws Exception {
    mockMvc
        .perform(
            post("/admin/ocf-publications")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "month": "2026-10", "version": 2, "validFrom": "2026-11-01",
                     "calculationFingerprint": "%s",
                     "publishedOcf": 0.00450000, "publishedIn": "KID 01.11.2026",
                     "publishedBy": "portfolio-manager"}
                    """
                        .formatted(A_FINGERPRINT)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(ocfPublicationService);
  }

  @Test
  void aGivenExPostFigureIsRecordedWithItsPeriod() throws Exception {
    given(
            ocfPublicationService.publishGiven(
                new OcfGivenPublicationRequest(
                    TUK75,
                    NOVEMBER_1,
                    new BigDecimal("0.004638"),
                    2,
                    EX_POST,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31),
                    "KID 01.11.2026",
                    "portfolio-manager",
                    "the ex-post result differs from the estimate")))
        .willReturn(
            new KidFigure(
                7L, NOVEMBER_1, new BigDecimal("0.004638"), 2, EX_POST, "KID 01.11.2026"));

    mockMvc
        .perform(
            post("/admin/ocf-publications/given")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "validFrom": "2026-11-01", "publishedOcf": 0.004638,
                     "publishedDecimals": 2, "basis": "EX_POST", "exPostPeriodStart": "2025-01-01",
                     "exPostPeriodEnd": "2025-12-31", "publishedIn": "KID 01.11.2026",
                     "publishedBy": "portfolio-manager",
                     "basisReason": "the ex-post result differs from the estimate"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publicationId").value(7))
        .andExpect(jsonPath("$.publishedDecimals").value(2))
        .andExpect(jsonPath("$.basis").value("EX_POST"));
  }

  @Test
  void aGivenFigureAboveTenPercentIsRejectedBeforeTheServiceAsAPercentSentForAFraction()
      throws Exception {
    mockMvc
        .perform(
            post("/admin/ocf-publications/given")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "validFrom": "2026-11-01", "publishedOcf": 0.4638,
                     "publishedDecimals": 2, "basis": "MONTHLY_ESTIMATE",
                     "publishedIn": "KID 01.11.2026",
                     "publishedBy": "portfolio-manager", "basisReason": "the KID in force"}
                    """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(ocfPublicationService);
  }

  @Test
  void aGivenFigureMustSayHowTheKidPrintsItWithOneToFourDecimals() throws Exception {
    for (var decimals : new String[] {"0", "5", "null"}) {
      mockMvc
          .perform(
              post("/admin/ocf-publications/given")
                  .with(csrf())
                  .header("X-Admin-Token", "ops-token")
                  .contentType(APPLICATION_JSON)
                  .content(
                      """
                      {"fund": "TUK75", "validFrom": "2026-11-01", "publishedOcf": 0.004638,
                       "publishedDecimals": %s, "basis": "MONTHLY_ESTIMATE",
                       "publishedIn": "KID 01.11.2026", "publishedBy": "portfolio-manager",
                       "basisReason": "the KID in force"}
                      """
                          .formatted(decimals)))
          .andExpect(status().isBadRequest());
    }

    verifyNoInteractions(ocfPublicationService);
  }

  @Test
  void aPreviewWithoutAMonthIsRejectedBeforeTheService() throws Exception {
    mockMvc
        .perform(
            post("/admin/ocf-publications/preview")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "validFrom": "2026-11-01"}
                    """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(ocfPublicationService);
  }

  @Test
  void aWrongTokenIsRefused() throws Exception {
    mockMvc
        .perform(
            post("/admin/ocf-publications/calculation")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "month": "2026-10"}
                    """))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(ocfCalculationDecisionService);
  }
}
