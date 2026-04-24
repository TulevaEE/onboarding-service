package ee.tuleva.onboarding.payment.savings.recurring;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.COOP;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LUMINOR;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.OTHER;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SEB;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SWEDBANK;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.TULUNDUSUHISTU;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SAVINGS_RECURRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentData.PaymentChannel;
import ee.tuleva.onboarding.payment.recurring.PaymentDateProvider;
import ee.tuleva.onboarding.payment.savings.SavingsFundRecipientConfiguration;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class SavingsFundRecurringPaymentLinkGeneratorTest {

  private static final String PERSONAL_CODE = "38812121215";
  private static final String RECIPIENT_NAME = "Tuleva Täiendav Kogumisfond";
  private static final String RECIPIENT_IBAN = "EE711010220306707220";
  private static final LocalDate FIRST_PAYMENT = LocalDate.of(2026, 5, 10);

  private final SavingsFundRecipientConfiguration recipientConfiguration =
      new SavingsFundRecipientConfiguration();
  private final PaymentDateProvider paymentDateProvider = mock(PaymentDateProvider.class);
  private final SavingFundPaymentRepository savingFundPaymentRepository =
      mock(SavingFundPaymentRepository.class);

  private final SavingsFundRecurringPaymentLinkGenerator generator =
      new SavingsFundRecurringPaymentLinkGenerator(
          recipientConfiguration, paymentDateProvider, savingFundPaymentRepository);

  @BeforeEach
  void setup() {
    recipientConfiguration.setRecipientName(RECIPIENT_NAME);
    recipientConfiguration.setRecipientIban(RECIPIENT_IBAN);
    given(paymentDateProvider.tenthDayOfMonth()).willReturn(FIRST_PAYMENT);
    given(
            savingFundPaymentRepository.findLastRemitterIban(
                new PartyId(PartyId.Type.PERSON, PERSONAL_CODE)))
        .willReturn(Optional.empty());
  }

  @Test
  void buildsLhvUrl() {
    var link = generator.getPaymentLink(paymentData(LHV), person());

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
    var link = generator.getPaymentLink(paymentData(COOP), person());

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
  void buildsSwedbankUrlWithoutAccountWhenNoPriorPayments() {
    var link = generator.getPaymentLink(paymentData(SWEDBANK), person());

    assertThat(link.url())
        .startsWith("https://www.swedbank.ee/private/d2d/payments2/standing_order/new?")
        .contains("standingOrder.beneficiaryAccountNumber=EE711010220306707220")
        .contains("standingOrder.beneficiaryName=Tuleva%20T%C3%A4iendav%20Kogumisfond")
        .contains("standingOrder.amount=50")
        .contains("standingOrder.details=38812121215")
        .contains("frequency=K")
        .doesNotContain("account=");
    assertCommonRecipientFields(link);
  }

  @Test
  void buildsSwedbankUrlWithAccountWhenLastRemitterIsSwedbank() {
    given(
            savingFundPaymentRepository.findLastRemitterIban(
                new PartyId(PartyId.Type.PERSON, PERSONAL_CODE)))
        .willReturn(Optional.of("EE782200221072366467"));

    var link = generator.getPaymentLink(paymentData(SWEDBANK), person());

    assertThat(link.url()).contains("account=221072366467");
  }

  @Test
  void buildsSwedbankUrlWithoutAccountWhenLastRemitterIsNotSwedbank() {
    given(
            savingFundPaymentRepository.findLastRemitterIban(
                new PartyId(PartyId.Type.PERSON, PERSONAL_CODE)))
        .willReturn(Optional.of("EE547700771002908125"));

    var link = generator.getPaymentLink(paymentData(SWEDBANK), person());

    assertThat(link.url()).doesNotContain("account=");
  }

  @Test
  void buildsSebLandingUrl() {
    var link = generator.getPaymentLink(paymentData(SEB), person());

    assertThat(link.url()).isEqualTo("https://e.seb.ee/ib/p/payments/new-standing-order");
    assertCommonRecipientFields(link);
  }

  @Test
  void buildsLuminorLandingUrl() {
    var link = generator.getPaymentLink(paymentData(LUMINOR), person());

    assertThat(link.url()).isEqualTo("https://luminor.ee/auth/#/web/view/autopilot/newpayment");
    assertCommonRecipientFields(link);
  }

  @Test
  void returnsNullUrlForOtherChannel() {
    var link = generator.getPaymentLink(paymentData(OTHER), person());

    assertThat(link.url()).isNull();
    assertCommonRecipientFields(link);
  }

  @Test
  void throwsForUnsupportedChannel() {
    assertThatThrownBy(() -> generator.getPaymentLink(paymentData(TULUNDUSUHISTU), person()))
        .isInstanceOf(IllegalArgumentException.class);
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

    var link = generator.getPaymentLink(paymentData, callerPerson);

    assertThat(link.description()).isEqualTo("49001011234");
    assertThat(link.url()).contains("i_payment_desc=49001011234").doesNotContain("38812121215");
  }

  @Test
  void buildsSwedbankUrlWithoutAccountWhenRemitterIbanIsMalformed() {
    given(
            savingFundPaymentRepository.findLastRemitterIban(
                new PartyId(PartyId.Type.PERSON, PERSONAL_CODE)))
        .willReturn(Optional.of("EE22"));

    var link = generator.getPaymentLink(paymentData(SWEDBANK), person());

    assertThat(link.url()).doesNotContain("account=");
  }

  @Test
  void encodesReservedUrlCharactersInRecipientName() {
    recipientConfiguration.setRecipientName("Tuleva & Co?");

    var link = generator.getPaymentLink(paymentData(LHV), person());

    assertThat(link.url())
        .contains("i_receiver_name=Tuleva%20%26%20Co%3F")
        .doesNotContain("Tuleva & Co?");
  }

  @ParameterizedTest
  @EnumSource(
      value = PaymentChannel.class,
      names = {"LHV", "COOP", "SEB", "LUMINOR", "OTHER"})
  void doesNotIncludeAccountParamForNonSwedbankChannels(PaymentChannel channel) {
    given(
            savingFundPaymentRepository.findLastRemitterIban(
                new PartyId(PartyId.Type.PERSON, PERSONAL_CODE)))
        .willReturn(Optional.of("EE782200221072366467"));

    var link = generator.getPaymentLink(paymentData(channel), person());

    if (link.url() != null) {
      assertThat(link.url()).doesNotContain("account=");
    }
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

  private void assertCommonRecipientFields(ee.tuleva.onboarding.payment.PaymentLink link) {
    assertThat(link.recipientName()).isEqualTo(RECIPIENT_NAME);
    assertThat(link.recipientIban()).isEqualTo(RECIPIENT_IBAN);
    assertThat(link.description()).isEqualTo(PERSONAL_CODE);
    assertThat(link.amount()).isEqualTo("50");
  }
}
