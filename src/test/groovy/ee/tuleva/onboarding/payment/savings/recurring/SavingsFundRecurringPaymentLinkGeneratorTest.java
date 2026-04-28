package ee.tuleva.onboarding.payment.savings.recurring;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.COOP;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LUMINOR;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SEB;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SWEDBANK;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.TULUNDUSUHISTU;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SAVINGS_RECURRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentData.PaymentChannel;
import ee.tuleva.onboarding.payment.PaymentDateProvider;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.PrefilledLink;
import ee.tuleva.onboarding.payment.savings.SavingsFundRecipientConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SavingsFundRecurringPaymentLinkGeneratorTest {

  private static final String PERSONAL_CODE = "38812121215";
  private static final String RECIPIENT_NAME = "Tuleva Täiendav Kogumisfond";
  private static final String RECIPIENT_IBAN = "EE711010220306707220";
  private static final LocalDate FIRST_PAYMENT = LocalDate.of(2026, 5, 10);

  private final SavingsFundRecipientConfiguration recipientConfiguration =
      new SavingsFundRecipientConfiguration();
  private final PaymentDateProvider paymentDateProvider = mock(PaymentDateProvider.class);

  private final SavingsFundRecurringPaymentLinkGenerator generator =
      new SavingsFundRecurringPaymentLinkGenerator(recipientConfiguration, paymentDateProvider);

  @BeforeEach
  void setup() {
    recipientConfiguration.setRecipientName(RECIPIENT_NAME);
    recipientConfiguration.setRecipientIban(RECIPIENT_IBAN);
    given(paymentDateProvider.tenthDayOfMonth()).willReturn(FIRST_PAYMENT);
  }

  @Test
  void buildsLhvUrl() {
    var link = prefilledLink(LHV);

    assertThat(link.url())
        .startsWith("https://www.lhv.ee/ibank/cf/portfolio/payment_standing_add?")
        .contains("i_receiver_name=Tuleva%20T%C3%A4iendav%20Kogumisfond")
        .contains("i_receiver_account_no=EE711010220306707220")
        .contains("i_payment_desc=38812121215")
        .contains("i_amount=50")
        .contains("i_currency_id=38")
        .contains("i_interval_type=K")
        .contains("i_date_first_payment=10.05.2026");
    assertCommonRecipientFields(link);
  }

  @Test
  void buildsCoopUrl() {
    var link = prefilledLink(COOP);

    assertThat(link.url())
        .startsWith("https://i.cooppank.ee/newpmt?")
        .contains("whatform=PermPaymentNew")
        .contains("SaajaNimi=Tuleva%20T%C3%A4iendav%20Kogumisfond")
        .contains("SaajaKonto=EE711010220306707220")
        .contains("MakseSumma=50")
        .contains("MaksePohjus=38812121215")
        .contains("MakseSagedus=3")
        .contains("MakseEsimene=10.05.2026");
    assertCommonRecipientFields(link);
  }

  @Test
  void buildsSwedbankUrl() {
    var link = prefilledLink(SWEDBANK);

    assertThat(link.url())
        .startsWith("https://www.swedbank.ee/private/d2d/payments2/standing_order/new?")
        .contains("standingOrder.beneficiaryAccountNumber=EE711010220306707220")
        .contains("standingOrder.beneficiaryName=Tuleva%20T%C3%A4iendav%20Kogumisfond")
        .contains("standingOrder.amount=50")
        .contains("standingOrder.details=38812121215")
        .contains("frequency=K");
    assertCommonRecipientFields(link);
  }

  @Test
  void buildsSebLandingUrl() {
    var link = prefilledLink(SEB);

    assertThat(link.url()).isEqualTo("https://e.seb.ee/ib/p/payments/new-standing-order");
    assertCommonRecipientFields(link);
  }

  @Test
  void buildsLuminorLandingUrl() {
    var link = prefilledLink(LUMINOR);

    assertThat(link.url()).isEqualTo("https://luminor.ee/auth/#/web/view/autopilot/newpayment");
    assertCommonRecipientFields(link);
  }

  @Test
  void returnsNullUrlWhenPaymentChannelIsMissing() {
    var link = prefilledLink(null);

    assertThat(link.url()).isNull();
    assertCommonRecipientFields(link);
  }

  @Test
  void throwsForUnsupportedChannel() {
    assertThatThrownBy(() -> generator.getPaymentLink(paymentData(TULUNDUSUHISTU), person()))
        .isInstanceOf(ErrorsResponseException.class)
        .extracting(ex -> ((ErrorsResponseException) ex).getErrorsResponse().getErrors().get(0))
        .extracting(ErrorResponse::getCode)
        .isEqualTo("payment.channel.not.supported");
  }

  @Test
  void throwsWhenAmountIsMissing() {
    var paymentData =
        PaymentData.builder()
            .currency(EUR)
            .type(SAVINGS_RECURRING)
            .paymentChannel(LHV)
            .recipientPersonalCode(PERSONAL_CODE)
            .build();

    assertThatThrownBy(() -> generator.getPaymentLink(paymentData, person()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "0.001"})
  void throwsWhenAmountIsNotPositive(String value) {
    var paymentData =
        PaymentData.builder()
            .amount(new BigDecimal(value))
            .currency(EUR)
            .type(SAVINGS_RECURRING)
            .paymentChannel(LHV)
            .recipientPersonalCode(PERSONAL_CODE)
            .build();

    assertThatThrownBy(() -> generator.getPaymentLink(paymentData, person()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void usesRecipientPersonalCodeFromPaymentDataAsDescription() {
    var paymentData =
        PaymentData.builder()
            .amount(new BigDecimal("50"))
            .currency(EUR)
            .type(SAVINGS_RECURRING)
            .paymentChannel(LHV)
            .recipientPersonalCode("49001011234")
            .build();
    var callerPerson = sampleAuthenticatedPersonNonMember().personalCode("38812121215").build();

    var link = (PrefilledLink) generator.getPaymentLink(paymentData, callerPerson);

    assertThat(link.description()).isEqualTo("49001011234");
    assertThat(link.url()).contains("i_payment_desc=49001011234").doesNotContain("38812121215");
  }

  @Test
  void encodesReservedUrlCharactersInRecipientName() {
    recipientConfiguration.setRecipientName("Tuleva & Co?");

    var link = prefilledLink(LHV);

    assertThat(link.url())
        .contains("i_receiver_name=Tuleva%20%26%20Co%3F")
        .doesNotContain("Tuleva & Co?");
  }

  private PrefilledLink prefilledLink(PaymentChannel channel) {
    PaymentLink link = generator.getPaymentLink(paymentData(channel), person());
    assertThat(link).isInstanceOf(PrefilledLink.class);
    return (PrefilledLink) link;
  }

  private PaymentData paymentData(PaymentChannel channel) {
    return PaymentData.builder()
        .amount(new BigDecimal("50"))
        .currency(EUR)
        .type(SAVINGS_RECURRING)
        .paymentChannel(channel)
        .recipientPersonalCode(PERSONAL_CODE)
        .build();
  }

  private ee.tuleva.onboarding.auth.principal.Person person() {
    return sampleAuthenticatedPersonNonMember().personalCode(PERSONAL_CODE).build();
  }

  private void assertCommonRecipientFields(PrefilledLink link) {
    assertThat(link.recipientName()).isEqualTo(RECIPIENT_NAME);
    assertThat(link.recipientIban()).isEqualTo(RECIPIENT_IBAN);
    assertThat(link.description()).isEqualTo(PERSONAL_CODE);
    assertThat(link.amount()).isEqualTo("50");
  }
}
