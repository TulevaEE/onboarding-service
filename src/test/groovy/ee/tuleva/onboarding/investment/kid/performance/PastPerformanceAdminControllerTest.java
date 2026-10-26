package ee.tuleva.onboarding.investment.kid.performance;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PastPerformanceAdminController.class)
@Import(AdminTokenValidator.class)
@TestPropertySource(properties = {"admin.api-token=admin-token", "admin.ops-token=ops-token"})
@WithMockUser
class PastPerformanceAdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PastPerformanceService pastPerformanceService;

  @Test
  void servesThePastPerformanceToTheOpsTokenTheAgentGatewayHolds() throws Exception {
    given(pastPerformanceService.pastPerformance(TKF100))
        .willReturn(
            new PastPerformance(
                TKF100,
                "EE0000003283",
                "MSCI_ACWI",
                null,
                LocalDate.of(2026, 10, 21),
                false,
                List.of(
                    new YearReturn(
                        2026,
                        new BigDecimal("0.01731000"),
                        new BigDecimal("0.01282657"),
                        true,
                        true))));

    mockMvc
        .perform(get("/admin/kid/TKF100/past-performance").header("X-Admin-Token", "ops-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enoughHistory").value(false))
        .andExpect(jsonPath("$.asOf").value("2026-10-21"))
        .andExpect(jsonPath("$.years[0].year").value(2026))
        .andExpect(jsonPath("$.years[0].fundReturn").value(0.01731))
        .andExpect(jsonPath("$.years[0].partialYear").value(true))
        .andExpect(jsonPath("$.years[0].yearToDate").value(true));
  }

  @Test
  void rejectsAnUnknownToken() throws Exception {
    mockMvc
        .perform(get("/admin/kid/TKF100/past-performance").header("X-Admin-Token", "wrong"))
        .andExpect(status().isUnauthorized());

    verify(pastPerformanceService, never()).pastPerformance(any());
  }

  @Test
  void reportsAFundWithoutNavsAsAConflictTheGatewayRelays() throws Exception {
    willThrow(new PastPerformanceRefusedException("No NAV"))
        .given(pastPerformanceService)
        .pastPerformance(TKF100);

    mockMvc
        .perform(get("/admin/kid/TKF100/past-performance").header("X-Admin-Token", "ops-token"))
        .andExpect(status().isConflict());
  }
}
