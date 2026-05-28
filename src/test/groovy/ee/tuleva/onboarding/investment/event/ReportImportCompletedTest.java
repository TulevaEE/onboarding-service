package ee.tuleva.onboarding.investment.event;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

class ReportImportCompletedTest {

  @Test
  void carriesProviderReportTypeDateAndRowCount() {
    ReportImportCompleted event =
        new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, LocalDate.of(2026, 5, 11), 3);

    assertThat(event.provider()).isEqualTo(SEB);
    assertThat(event.reportType()).isEqualTo(PENDING_TRANSACTIONS);
    assertThat(event.reportDate()).isEqualTo(LocalDate.of(2026, 5, 11));
    assertThat(event.importedRowCount()).isEqualTo(3);
  }

  @Test
  void publishesAndReceivesPayloadThroughApplicationEventPublisher() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(TestConfig.class)) {
      TestListener listener = context.getBean(TestListener.class);

      ReportImportCompleted seb =
          new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, LocalDate.of(2026, 5, 11), 3);
      ReportImportCompleted positions =
          new ReportImportCompleted(SEB, POSITIONS, LocalDate.of(2026, 5, 11), 5);

      context.publishEvent(seb);
      context.publishEvent(positions);

      assertThat(listener.received).containsExactly(seb, positions);
    }
  }

  @Configuration
  static class TestConfig {
    @Bean
    TestListener testListener() {
      return new TestListener();
    }
  }

  static class TestListener {
    final List<ReportImportCompleted> received = new ArrayList<>();

    @EventListener
    public void on(ReportImportCompleted event) {
      received.add(event);
    }
  }
}
