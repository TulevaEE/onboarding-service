package ee.tuleva.onboarding.savings.fund.gift;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.RedirectLink;
import ee.tuleva.onboarding.payment.savings.SavingsPaymentLinkGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GiftPaymentServiceTest {

  private static final String CHILD = "50108120265";

  @Mock GiftLinkService giftLinkService;
  @Mock SavingsPaymentLinkGenerator paymentLinkGenerator;

  @InjectMocks GiftPaymentService service;

  @ParameterizedTest
  // Under a euro, over the Montonio ceiling, and fractions of a cent. The browser enforces the
  // same range, but the browser is not where the rule lives.
  @ValueSource(strings = {"0", "0.99", "15000.01", "100000", "10.005"})
  void anAmountOutsideWhatMontonioWillTakeIsRefused(String amount) {
    given(giftLinkService.findOpenLink("TOKEN")).willReturn(aLink());

    assertThatThrownBy(
            () -> service.startPayment("TOKEN", new GiftPaymentRequest(new BigDecimal(amount), LHV)))
        .isInstanceOf(IllegalArgumentException.class);

    verify(paymentLinkGenerator, never()).getAnonymousPaymentLink(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "100.00", "15000"})
  void aGiftIsAddressedToTheChildTheLinkNames(String amount) {
    given(giftLinkService.findOpenLink("TOKEN")).willReturn(aLink());
    given(paymentLinkGenerator.getAnonymousPaymentLink(any()))
        .willReturn(new RedirectLink("https://montonio.example/pay"));

    var link = service.startPayment("TOKEN", new GiftPaymentRequest(new BigDecimal(amount), LHV));

    assertThat(link.url()).isEqualTo("https://montonio.example/pay");

    var sent = ArgumentCaptor.forClass(PaymentData.class);
    verify(paymentLinkGenerator).getAnonymousPaymentLink(sent.capture());
    assertThat(sent.getValue().getRecipientPersonalCode()).isEqualTo(CHILD);
    assertThat(sent.getValue().getAmount()).isEqualByComparingTo(amount);
    assertThat(sent.getValue().getCurrency()).isEqualTo(Currency.EUR);
    assertThat(sent.getValue().getPaymentChannel()).isEqualTo(LHV);
  }

  private static GiftLink aLink() {
    return GiftLink.builder()
        .id(UUID.randomUUID())
        .token("TOKEN")
        .recipientPersonalCode(CHILD)
        .createdByPersonalCode("38812121212")
        .createdAt(Instant.parse("2026-09-16T10:00:00Z"))
        .build();
  }
}
