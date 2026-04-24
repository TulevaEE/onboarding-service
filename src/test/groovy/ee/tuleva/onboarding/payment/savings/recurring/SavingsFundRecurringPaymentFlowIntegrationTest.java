package ee.tuleva.onboarding.payment.savings.recurring;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SWEDBANK;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SAVINGS_RECURRING;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.PROCESSED;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SavingsFundRecurringPaymentFlowIntegrationTest {

  private static final String PERSONAL_CODE = "38812121215";
  private static final String SWEDBANK_IBAN = "EE782200221072366467";

  @Autowired private SavingsFundRecurringPaymentLinkGenerator linkGenerator;
  @Autowired private SavingFundPaymentRepository savingFundPaymentRepository;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  void savingsRecurringLhv_returnsUrlWithRecipientAndPersonalCode() {
    var person = sampleAuthenticatedPersonNonMember().personalCode(PERSONAL_CODE).build();
    var paymentData =
        PaymentData.builder()
            .amount(new BigDecimal("50"))
            .currency(EUR)
            .type(SAVINGS_RECURRING)
            .paymentChannel(LHV)
            .recipientPersonalCode(PERSONAL_CODE)
            .build();

    var link = linkGenerator.getPaymentLink(paymentData, person);

    assertThat(link.url())
        .startsWith("https://www.lhv.ee/ibank/cf/portfolio/payment_standing_add?")
        .contains("i_receiver_account_no=EE711010220306707220")
        .contains("i_payment_desc=38812121215");
    assertThat(link.recipientIban()).isEqualTo("EE711010220306707220");
    assertThat(link.recipientName()).isEqualTo("Tuleva Täiendav Kogumisfond");
  }

  @Test
  void savingsRecurringSwedbank_prefillsAccountFromLastProcessedRemitterIban() {
    var person = sampleAuthenticatedPersonNonMember().personalCode(PERSONAL_CODE).build();
    var party = new PartyId(PERSON, PERSONAL_CODE);

    var paymentId =
        savingFundPaymentRepository.savePaymentData(
            SavingFundPayment.builder()
                .externalId("integration-test-1")
                .remitterName("Test Remitter")
                .remitterIdCode(PERSONAL_CODE)
                .remitterIban(SWEDBANK_IBAN)
                .beneficiaryName("Tuleva")
                .beneficiaryIdCode("14118923")
                .beneficiaryIban("EE711010220306707220")
                .amount(new BigDecimal("100.00"))
                .description(PERSONAL_CODE)
                .build());
    savingFundPaymentRepository.attachParty(paymentId, party);
    jdbcTemplate.update(
        "update saving_fund_payment set status=:status where id=:id",
        Map.of("status", PROCESSED.name(), "id", paymentId));

    var paymentData =
        PaymentData.builder()
            .amount(new BigDecimal("50"))
            .currency(EUR)
            .type(SAVINGS_RECURRING)
            .paymentChannel(SWEDBANK)
            .recipientPersonalCode(PERSONAL_CODE)
            .build();

    var link = linkGenerator.getPaymentLink(paymentData, person);

    assertThat(link.url())
        .startsWith("https://www.swedbank.ee/private/d2d/payments2/standing_order/new?")
        .contains("standingOrder.beneficiaryAccountNumber=EE711010220306707220")
        .contains("frequency=K")
        .contains("account=221072366467");
  }
}
