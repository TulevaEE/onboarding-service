package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.EXECUTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutgoingPaymentLookupTest {

  private static final UUID SOURCE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
  private static final String END_TO_END_ID = "11111111222233334444555555555555";

  @Mock OutgoingPaymentRepository outgoingPaymentRepository;

  OutgoingPaymentLookup lookup() {
    return new OutgoingPaymentLookup(outgoingPaymentRepository, new EndToEndIdConverter());
  }

  @Test
  void findStatusForSourceResolvesTheDeterministicEndToEndId() {
    var payment = OutgoingPayment.builder().endToEndId(END_TO_END_ID).status(EXECUTED).build();
    given(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID))
        .willReturn(Optional.of(payment));

    assertThat(lookup().findStatusForSource(SOURCE_ID)).contains(EXECUTED);
  }

  @Test
  void findStatusForSourceIsEmptyWhenNothingWasEverSent() {
    given(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID)).willReturn(Optional.empty());

    assertThat(lookup().findStatusForSource(SOURCE_ID)).isEmpty();
  }
}
