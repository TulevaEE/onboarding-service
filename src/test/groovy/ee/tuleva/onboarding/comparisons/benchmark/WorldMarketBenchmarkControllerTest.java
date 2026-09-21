package ee.tuleva.onboarding.comparisons.benchmark;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.config.SecurityConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WorldMarketBenchmarkController.class)
@Import(SecurityConfiguration.class)
class WorldMarketBenchmarkControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private WorldMarketBenchmarkService worldMarketBenchmarkService;

  // Needed by the imported SecurityConfiguration's JwtAuthorizationFilter bean.
  @MockitoBean private JwtTokenUtil jwtTokenUtil;
  @MockitoBean private PrincipalService principalService;

  @Test
  void servesWorldMarketReturnsAnonymouslyWithPublicCaching() throws Exception {
    given(worldMarketBenchmarkService.getReturns())
        .willReturn(
            List.of(
                new WorldMarketReturn(
                    5,
                    new BigDecimal("0.148720"),
                    LocalDate.parse("2021-07-15"),
                    LocalDate.parse("2026-07-15"),
                    false)));

    mockMvc
        .perform(get("/v1/benchmarks/world-market/returns"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "max-age=3600, public"))
        .andExpect(jsonPath("$.returns", hasSize(1)))
        .andExpect(jsonPath("$.returns[0].years", is(5)))
        .andExpect(jsonPath("$.returns[0].annualizedReturn", is(0.14872)))
        .andExpect(jsonPath("$.returns[0].fromDate", is("2021-07-15")))
        .andExpect(jsonPath("$.returns[0].toDate", is("2026-07-15")))
        .andExpect(jsonPath("$.returns[0].composite", is(false)));
  }
}
