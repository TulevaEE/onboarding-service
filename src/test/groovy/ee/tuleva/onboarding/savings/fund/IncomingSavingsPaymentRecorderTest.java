package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.payment.IncomingSavingsPayment;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.SavingFundPaymentQueries;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncomingSavingsPaymentRecorderTest {

  private final SavingFundPaymentQueries savingFundPaymentQueries =
      mock(SavingFundPaymentQueries.class);

  private final IncomingSavingsPaymentRecorder recorder =
      new IncomingSavingsPaymentRecorder(savingFundPaymentQueries);

  private final PartyId recipient = new PartyId(PERSON, "38812121215");

  private final IncomingSavingsPayment incomingPayment =
      new IncomingSavingsPayment(
          "First Last",
          "EE111111111111111111",
          "description",
          new BigDecimal("10"),
          EUR,
          recipient);

  @Test
  void savesAndAttachesPartyWhenNoRecentPaymentExists() {
    given(savingFundPaymentQueries.findRecentPayments("description")).willReturn(List.of());
    var paymentId = UUID.randomUUID();
    var expectedPayment =
        SavingFundPayment.builder()
            .remitterName("First Last")
            .remitterIban("EE111111111111111111")
            .description("description")
            .amount(new BigDecimal("10"))
            .currency(EUR)
            .build();
    given(savingFundPaymentQueries.savePaymentData(expectedPayment)).willReturn(paymentId);

    var recorded = recorder.recordIncoming(incomingPayment);

    assertThat(recorded).isTrue();
    verify(savingFundPaymentQueries).attachParty(paymentId, recipient);
  }

  @Test
  void doesNotSaveWhenARecentPaymentAlreadyExists() {
    var existingPayment = SavingFundPayment.builder().description("description").build();
    given(savingFundPaymentQueries.findRecentPayments("description"))
        .willReturn(List.of(existingPayment));

    var recorded = recorder.recordIncoming(incomingPayment);

    assertThat(recorded).isFalse();
    verify(savingFundPaymentQueries, never()).savePaymentData(any());
    verify(savingFundPaymentQueries, never()).attachParty(any(), any());
  }
}
