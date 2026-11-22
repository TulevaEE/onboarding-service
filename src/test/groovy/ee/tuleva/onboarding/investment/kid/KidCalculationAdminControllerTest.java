package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.ScenarioKind.MODERATE;
import static ee.tuleva.onboarding.investment.kid.TransactionCostSource.PUBLISHED_OCF;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KidCalculationAdminController.class)
@Import(AdminTokenValidator.class)
@TestPropertySource(properties = {"admin.api-token=admin-token", "admin.ops-token=ops-token"})
@WithMockUser
class KidCalculationAdminControllerTest {

  private static final YearMonth JANUARY = YearMonth.of(2026, 1);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private KidCalculationService kidCalculationService;
  @MockitoBean private KidCalculationReader kidCalculationReader;

  @Test
  void storesTheMonthsCalculationForTheOpsTokenAndAnswersWithWhatWasStored() throws Exception {
    given(kidCalculationService.calculateAndStore(TKF100, JANUARY))
        .willReturn(
            new StoredKidCalculation(
                7,
                TKF100,
                JANUARY,
                1,
                List.of(),
                "MSCI_ACWI",
                LocalDate.of(2026, 1, 30),
                new OcfBasis(
                    3,
                    LocalDate.of(2026, 1, 12),
                    new BigDecimal("0.00500000"),
                    new OcfBasis.CalculatedFrom(11, YearMonth.of(2025, 12))),
                PUBLISHED_OCF,
                Instant.parse("2026-12-01T08:30:00Z")));

    mockMvc
        .perform(
            post("/admin/kid/TKF100/calculations")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("month", "2026-01"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {
                      "id": 7,
                      "fund": "TKF100",
                      "month": "2026-01",
                      "version": 1,
                      "complete": true,
                      "refusalReasons": [],
                      "seriesSegments": "MSCI_ACWI",
                      "lastPriceDate": "2026-01-30",
                      "ocf": {
                        "publicationId": 3,
                        "validFrom": "2026-01-12",
                        "ongoingCharges": 0.00500000,
                        "calculatedFrom": {"snapshotId": 11, "month": "2025-12"},
                        "rateSource": "CALCULATION_UNROUNDED"
                      },
                      "transactionCostSource": "PUBLISHED_OCF",
                      "calculatedAt": "2026-12-01T08:30:00Z"
                    }
                    """,
                    STRICT));
  }

  @Test
  void answersARefusalWithItsReasonsAndNoNumbers() throws Exception {
    given(kidCalculationService.calculateAndStore(TKF100, JANUARY))
        .willReturn(
            new StoredKidCalculation(
                8,
                TKF100,
                JANUARY,
                2,
                List.of("No OCF in force at the month end: fund=TKF100, monthEnd=2026-01-31"),
                null,
                null,
                null,
                PUBLISHED_OCF,
                Instant.parse("2026-12-01T08:30:00Z")));

    mockMvc
        .perform(
            post("/admin/kid/TKF100/calculations")
                .with(csrf())
                .header("X-Admin-Token", "admin-token")
                .param("month", "2026-01"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {
                      "id": 8,
                      "fund": "TKF100",
                      "month": "2026-01",
                      "version": 2,
                      "complete": false,
                      "refusalReasons": [
                        "No OCF in force at the month end: fund=TKF100, monthEnd=2026-01-31"
                      ],
                      "seriesSegments": null,
                      "lastPriceDate": null,
                      "ocf": null,
                      "transactionCostSource": "PUBLISHED_OCF",
                      "calculatedAt": "2026-12-01T08:30:00Z"
                    }
                    """,
                    STRICT));
  }

  @Test
  void rejectsAnUnknownTokenWithoutCalculating() throws Exception {
    mockMvc
        .perform(
            post("/admin/kid/TKF100/calculations")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("month", "2026-01"))
        .andExpect(status().isUnauthorized());

    verify(kidCalculationService, never()).calculateAndStore(any(), any());
  }

