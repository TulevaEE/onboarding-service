package ee.tuleva.onboarding.payment.recurring;

import static ee.tuleva.onboarding.payment.PaymentDateProvider.format;
import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentData.PaymentChannel;
import ee.tuleva.onboarding.payment.PaymentDateProvider;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.PaymentLinkGenerator;
import ee.tuleva.onboarding.payment.PrefilledLink;
import ee.tuleva.onboarding.payment.RedirectLink;
import java.math.BigDecimal;
import java.net.URLEncoder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecurringPaymentLinkGenerator implements PaymentLinkGenerator {

  private final ContactDetailsService contactDetailsService;
  private final PaymentDateProvider paymentDateProvider;
  private final CoopPankPaymentLinkGenerator coopPankPaymentLinkGenerator;
  private final ThirdPillarRecipientConfiguration thirdPillarConfig;

  @Override
  @SneakyThrows
  public PaymentLink getPaymentLink(PaymentData paymentData, Person person) {
    if (paymentData.getPaymentChannel() == null) {
      throw new ErrorsResponseException(
          ErrorsResponse.ofSingleError(
              "payment.channel.required", "Payment channel is required for recurring payments."));
    }
    ContactDetails contactDetails = contactDetailsService.getContactDetails(person);
    var encodedName = urlEncode(thirdPillarConfig.getRecipientName());
    var encodedDescription = urlEncode(thirdPillarConfig.getDescription());
    var channel = paymentData.getPaymentChannel();
    return switch (channel) {
      case SWEDBANK ->
          new RedirectLink("https://www.swedbank.ee/private/pensions/pillar3/orderp3p");
      case SEB -> {
        var amount = requireAmount(paymentData, channel);
        yield new RedirectLink(
            "https://e.seb.ee/web/ipank?act=PENSION3_STPAYM"
                + "&saajakonto="
                + thirdPillarConfig.getBankAccounts().get(PaymentChannel.SEB)
                + "&saajanimi="
                + encodedName
                + "&selgitus="
                + encodedDescription
                + "&viitenr="
                + contactDetails.getPensionAccountNumber()
                + "&summa="
                + amount
                + "&alguskuup="
                + format(paymentDateProvider.tenthDayOfMonth())
                + "&sagedus=M"); // Monthly
      }
      case LHV -> {
        var amount = requireAmount(paymentData, channel);
        yield new PrefilledLink(
            "https://www.lhv.ee/ibank/cf/portfolio/payment_standing_add"
                + "?i_receiver_name="
                + encodedName
                + "&i_receiver_account_no="
                + thirdPillarConfig.getBankAccounts().get(PaymentChannel.LHV)
                + "&i_payment_desc="
                // LHV's pre-existing URL uses lowercase %2c for the comma
                + encodedDescription.replace("%2C", "%2c")
                + "&i_payment_clirefno="
                + contactDetails.getPensionAccountNumber()
                + "&i_amount="
                + amount
                + "&i_currency_id=38" // EUR
                + "&i_interval_type=K" // Kuu
                + "&i_date_first_payment="
                + format(paymentDateProvider.tenthDayOfMonth()),
            thirdPillarConfig.getRecipientName(),
            thirdPillarConfig.getBankAccounts().get(PaymentChannel.LHV),
            thirdPillarConfig.getDescription(),
            amount.toString());
      }
      case LUMINOR -> new RedirectLink("https://luminor.ee/auth/#/web/view/autopilot/newpayment");
      case COOP, COOP_WEB, PARTNER ->
          coopPankPaymentLinkGenerator.getPaymentLink(paymentData, person);
      case TULUNDUSUHISTU ->
          throw new ErrorsResponseException(
              ErrorsResponse.ofSingleError(
                  "payment.channel.not.supported",
                  "Recurring payments to the specified payment channel are not supported."));
    };
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, UTF_8).replace("+", "%20");
  }

  private static BigDecimal requireAmount(PaymentData paymentData, PaymentChannel channel) {
    var amount = paymentData.getAmount();
    if (amount == null) {
      throw new ErrorsResponseException(
          ErrorsResponse.ofSingleError(
              "payment.amount.required",
              "Payment amount is required for " + channel + " recurring links."));
    }
    return amount;
  }
}
