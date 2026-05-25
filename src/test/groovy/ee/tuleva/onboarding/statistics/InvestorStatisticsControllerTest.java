package ee.tuleva.onboarding.statistics;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvestorStatisticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class InvestorStatisticsControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private InvestorStatisticsRepository investorStatisticsRepository;

  @Test
  void getInvestorCount_returnsActiveInvestorCount() throws Exception {
    given(investorStatisticsRepository.getActiveInvestorCount()).willReturn(85224L);

    mvc.perform(get("/v1/statistics/investor-count"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(85224));
  }
}
