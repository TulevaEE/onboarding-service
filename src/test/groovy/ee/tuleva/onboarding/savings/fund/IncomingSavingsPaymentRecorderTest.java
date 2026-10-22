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

  private final UUID internalReference = UUID.randomUUID();

  private final IncomingSavingsPayment incomingPayment =
      new IncomingSavingsPayment(
          internalReference,
          "First Last",
          "EE111111111111111111",
          "description",
          new BigDecimal("10"),
          EUR,
          recipient);

  private final SavingFundPayment expectedPayment =
      SavingFundPayment.builder()
          .remitterName("First Last")
          .remitterIban("EE111111111111111111")
          .description("description")
          .amount(new BigDecimal("10"))
          .currency(EUR)
          .build();

  @Test
  void savesThePaymentWithItsPartyOncePerPaymentLinkWhenNoRecentPaymentExists() {
    given(savingFundPaymentQueries.findRecentPayments("description")).willReturn(List.of());
    given(
            savingFundPaymentQueries.savePaymentDataOnce(
                internalReference, expectedPayment, recipient))
        .willReturn(true);

    var recorded = recorder.recordIncoming(incomingPayment);

    assertThat(recorded).isTrue();
  }

  @Test
  void recordsNothingWhenTheOtherCallbackForTheSamePaymentLinkSavedItFirst() {
    given(savingFundPaymentQueries.findRecentPayments("description")).willReturn(List.of());
    given(
            savingFundPaymentQueries.savePaymentDataOnce(
                internalReference, expectedPayment, recipient))
        .willReturn(false);

    var recorded = recorder.recordIncoming(incomingPayment);

    assertThat(recorded).isFalse();
  }

  @Test
  void doesNotSaveACallbackArrivingAfterTheBankStatementAlreadyRecordedThePayment() {
    var bankStatementPayment =
        SavingFundPayment.builder()
            .description("description")
            .externalId("statement-entry")
            .build();
    given(savingFundPaymentQueries.findRecentPayments("description"))
        .willReturn(List.of(bankStatementPayment));

    var recorded = recorder.recordIncoming(incomingPayment);

    assertThat(recorded).isFalse();
    verify(savingFundPaymentQueries, never()).savePaymentDataOnce(any(), any(), any());
  }
}
