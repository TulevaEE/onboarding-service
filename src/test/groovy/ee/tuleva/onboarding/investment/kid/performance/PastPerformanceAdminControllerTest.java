package ee.tuleva.onboarding.investment.kid.performance;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
        .andExpect(
            content()
                .json(
                    """
                    {
                      "fund": "TKF100",
                      "fundKey": "EE0000003283",
                      "benchmarkKey": "MSCI_ACWI",
                      "benchmarkEquityCap": null,
                      "asOf": "2026-10-21",
                      "enoughHistory": false,
                      "years": [
                        {
                          "year": 2026,
                          "fundReturn": 0.01731000,
                          "benchmarkReturn": 0.01282657,
                          "partialYear": true,
                          "yearToDate": true
                        }
                      ]
                    }
                    """,
                    STRICT));
  }

  @Test
  void servesTheEquityCapTheBenchmarkWasHeldAt() throws Exception {
    given(pastPerformanceService.pastPerformance(TUK75))
        .willReturn(
            new PastPerformance(
                TUK75,
                "EE3600109435",
                "MSCI_ACWI",
                new EquityCap(
                    "EURO_AGGREGATE_BOND", new BigDecimal("0.75"), LocalDate.of(2019, 9, 2)),
                LocalDate.of(2026, 10, 21),
                true,
                List.of()));

    mockMvc
        .perform(get("/admin/kid/TUK75/past-performance").header("X-Admin-Token", "ops-token"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {
                      "fund": "TUK75",
                      "fundKey": "EE3600109435",
                      "benchmarkKey": "MSCI_ACWI",
                      "benchmarkEquityCap": {
                        "bondKey": "EURO_AGGREGATE_BOND",
                        "equityWeight": 0.75,
                        "liftedOn": "2019-09-02"
                      },
                      "asOf": "2026-10-21",
                      "enoughHistory": true,
                      "years": []
                    }
                    """,
                    STRICT));
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
