package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.config.ScheduledTest;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(SettlementCheckJob.class)
@ActiveProfiles("production")
class SettlementCheckJobScheduledTest {

  @MockitoBean Clock clock;
  @MockitoBean PublicHolidays publicHolidays;
  @MockitoBean TransactionOrderRepository orderRepository;
  @MockitoBean TransactionExecutionRepository executionRepository;
  @MockitoBean InvestmentReportService reportService;
  @MockitoBean SebPendingTransactionExtractor extractor;
  @MockitoBean UnmatchedPendingTransactionFinder unmatchedFinder;
  @MockitoBean SebClientNameToFundResolver fundResolver;
  @MockitoBean OperationsNotificationService notificationService;

  @Test
  void cronExpressionsResolve() {}
}
