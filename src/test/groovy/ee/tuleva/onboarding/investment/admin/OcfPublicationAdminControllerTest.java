package ee.tuleva.onboarding.investment.admin;

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
import ee.tuleva.onboarding.investment.fees.ocf.KidFigure;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationPreview;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationRefusedException;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationRequest;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationService;
import java.math.BigDecimal;
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

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OcfPublicationService ocfPublicationService;

  private static final OcfPublicationPreview PREVIEW =
      new OcfPublicationPreview(
          "TUK75",
          OCTOBER,
          2,
          true,
          "{\"gaps\":[]}",
          A_FINGERPRINT,
          new BigDecimal("0.00280000"),
          new BigDecimal("0.00200000"),
          NOVEMBER_1,
          new BigDecimal("0.00190000"),
          new BigDecimal("0.00270000"),
          new BigDecimal("0.27"),
          new KidFigure(
              LocalDate.of(2026, 2, 1),
              1,
              LocalDate.of(2026, 3, 1),
              new BigDecimal("0.00280000"),
              "KID 02.03.2026"),
          false);

  private static final String PUBLISH_BODY =
      """
      {"fund": "TUK75", "month": "2026-10", "version": 2, "validFrom": "2026-11-01",
       "calculationFingerprint": "%s",
       "publishedOcf": 0.00270000, "publishedIn": "KID 01.11.2026",
       "publishedBy": "portfolio-manager@example.com"}
      """
          .formatted(A_FINGERPRINT);

  @Test
  void previewWithTheGatewaysOpsTokenShowsTheFigureThatWouldBePublished() throws Exception {
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
        .andExpect(jsonPath("$.publishedOcfPercent").value(0.27))
        .andExpect(jsonPath("$.kidInForceBefore.publishedIn").value("KID 02.03.2026"));
  }

  @Test
  void publishWithTheGatewaysOpsTokenRecordsThePublication() throws Exception {
    given(ocfPublicationService.publish(any(OcfPublicationRequest.class))).willReturn(PREVIEW);

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
  void aPublicationWithoutWhoPublishedItIsRejectedBeforeTheService() throws Exception {
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
                     "publishedOcf": 0.00270000, "publishedIn": "KID 01.11.2026"}
                    """
                        .formatted(A_FINGERPRINT)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(ocfPublicationService);
  }

  @Test
  void aWrongTokenIsRefused() throws Exception {
    mockMvc
        .perform(
            post("/admin/ocf-publications")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType(APPLICATION_JSON)
                .content(PUBLISH_BODY))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(ocfPublicationService);
  }
}
