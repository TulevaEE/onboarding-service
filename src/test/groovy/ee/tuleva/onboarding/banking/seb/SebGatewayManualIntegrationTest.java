package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankType.SEB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ee.tuleva.onboarding.LoadDotEnv;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankStatementReceived;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayTransactionsRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebEodTransactionsRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.ProcessBankMessagesRequested;
import ee.tuleva.onboarding.banking.payment.PaymentRequest;
import ee.tuleva.onboarding.banking.payment.RequestPaymentEvent;
import ee.tuleva.onboarding.config.TestSchedulerLockConfiguration;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test that exercises the full event-driven SEB banking flow against the real SEB test
 * gateway. This test verifies:
 *
 * <ul>
 *   <li>Event publishing and handling works end-to-end
 *   <li>SebGatewayClient integration with real API
 *   <li>Message persistence and processing
 *   <li>Complete production code path (no mocks)
 * </ul>
 *
 * <p>Run manually with AWS SSM proxy:
 *
 * <pre>
 * # Start proxy (in separate terminal)
 * aws ssm start-session \
 *   --target <INSTACE_ID> \
 *   --document-name AWS-StartPortForwardingSessionToRemoteHost \
 *   --parameters '{"host":["test.api.bgw.baltics.sebgroup.com"],"portNumber":["443"],"localPortNumber":["8443"]}' \
 *   --region eu-central-1 \
 *   --profile <TULEVA_PROFILE>
 *
 * # Run test
 * ./gradlew test --tests "SebGatewayIntegrationTest"
 * </pre>
 */
@SpringBootTest
@LoadDotEnv
@TestPropertySource(
    properties = {
      "seb-gateway.enabled=true",
      "swedbank-gateway.enabled=false",
      "seb-gateway.url=https://localhost:8443",
      "seb-gateway.org-id=${SEB_GATEWAY_ORG_ID}",
      "seb-gateway.keystore.path=${SEB_GATEWAY_KEYSTORE_PATH}",
      "seb-gateway.keystore.password=${SEB_GATEWAY_KEYSTORE_PASSWORD}",
      "seb-gateway.accounts.DEPOSIT_EUR=EE241010220306719221",
      "seb-gateway.accounts.WITHDRAWAL_EUR=EE381010220306717223",
      "seb-gateway.accounts.FUND_INVESTMENT_EUR=EE381010220306717224" // Not provisioned
    })
@Import({TestSchedulerLockConfiguration.class, LocalProxySebTlsStrategyFactory.class})
@RecordApplicationEvents
@Transactional
@Disabled("Run manually - requires AWS SSM proxy to SEB test gateway")
class SebGatewayManualIntegrationTest {

  @Autowired private ApplicationEventPublisher eventPublisher;

  @Autowired private ApplicationEvents applicationEvents;

  @Test
  void fetchAndProcessCurrentDayTransactions() {
    eventPublisher.publishEvent(new FetchSebCurrentDayTransactionsRequested(DEPOSIT_EUR));
    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    List<BankStatementReceived> receivedEvents =
        applicationEvents.stream(BankStatementReceived.class).toList();

    assertThat(receivedEvents)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.bankType()).isEqualTo(SEB);
              assertThat(event.statement()).isNotNull();
              assertThat(event.statement().getEntries()).isNotEmpty();
            });
  }

  @Test
  void fetchAndProcessEodTransactions() {
    eventPublisher.publishEvent(new FetchSebEodTransactionsRequested(DEPOSIT_EUR));
    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    List<BankStatementReceived> receivedEvents =
        applicationEvents.stream(BankStatementReceived.class).toList();

    assertThat(receivedEvents)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.bankType()).isEqualTo(SEB);
              assertThat(event.statement()).isNotNull();
            });
  }

  @Test
  void submitPaymentRequest() {
    var paymentRequest =
        PaymentRequest.builder()
            .remitterName("Tuleva Fondid AS")
            .remitterId("14118923")
            .remitterIban("EE241010220306719221")
            .beneficiaryName("Test Recipient")
            .beneficiaryIban("EE381010220306717223")
            .amount(new BigDecimal("1.00"))
            .description("Integration test payment")
            .ourId("TEST-" + System.currentTimeMillis())
            .endToEndId("E2E-" + System.currentTimeMillis())
            .build();

    assertThatCode(
            () ->
                eventPublisher.publishEvent(
                    new RequestPaymentEvent(paymentRequest, UUID.randomUUID())))
        .doesNotThrowAnyException();
  }
}
