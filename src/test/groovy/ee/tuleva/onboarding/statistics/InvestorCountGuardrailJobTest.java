package ee.tuleva.onboarding.statistics;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestorCountGuardrailJobTest {

  @Mock private InvestorStatisticsRepository investorStatisticsRepository;
  @Mock private InvestorCountGuardrail investorCountGuardrail;
  @Mock private OperationsNotificationService notificationService;
  @InjectMocks private InvestorCountGuardrailJob job;

  @Test
  void doesNotAlert_whenNoViolations() {
    given(investorStatisticsRepository.getActiveInvestorCount()).willReturn(85224L);
    given(investorStatisticsRepository.getPreviousActiveInvestorCount())
        .willReturn(OptionalLong.of(85000L));
    given(investorCountGuardrail.findViolations(85224L, OptionalLong.of(85000L)))
        .willReturn(List.of());

    job.checkInvestorCount();

    verify(notificationService, never()).sendMessage(any(), any());
  }

  @Test
  void alertsToSavingsChannel_whenViolations() {
    given(investorStatisticsRepository.getActiveInvestorCount()).willReturn(120000L);
    given(investorStatisticsRepository.getPreviousActiveInvestorCount())
        .willReturn(OptionalLong.of(85000L));
    given(investorCountGuardrail.findViolations(120000L, OptionalLong.of(85000L)))
        .willReturn(List.of("count out of expected bounds: count=120000, expected=[80000, 95000]"));

    job.checkInvestorCount();

    verify(notificationService)
        .sendMessage(contains("Investor count guardrail failed"), eq(SAVINGS));
  }
}
