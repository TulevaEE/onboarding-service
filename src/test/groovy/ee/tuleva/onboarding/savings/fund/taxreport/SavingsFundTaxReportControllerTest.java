package ee.tuleva.onboarding.savings.fund.taxreport;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.savings.fund.taxreport.CostBasisMethod.FIFO;
import static ee.tuleva.onboarding.savings.fund.taxreport.CostBasisMethod.WEIGHTED_AVERAGE;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SavingsFundTaxReportController.class)
@AutoConfigureMockMvc
class SavingsFundTaxReportControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private SavingsFundTaxReportService savingsFundTaxReportService;

  private final AuthenticatedPerson authPerson = sampleAuthenticatedPersonNonMember().build();
  private final Authentication authentication =
      new UsernamePasswordAuthenticationToken(
          authPerson, null, List.of(new SimpleGrantedAuthority(USER)));

  @Test
  void returnsRealisedGainsForTheTaxYear() throws Exception {
    when(savingsFundTaxReportService.getTaxReport(eq(authPerson), eq(2025), eq(WEIGHTED_AVERAGE)))
        .thenReturn(report(WEIGHTED_AVERAGE, "58.96"));

    mvc.perform(
            get("/v1/savings-fund/tax-report")
                .param("year", "2025")
                .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.year").value(2025))
        .andExpect(jsonPath("$.method").value("WEIGHTED_AVERAGE"))
        .andExpect(jsonPath("$.totalGain").value(58.96))
        .andExpect(jsonPath("$.redemptions[0].acquisitionCost").value(421.04))
        .andExpect(jsonPath("$.redemptions[0].gain").value(58.96));
  }

  @Test
  void defaultsToTheWeightedAverageMethod() throws Exception {
    when(savingsFundTaxReportService.getTaxReport(eq(authPerson), eq(2025), eq(WEIGHTED_AVERAGE)))
        .thenReturn(report(WEIGHTED_AVERAGE, "58.96"));

    mvc.perform(
            get("/v1/savings-fund/tax-report")
                .param("year", "2025")
                .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.method").value("WEIGHTED_AVERAGE"));
  }

  @Test
  void reportsUnderFifoWhenAsked() throws Exception {
    when(savingsFundTaxReportService.getTaxReport(eq(authPerson), eq(2025), eq(FIFO)))
        .thenReturn(report(FIFO, "80.00"));

    mvc.perform(
            get("/v1/savings-fund/tax-report")
                .param("year", "2025")
                .param("method", "FIFO")
                .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.method").value("FIFO"))
        .andExpect(jsonPath("$.totalGain").value(80.00));
  }

  private static SavingsFundTaxReport report(CostBasisMethod method, String totalGain) {
    return SavingsFundTaxReport.builder()
        .year(2025)
        .method(method)
        .totalGain(new BigDecimal(totalGain))
        .redemptions(
            List.of(
                RealisedGain.builder()
                    .time(Instant.parse("2025-09-10T10:00:00Z"))
                    .units(new BigDecimal("40"))
                    .acquisitionCost(new BigDecimal("421.04"))
                    .proceeds(new BigDecimal("480.00"))
                    .gain(new BigDecimal("58.96"))
                    .build()))
        .build();
  }
}
