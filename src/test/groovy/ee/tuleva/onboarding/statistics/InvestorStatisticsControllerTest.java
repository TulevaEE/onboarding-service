package ee.tuleva.onboarding.statistics;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.config.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvestorStatisticsController.class)
@Import(SecurityConfiguration.class)
class InvestorStatisticsControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private InvestorStatisticsService investorStatisticsService;

  // Needed by the imported SecurityConfiguration's JwtAuthorizationFilter bean.
  @MockitoBean private JwtTokenUtil jwtTokenUtil;
  @MockitoBean private PrincipalService principalService;

  @Test
  void investorCount_isPubliclyAccessible_andReturnsCount() throws Exception {
    given(investorStatisticsService.getActiveInvestorCount()).willReturn(85224L);

    mvc.perform(get("/v1/statistics/investor-count"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(85224))
        .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
        .andExpect(header().string("Cache-Control", containsString("public")));
  }
}
