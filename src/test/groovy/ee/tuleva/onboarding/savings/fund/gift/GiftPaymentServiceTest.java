package ee.tuleva.onboarding.savings.fund.gift;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.payment.AnonymousPayment;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentService;
import ee.tuleva.onboarding.payment.RedirectLink;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GiftPaymentServiceTest {

  private static final String CHILD = "50108120265";
  private static final String DESCRIPTION = "50108120265, 1758012345";
  private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");

  @Mock GiftLinkService giftLinkService;
  @Mock GiftMessageRepository giftMessages;
  @Mock PaymentService paymentService;

  GiftPaymentService service;

  @BeforeEach
  void setUp() {
    service =
        new GiftPaymentService(
            giftLinkService, giftMessages, paymentService, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @ParameterizedTest
  // Under a euro, over the Montonio ceiling, and fractions of a cent. The browser enforces the
  // same range, but the browser is not where the rule lives.
  @ValueSource(strings = {"0", "0.99", "15000.01", "100000", "10.005"})
  void anAmountOutsideWhatMontonioWillTakeIsRefused(String amount) {
    given(giftLinkService.findOpenLink("TOKEN")).willReturn(aLink());

    assertThatThrownBy(() -> service.startPayment("TOKEN", request(amount, "Hello")))
        .isInstanceOf(IllegalArgumentException.class);

    verify(paymentService, never()).getAnonymousSavingsPaymentLink(any());
    // A refused payment must not leave a message behind for the parent to puzzle over.
    verify(giftMessages, never()).save(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "100.00", "15000"})
  void aGiftIsAddressedToTheChildTheLinkNames(String amount) {
    givenAPaymentIsMinted();

    var link = service.startPayment("TOKEN", request(amount, null));

    assertThat(link.url()).isEqualTo("https://montonio.example/pay");

    var sent = ArgumentCaptor.forClass(PaymentData.class);
    verify(paymentService).getAnonymousSavingsPaymentLink(sent.capture());
    assertThat(sent.getValue().getRecipientPersonalCode()).isEqualTo(CHILD);
    assertThat(sent.getValue().getAmount()).isEqualByComparingTo(amount);
    assertThat(sent.getValue().getCurrency()).isEqualTo(Currency.EUR);
    assertThat(sent.getValue().getPaymentChannel()).isEqualTo(LHV);
  }

  @Test
  void aGreetingIsFiledAgainstThePaymentItWasWrittenFor() {
    givenAPaymentIsMinted();

    service.startPayment("TOKEN", request("100", "  Palju õnne, Mari!  "));

    var saved = ArgumentCaptor.forClass(GiftMessage.class);
    verify(giftMessages).save(saved.capture());
    // The description is the only thing the payment carries back, whichever way it reaches us.
    assertThat(saved.getValue().getDescription()).isEqualTo(DESCRIPTION);
    assertThat(saved.getValue().getMessage()).isEqualTo("Palju õnne, Mari!");
    assertThat(saved.getValue().getAmount()).isEqualByComparingTo("100");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void anEmptyGreetingIsNoGreeting(String message) {
    givenAPaymentIsMinted();

    service.startPayment("TOKEN", request("100", message));

    verify(giftMessages, never()).save(any());
  }

  @Test
  void aGreetingTooLongToBeAGreetingIsCutDown() {
    givenAPaymentIsMinted();

    service.startPayment("TOKEN", request("100", "a".repeat(GiftMessage.MAX_LENGTH + 50)));

    var saved = ArgumentCaptor.forClass(GiftMessage.class);
    verify(giftMessages).save(saved.capture());
    assertThat(saved.getValue().getMessage()).hasSize(GiftMessage.MAX_LENGTH);
  }

  private void givenAPaymentIsMinted() {
    given(giftLinkService.findOpenLink("TOKEN")).willReturn(aLink());
    given(paymentService.getAnonymousSavingsPaymentLink(any()))
        .willReturn(
            new AnonymousPayment(new RedirectLink("https://montonio.example/pay"), DESCRIPTION));
  }

  private static GiftPaymentRequest request(String amount, String message) {
    return new GiftPaymentRequest(new BigDecimal(amount), LHV, message);
  }

  private static GiftLink aLink() {
    return GiftLink.builder()
        .id(UUID.randomUUID())
        .token("TOKEN")
        .recipientPersonalCode(CHILD)
        .createdByPersonalCode("38812121212")
        .createdAt(NOW)
        .build();
  }
}
