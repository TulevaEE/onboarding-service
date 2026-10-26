package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.ScenarioKind.UNFAVOURABLE;
import static ee.tuleva.onboarding.investment.kid.WindowSource.SHORTER;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
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

@WebMvcTest(KidAdminController.class)
@Import(AdminTokenValidator.class)
@TestPropertySource(properties = {"admin.api-token=admin-token", "admin.ops-token=ops-token"})
@WithMockUser
class KidAdminControllerTest {

  private static final BigDecimal ONGOING_CHARGES = new BigDecimal("0.005");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private KidFiguresService kidFiguresService;

  @Test
  void servesTheFiguresToTheOpsTokenTheAgentGatewayHolds() throws Exception {
    given(kidFiguresService.figures(TKF100, ONGOING_CHARGES)).willReturn(figures());

    mockMvc
        .perform(
            get("/admin/kid/TKF100")
                .header("X-Admin-Token", "ops-token")
                .param("ongoingCharges", "0.005"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fund").value("TKF100"))
        .andExpect(jsonPath("$.periodEnd").value("2026-09-30"))
        .andExpect(jsonPath("$.ongoingCharges").value(0.005))
        .andExpect(jsonPath("$.scenarios[0].kind").value("UNFAVOURABLE"))
        .andExpect(jsonPath("$.scenarios[0].amountEur").value(8620))
        .andExpect(jsonPath("$.scenarios[0].periodStart").value("2025-09-30"))
        .andExpect(jsonPath("$.scenarios[0].window").value("SHORTER"))
        .andExpect(jsonPath("$.costs[0].totalCostsEur").value(50));
  }

  @Test
  void servesTheFiguresToTheAdminTokenToo() throws Exception {
    given(kidFiguresService.figures(TKF100, ONGOING_CHARGES)).willReturn(figures());

    mockMvc
        .perform(
            get("/admin/kid/TKF100")
                .header("X-Admin-Token", "admin-token")
                .param("ongoingCharges", "0.005"))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsAnUnknownToken() throws Exception {
    mockMvc
        .perform(
            get("/admin/kid/TKF100")
                .header("X-Admin-Token", "wrong-token")
                .param("ongoingCharges", "0.005"))
        .andExpect(status().isUnauthorized());

    verify(kidFiguresService, never()).figures(any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"-0.0001", "0.05", "0.28", "0.99999999999999999"})
  void rejectsOngoingChargesThatAreNegativeOrLookLikeAPercentTypedAsAFraction(String charges)
      throws Exception {
    mockMvc
        .perform(
            get("/admin/kid/TKF100")
                .header("X-Admin-Token", "ops-token")
                .param("ongoingCharges", charges))
        .andExpect(status().isBadRequest());

    verify(kidFiguresService, never()).figures(any(), any());
  }

  @Test
  void acceptsOngoingChargesOfZero() throws Exception {
    given(kidFiguresService.figures(TKF100, BigDecimal.ZERO)).willReturn(figures());

    mockMvc
        .perform(
            get("/admin/kid/TKF100")
                .header("X-Admin-Token", "ops-token")
                .param("ongoingCharges", "0"))
        .andExpect(status().isOk());
  }

  @Test
  void reportsARefusedCalculationAsAConflictTheGatewayRelays() throws Exception {
    willThrow(new KidCalculationRefusedException("Month-end close missing or stale"))
        .given(kidFiguresService)
        .figures(TKF100, ONGOING_CHARGES);

    mockMvc
        .perform(
            get("/admin/kid/TKF100")
                .header("X-Admin-Token", "ops-token")
                .param("ongoingCharges", "0.005"))
        .andExpect(status().isConflict());
  }

  private static KidFigures figures() {
    return new KidFigures(
        TKF100,
        "MSCI_ACWI",
        LocalDate.of(2016, 9, 30),
        LocalDate.of(2026, 9, 30),
        ONGOING_CHARGES,
        5,
        new BigDecimal("10000"),
        List.of(
            new KidScenario(
                5,
                UNFAVOURABLE,
                new BigDecimal("8620"),
                new BigDecimal("-0.02929577"),
                LocalDate.of(2025, 9, 30),
                LocalDate.of(2026, 9, 30),
                SHORTER,
                12)),
        List.of(
            new KidCost(
                1,
                new BigDecimal("50"),
                new BigDecimal("0.005"),
                new BigDecimal("0.005"),
                new BigDecimal("0.000"))));
  }
}
