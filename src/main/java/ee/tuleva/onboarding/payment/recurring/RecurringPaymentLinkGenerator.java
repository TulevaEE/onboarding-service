package ee.tuleva.onboarding.payment.recurring;

import static ee.tuleva.onboarding.payment.recurring.PaymentDateProvider.format;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.PaymentLinkGenerator;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecurringPaymentLinkGenerator implements PaymentLinkGenerator {

  private final ContactDetailsService contactDetailsService;
  private final PaymentDateProvider paymentDateProvider;
  private final CoopPankPaymentLinkGenerator coopPankPaymentLinkGenerator;

  @Override
  @SneakyThrows
  public PaymentLink getPaymentLink(PaymentData paymentData, Person person) {
    ContactDetails contactDetails = contactDetailsService.getContactDetails(person);
    var url =
        switch (paymentData.getPaymentChannel()) {
          case SWEDBANK -> "https://www.swedbank.ee/private/pensions/pillar3/orderp3p";
          case SEB ->
              "https://e.seb.ee/web/ipank?act=PENSION3_STPAYM"
                  + "&saajakonto=EE141010220263146225" // SEB account
                  + "&saajanimi=AS%20Pensionikeskus"
                  + "&selgitus=30101119828%2C%20EE3600001707"
                  // 3rd pillar processing code and Tuleva 3rd pillar fund ISIN
                  + "&viitenr="
                  + contactDetails.getPensionAccountNumber()
                  + "&summa="
                  + paymentData.getAmount()
                  + "&alguskuup="
                  + format(paymentDateProvider.tenthDayOfMonth())
                  + "&sagedus=M"; // Monthly
          case LHV ->
              "https://www.lhv.ee/ibank/cf/portfolio/payment_standing_add"
                  + "?i_receiver_name=AS%20Pensionikeskus"
                  + "&i_receiver_account_no=EE547700771002908125" // LHV account
                  + "&i_payment_desc=30101119828%2c%20EE3600001707"
                  // 3rd pillar processing code and Tuleva 3rd pillar fund ISIN
                  + "&i_payment_clirefno="
                  + contactDetails.getPensionAccountNumber()
                  + "&i_amount="
                  + paymentData.getAmount()
                  + "&i_currency_id=38" // EUR
                  + "&i_interval_type=K" // Kuu
                  + "&i_date_first_payment="
                  + format(paymentDateProvider.tenthDayOfMonth());
          case LUMINOR -> "https://luminor.ee/auth/#/web/view/autopilot/newpayment";
          case COOP, COOP_WEB, PARTNER ->
              coopPankPaymentLinkGenerator.getPaymentLink(paymentData, person).url();
          case TULUNDUSUHISTU ->
              throw new IllegalArgumentException(
                  "Recurring payments to the specified payment channel are not supported");
        };
    return new PaymentLink(url);
  }
}
