package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.ScenarioKind.MODERATE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.STRESS;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KidScenarioHistoryController.class)
@WithMockUser
class KidScenarioHistoryControllerTest {

  private static final String ISIN = "EE0000003283";
  private static final List<PublishedScenario> HISTORY =
      List.of(
          new PublishedScenario(
              YearMonth.of(2026, 10), 5, STRESS, new BigDecimal("5290"), new BigDecimal("-0.1196")),
          new PublishedScenario(
              YearMonth.of(2026, 9),
              5,
              MODERATE,
              new BigDecimal("11480"),
              new BigDecimal("0.0281")));

  @Autowired private MockMvc mockMvc;

  @MockitoBean private KidPublicationService kidPublicationService;

  @Test
  void servesThePublishedScenarioHistoryAsJsonWithoutCaching() throws Exception {
    given(kidPublicationService.publishedScenarioHistory(ISIN)).willReturn(Optional.of(HISTORY));

    mockMvc
        .perform(get("/v1/funds/{isin}/kid-scenarios", ISIN))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            content()
                .json(
                    """
                    [
                      {"month": "2026-10", "horizonYears": 5, "scenario": "STRESS",
                       "amountEur": 5290, "averageReturnPerYear": -0.1196},
                      {"month": "2026-09", "horizonYears": 5, "scenario": "MODERATE",
                       "amountEur": 11480, "averageReturnPerYear": 0.0281}
                    ]
                    """,
                    STRICT));
  }

  @Test
  void servesItAsACsvDownloadLikeTheNavHistory() throws Exception {
    given(kidPublicationService.publishedScenarioHistory(ISIN)).willReturn(Optional.of(HISTORY));

    mockMvc
        .perform(get("/v1/funds/{isin}/kid-scenarios", ISIN).param("format", "csv"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    "attachment; filename=\"kid-scenarios-" + ISIN + ".csv\""))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().bytes(new KidScenarioHistoryCsv(HISTORY).toBytes()));
  }

  @Test
  void answersNotFoundForAFundWithoutAKid() throws Exception {
    given(kidPublicationService.publishedScenarioHistory("EE3600109435"))
        .willReturn(Optional.empty());

    mockMvc
        .perform(get("/v1/funds/{isin}/kid-scenarios", "EE3600109435"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Cache-Control", "no-store"));
    mockMvc
        .perform(get("/v1/funds/{isin}/kid-scenarios", "EE3600109435").param("format", "csv"))
        .andExpect(status().isNotFound());
  }
}
