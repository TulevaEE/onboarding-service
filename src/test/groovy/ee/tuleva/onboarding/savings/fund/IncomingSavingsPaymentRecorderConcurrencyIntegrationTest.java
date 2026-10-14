package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static java.util.Collections.nCopies;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.payment.IncomingSavingsPayment;
import ee.tuleva.onboarding.payment.SavingsPayments;
import ee.tuleva.onboarding.savings.SavingFundPaymentQueries;
import ee.tuleva.onboarding.time.ClockConfig;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({
  IncomingSavingsPaymentRecorder.class,
  SavingFundPaymentQueries.class,
  SavingFundPaymentRepository.class,
  ClockConfig.class
})
@Transactional(propagation = NOT_SUPPORTED)
class IncomingSavingsPaymentRecorderConcurrencyIntegrationTest {

  private static final int PAYMENT_LINKS = 20;
  private static final int CALLBACKS_PER_LINK = 2;
  private static final PartyId RECIPIENT = new PartyId(PERSON, "38888888888");

  @Autowired SavingsPayments savingsPayments;
  @Autowired NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired DataSource dataSource;

  private final List<String> descriptions =
      IntStream.range(0, PAYMENT_LINKS)
          .mapToObj(link -> "38888888888, " + UUID.randomUUID())
          .toList();

  @AfterEach
  void deletePayments() {
    jdbcTemplate.update(
        "delete from saving_fund_payment where description in (:descriptions)",
        Map.of("descriptions", descriptions));
  }

  @Test
  void theWebhookAndTheReturnRedirectForOnePaidLinkArrivingTogetherRecordOnePayment()
      throws Exception {
    assumeTrue(isPostgres(), "The callback race only reproduces on PostgreSQL");

    var recordedPerLink = new ArrayList<Long>();
    for (var description : descriptions) {
      recordedPerLink.add(recordAtOnce(paidLink(description)));
    }

    assertThat(recordedPerLink).isEqualTo(nCopies(PAYMENT_LINKS, 1L));
    assertThat(descriptions.stream().map(this::paymentCount).toList())
        .isEqualTo(nCopies(PAYMENT_LINKS, 1L));
  }

  private IncomingSavingsPayment paidLink(String description) {
    return new IncomingSavingsPayment(
        UUID.randomUUID(),
        "First Last",
        "EE111111111111111111",
        description,
        new BigDecimal("1000.00"),
        EUR,
        RECIPIENT);
  }

  private long recordAtOnce(IncomingSavingsPayment payment) throws Exception {
    var barrier = new CyclicBarrier(CALLBACKS_PER_LINK);
    List<Throwable> errors = new CopyOnWriteArrayList<>();
    Callable<Boolean> callback =
        () -> {
          try {
            barrier.await();
            return savingsPayments.recordIncoming(payment);
          } catch (Throwable t) {
            errors.add(t);
            return false;
          }
        };
    try (var pool = Executors.newFixedThreadPool(CALLBACKS_PER_LINK)) {
      List<Future<Boolean>> results = pool.invokeAll(nCopies(CALLBACKS_PER_LINK, callback));
      var recorded = 0L;
      for (var result : results) {
        if (result.get(30, SECONDS)) {
          recorded++;
        }
      }
      assertThat(errors).isEmpty();
      return recorded;
    }
  }

  private long paymentCount(String description) {
    return jdbcTemplate.queryForObject(
        "select count(*) from saving_fund_payment where description = :description",
        Map.of("description", description),
        Long.class);
  }

  private boolean isPostgres() throws SQLException {
    try (var connection = dataSource.getConnection()) {
      return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
    }
  }
}
