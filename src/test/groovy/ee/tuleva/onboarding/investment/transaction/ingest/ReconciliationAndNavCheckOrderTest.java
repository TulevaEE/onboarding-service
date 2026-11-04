package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.ApplicationListenerMethodAdapter;

/**
 * Pinning test: the M2 reconciliation listener must be annotated with a lower @Order than the M4
 * NAV-check listener so Spring invokes them in the correct sequence (M2 writes the execution row,
 * M4 reads it).
 */
class ReconciliationAndNavCheckOrderTest {

  @Test
  void reconciliationListener_runsBeforeNavCheckListener() throws Exception {
    var reconciliation =
        new ApplicationListenerMethodAdapter(
            "reconciliation",
            SebPendingTransactionReconciliationListener.class,
            SebPendingTransactionReconciliationListener.class.getMethod(
                "onReportImportCompleted", ReportImportCompleted.class));
    var navCheck =
        new ApplicationListenerMethodAdapter(
            "navCheck",
            SebPriceVsNavCheckListener.class,
            SebPriceVsNavCheckListener.class.getMethod(
                "onReportImportCompleted", ReportImportCompleted.class));

    assertThat(reconciliation.getOrder())
        .as("M2 reconciliation must run before M4 NAV check")
        .isLessThan(navCheck.getOrder());
  }
}
