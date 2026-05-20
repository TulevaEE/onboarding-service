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
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentData.PaymentChannel;
import ee.tuleva.onboarding.payment.PaymentDateProvider;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.PrefilledLink;
import ee.tuleva.onboarding.payment.savings.SavingsFundRecipientConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

class SavingsFundRecurringPaymentLinkGeneratorTest {

  private static final String PERSONAL_CODE = "38812121215";
  private static final String RECIPIENT_NAME = "Tuleva Täiendav Kogumisfond";
  private static final String RECIPIENT_IBAN = "EE711010220306707220";
  private static final LocalDate FIRST_PAYMENT = LocalDate.of(2026, 5, 10);

  private final SavingsFundRecipientConfiguration recipientConfiguration =
      new SavingsFundRecipientConfiguration();
  private final PaymentDateProvider paymentDateProvider = mock(PaymentDateProvider.class);
  private final LocaleService localeService = new LocaleService();

  private final SavingsFundRecurringPaymentLinkGenerator generator =
      new SavingsFundRecurringPaymentLinkGenerator(
          recipientConfiguration, paymentDateProvider, localeService);

  @BeforeEach
  void setup() {
    recipientConfiguration.setRecipientName(RECIPIENT_NAME);
    recipientConfiguration.setRecipientIban(RECIPIENT_IBAN);
    given(paymentDateProvider.tenthDayOfMonth()).willReturn(FIRST_PAYMENT);
    LocaleContextHolder.setLocale(Locale.ENGLISH);
  }

  @AfterEach
  void cleanup() {
    LocaleContextHolder.resetLocaleContext();
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
        .startsWith("https://i.cooppank.ee/i/standing-orders/new?")
        .contains("bname=Tuleva%20T%C3%A4iendav%20Kogumisfond")
        .contains("bacc=EE711010220306707220")
        .contains("amt=50")
        .contains("cur=EUR")
        .contains("desc=38812121215")
        .contains("date=10.05.2026")
        .contains("freq=2")
        .contains("lang=en")
        .doesNotContain(
            "SaajaNimi",
            "SaajaKonto",
            "MaksePohjus",
            "MakseSumma",
            "MakseSagedus",
            "MakseEsimene",
            "whatform",
            "newpmt");
    assertCommonRecipientFields(link);
  }

  @Test
  void buildsCoopUrlWithEstonianLocale() {
    LocaleContextHolder.setLocale(Locale.of("et"));

    var link = prefilledLink(COOP);

    assertThat(link.url()).contains("lang=ee").doesNotContain("lang=et");
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
        .contains("standingOrder.firstPaymentDate=10.05.2026")
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
  void buildsUrlWithoutAmountWhenAmountIsMissing() {
    var paymentData =
        PaymentData.builder()
            .currency(EUR)
            .type(SAVINGS_RECURRING)
            .paymentChannel(LHV)
            .recipientPersonalCode(PERSONAL_CODE)
            .build();

    var link = (PrefilledLink) generator.getPaymentLink(paymentData, person());

    assertThat(link.amount()).isNull();
    assertThat(link.url())
        .startsWith("https://www.lhv.ee/ibank/cf/portfolio/payment_standing_add?")
        .doesNotContain("i_amount=");
    assertThat(link.recipientName()).isEqualTo(RECIPIENT_NAME);
    assertThat(link.recipientIban()).isEqualTo(RECIPIENT_IBAN);
    assertThat(link.description()).isEqualTo(PERSONAL_CODE);
  }

  @Test
  void acceptsExactMinimumAmount() {
    var paymentData =
        PaymentData.builder()
            .amount(new BigDecimal("0.01"))
            .currency(EUR)
            .type(SAVINGS_RECURRING)
            .paymentChannel(LHV)
            .recipientPersonalCode(PERSONAL_CODE)
            .build();

    var link = (PrefilledLink) generator.getPaymentLink(paymentData, person());

    assertThat(link.amount()).isEqualTo("0.01");
    assertThat(link.url()).contains("i_amount=0.01");
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

  @Test
  void rendersAmountAsPlainDecimalEvenWhenBigDecimalWouldPrintScientific() {
    var paymentData =
        PaymentData.builder()
            .amount(new BigDecimal("1E2"))
            .currency(EUR)
            .type(SAVINGS_RECURRING)
            .paymentChannel(LHV)
            .recipientPersonalCode(PERSONAL_CODE)
            .build();

    var link = (PrefilledLink) generator.getPaymentLink(paymentData, person());

    assertThat(link.amount()).isEqualTo("100");
    assertThat(link.url()).contains("i_amount=100").doesNotContain("1E+2").doesNotContain("1E2");
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
