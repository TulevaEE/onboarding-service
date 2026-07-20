package ee.tuleva.onboarding.comparisons.benchmark;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorldMarketBenchmarkIntegrationTest {

  private static final Instant UPDATED_AT = Instant.parse("2026-07-15T12:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private FundValueRepository fundValueRepository;

  @BeforeEach
  void seedIndexValues() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-16T10:00:00Z"), ZoneOffset.UTC));
    fundValueRepository.saveAll(
        List.of(
            acwi("2016-07-15", "80"),
            acwi("2019-09-02", "90"),
            acwi("2021-07-15", "100"),
            acwi("2026-07-15", "200"),
            bond("2016-07-15", "50"),
            bond("2019-09-02", "55")));
  }

  @AfterEach
  void resetClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void returnsAnnualizedWorldMarketReturnsForComputableWindowsAnonymously() throws Exception {
    mockMvc
        .perform(get("/v1/benchmarks/world-market/returns"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "max-age=3600, public"))
        .andExpect(jsonPath("$.returns", hasSize(2)))
        .andExpect(jsonPath("$.returns[0].years", is(5)))
        .andExpect(jsonPath("$.returns[0].annualizedReturn", is(0.14872)))
        .andExpect(jsonPath("$.returns[0].composite", is(false)))
        .andExpect(jsonPath("$.returns[0].fromDate", is("2021-07-15")))
        .andExpect(jsonPath("$.returns[0].toDate", is("2026-07-15")))
        .andExpect(jsonPath("$.returns[1].years", is(10)))
        .andExpect(jsonPath("$.returns[1].annualizedReturn", is(0.095361)))
        .andExpect(jsonPath("$.returns[1].composite", is(true)))
        .andExpect(jsonPath("$.returns[1].fromDate", is("2016-07-15")))
        .andExpect(jsonPath("$.returns[1].toDate", is("2026-07-15")));
  }

  private FundValue acwi(String date, String value) {
    return new FundValue(
        "MSCI_ACWI", LocalDate.parse(date), new BigDecimal(value), "MSCI", UPDATED_AT);
  }

  private FundValue bond(String date, String value) {
    return new FundValue(
        "EURO_AGGREGATE_BOND", LocalDate.parse(date), new BigDecimal(value), "YAHOO", UPDATED_AT);
  }
}
