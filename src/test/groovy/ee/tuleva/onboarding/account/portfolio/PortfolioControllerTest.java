package ee.tuleva.onboarding.account.portfolio;

import static ee.tuleva.onboarding.account.portfolio.PortfolioGroup.SAVINGS_FUND;
import static ee.tuleva.onboarding.account.portfolio.PortfolioGroup.SECOND_PILLAR;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static java.time.ZoneOffset.UTC;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PortfolioController.class)
@AutoConfigureMockMvc
class PortfolioControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private PortfolioService portfolioService;

  @MockitoBean private Clock clock;

  private final AuthenticatedPerson authPerson = sampleAuthenticatedPersonNonMember().build();
  private final Authentication authentication =
      new UsernamePasswordAuthenticationToken(
          authPerson, null, List.of(new SimpleGrantedAuthority(USER)));

  @Test
  void returnsTheValuedPortfolioForTheRequestedPeriod() throws Exception {
    LocalDate from = LocalDate.parse("2025-01-01");
    LocalDate to = LocalDate.parse("2025-12-31");

    givenToday("2026-01-01");
    when(portfolioService.getPortfolio(eq(authPerson), eq(from), eq(to)))
        .thenReturn(portfolio(from, to));

    mvc.perform(
            get("/v1/portfolio")
                .param("from", "2025-01-01")
                .param("to", "2025-12-31")
                .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2025-01-01"))
        .andExpect(jsonPath("$.to").value("2025-12-31"))
        .andExpect(jsonPath("$.groups[0].group").value("SAVINGS_FUND"))
        .andExpect(jsonPath("$.groups[0].endValue").value(1800.00))
        .andExpect(jsonPath("$.groups[0].annualReturnRate").value(0.0712))
        .andExpect(jsonPath("$.series[0].date").value("2025-12-31"))
        .andExpect(jsonPath("$.series[0].values.SAVINGS_FUND").value(1800.00))
        .andExpect(jsonPath("$.series[0].values.SECOND_PILLAR").doesNotExist());
  }

  @Test
  void leavesTheStartOfAnAllTimePeriodToTheServiceAndEndsItToday() throws Exception {
    LocalDate firstHolding = LocalDate.parse("2019-03-05");
    LocalDate to = LocalDate.parse("2020-01-01");

    givenToday("2020-01-01");
    when(portfolioService.getPortfolio(eq(authPerson), eq(null), eq(to)))
        .thenReturn(portfolio(firstHolding, to));

    mvc.perform(get("/v1/portfolio").with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2019-03-05"))
        .andExpect(jsonPath("$.to").value("2020-01-01"));
  }

  @Test
  void rejectsAPeriodWhereFromIsAfterTo() throws Exception {
    givenToday("2026-01-01");

    mvc.perform(
            get("/v1/portfolio")
                .param("from", "2025-12-31")
                .param("to", "2025-01-01")
                .with(authentication(authentication)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(portfolioService);
  }

  @Test
  void rejectsAPeriodThatStartsAfterToday() throws Exception {
    givenToday("2026-01-01");

    mvc.perform(
            get("/v1/portfolio")
                .param("from", "2026-01-02")
                .param("to", "2026-02-01")
                .with(authentication(authentication)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(portfolioService);
  }

  @Test
  void acceptsAPeriodThatStartsToday() throws Exception {
    LocalDate from = LocalDate.parse("2026-01-01");
    LocalDate to = LocalDate.parse("2026-02-01");

    givenToday("2026-01-01");
    when(portfolioService.getPortfolio(eq(authPerson), eq(from), eq(to)))
        .thenReturn(portfolio(from, to));

    mvc.perform(
            get("/v1/portfolio")
                .param("from", "2026-01-01")
                .param("to", "2026-02-01")
                .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2026-01-01"))
        .andExpect(jsonPath("$.to").value("2026-02-01"));
  }

  private void givenToday(String today) {
    when(clock.instant()).thenReturn(LocalDate.parse(today).atStartOfDay(UTC).toInstant());
    when(clock.getZone()).thenReturn(UTC);
  }

  private static Portfolio portfolio(LocalDate from, LocalDate to) {
    Map<PortfolioGroup, BigDecimal> values = new HashMap<>();
    values.put(SAVINGS_FUND, new BigDecimal("1800.00"));
    values.put(SECOND_PILLAR, null);

    return Portfolio.builder()
        .from(from)
        .to(to)
        .groups(
            List.of(
                Portfolio.GroupSummary.builder()
                    .group(SAVINGS_FUND)
                    .startValue(new BigDecimal("1000.00"))
                    .endValue(new BigDecimal("1800.00"))
                    .contributions(new BigDecimal("550.00"))
                    .withdrawals(new BigDecimal("0.00"))
                    .gain(new BigDecimal("250.00"))
                    .gainPercentage(new BigDecimal("16.13"))
                    .annualReturnRate(new BigDecimal("0.0712"))
                    .build()))
        .series(List.of(new Portfolio.ValuePoint(to, values)))
        .build();
  }
}