  @Test
  void reportsAMonthOrFundThatCannotBeCalculatedAsABadRequest() throws Exception {
    willThrow(new InvalidKidRequestException("Month not ended yet: month=2026-12"))
        .given(kidCalculationService)
        .calculateAndStore(TKF100, YearMonth.of(2026, 12));

    mockMvc
        .perform(
            post("/admin/kid/TKF100/calculations")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("month", "2026-12"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void reportsAVersionTakenByACalculationRunningAtTheSameTimeAsAConflictToRetry() throws Exception {
    willThrow(new DuplicateKeyException("investment_kid_calculation_fund_month_version_key"))
        .given(kidCalculationService)
        .calculateAndStore(TKF100, JANUARY);

    mockMvc
        .perform(
            post("/admin/kid/TKF100/calculations")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("month", "2026-01"))
        .andExpect(status().isConflict());
  }

  @Test
  void readsEachMonthsLatestCalculationWithItsPrintedFiguresAndItsPublication() throws Exception {
    given(kidCalculationReader.read(TKF100, Optional.of(JANUARY), Optional.empty()))
        .willReturn(
            Optional.of(
                List.of(
                    new KidMonthCalculation(
                        JANUARY,
                        42,
                        2,
                        Instant.parse("2026-12-01T08:30:00Z"),
                        List.of(),
                        new OcfBasis(
                            3, LocalDate.of(2026, 1, 12), new BigDecimal("0.00500000"), null),
                        List.of(
                            new PrintedScenario(
                                5, MODERATE, new BigDecimal("11200"), new BigDecimal("0.0230"))),
                        List.of(
                            new PrintedCost(
                                5,
                                new BigDecimal("270"),
                                new BigDecimal("0.0051"),
                                new BigDecimal("0.0281"),
                                new BigDecimal("0.0230"))),
                        new KidPublication(
                            TKF100,
                            JANUARY,
                            41,
                            "portfolio-manager",
                            Instant.parse("2026-12-02T10:00:00Z"))))));

    mockMvc
        .perform(
            get("/admin/kid/TKF100/calculations")
                .header("X-Admin-Token", "ops-token")
                .param("from", "2026-01"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    [
                      {
                        "month": "2026-01",
                        "calculationId": 42,
                        "version": 2,
                        "calculatedAt": "2026-12-01T08:30:00Z",
                        "complete": true,
                        "refusalReasons": [],
                        "ocf": {
                          "publicationId": 3,
                          "validFrom": "2026-01-12",
                          "ongoingCharges": 0.00500000,
                          "calculatedFrom": null,
                          "rateSource": "PUBLISHED_FIGURE"
                        },
                        "scenarios": [
                          {"horizonYears": 5, "scenario": "MODERATE",
                           "amountEur": 11200, "averageReturnPerYear": 0.0230}
                        ],
                        "costs": [
                          {"horizonYears": 5, "totalCostsEur": 270, "annualCostImpact": 0.0051,
                           "returnPerYearBeforeCosts": 0.0281,
                           "returnPerYearAfterCosts": 0.0230}
                        ],
                        "published": {
                          "fund": "TKF100",
                          "month": "2026-01",
                          "calculationId": 41,
                          "publishedBy": "portfolio-manager",
                          "publishedAt": "2026-12-02T10:00:00Z"
                        }
                      }
                    ]
                    """,
                    STRICT));
  }

  @Test
  void answersNotFoundForAFundWithoutAKid() throws Exception {
    given(kidCalculationReader.read(TUK75, Optional.empty(), Optional.empty()))
        .willReturn(Optional.empty());

    mockMvc
        .perform(get("/admin/kid/TUK75/calculations").header("X-Admin-Token", "ops-token"))
        .andExpect(status().isNotFound());
  }

  @Test
  void answersABadRequestForAMonthThatIsNotAMonthOrARangeEndingBeforeItStarts() throws Exception {
    willThrow(new InvalidKidRequestException("Range ends before it starts"))
        .given(kidCalculationReader)
        .read(TKF100, Optional.of(YearMonth.of(2026, 9)), Optional.of(YearMonth.of(2026, 8)));

    mockMvc
        .perform(
            get("/admin/kid/TKF100/calculations")
                .header("X-Admin-Token", "ops-token")
                .param("from", "2026-13"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/admin/kid/TKF100/calculations")
                .header("X-Admin-Token", "ops-token")
                .param("from", "2026-09")
                .param("through", "2026-08"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsAnUnknownTokenWithoutReading() throws Exception {
    mockMvc
        .perform(get("/admin/kid/TKF100/calculations").header("X-Admin-Token", "wrong-token"))
        .andExpect(status().isUnauthorized());

    verify(kidCalculationReader, never()).read(any(), any(), any());
  }
}
