package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

/**
 * Pinning test: the M2 reconciliation listener must be annotated with a lower @Order than the M4
 * NAV-check listener so Spring invokes them in the correct sequence (M2 writes the execution row,
 * M4 reads it).
 */
class ReconciliationAndNavCheckOrderTest {

  @Test
  void reconciliationListener_runsBeforeNavCheckListener() {
    Order reconciliationOrder =
        AnnotationUtils.findAnnotation(
            SebPendingTransactionReconciliationListener.class, Order.class);
    Order navCheckOrder =
        AnnotationUtils.findAnnotation(SebPriceVsNavCheckListener.class, Order.class);

    assertThat(reconciliationOrder).as("M2 listener must declare @Order").isNotNull();
    assertThat(navCheckOrder).as("M4 listener must declare @Order").isNotNull();
    assertThat(reconciliationOrder.value())
        .as("M2 reconciliation must run before M4 NAV check")
        .isLessThan(navCheckOrder.value());
  }
}
