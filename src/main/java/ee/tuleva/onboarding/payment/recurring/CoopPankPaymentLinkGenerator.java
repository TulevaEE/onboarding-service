package ee.tuleva.onboarding.payment.recurring;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SINGLE;
import static ee.tuleva.onboarding.payment.PaymentDateProvider.format;
import static ee.tuleva.onboarding.payment.recurring.RecurringPaymentRequest.PaymentInterval.MONTHLY;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentDateProvider;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.PaymentLinkGenerator;
import ee.tuleva.onboarding.payment.PaymentUrlEncoder;
import ee.tuleva.onboarding.payment.PrefilledLink;
import java.util.LinkedHashMap;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@AllArgsConstructor
public class CoopPankPaymentLinkGenerator implements PaymentLinkGenerator {

  private static final String HOST = "https://i.cooppank.ee/";
  private static final String SINGLE_PATH = "i/payments/new";
  private static final String STANDING_ORDER_PATH = "i/standing-orders/new";
  private static final String MONTHLY_FREQ = "2";

  private final ContactDetailsService contactDetailsService;
  private final JsonMapper objectMapper;
  private final LocaleService localeService;
  private final PaymentDateProvider paymentDateProvider;
  private final ThirdPillarRecipientConfiguration thirdPillarConfig;

  @Override
  @SneakyThrows
  public PaymentLink getPaymentLink(PaymentData paymentData, Person person) {
    ContactDetails contactDetails = contactDetailsService.getContactDetails(person);
    var url =
        switch (paymentData.getPaymentChannel()) {
          case COOP -> HOST + recurringPaymentPath(paymentData, contactDetails);
          case COOP_WEB ->
              SINGLE == paymentData.getType()
                  ? singlePaymentPath(paymentData, contactDetails)
                  : recurringPaymentPath(paymentData, contactDetails);
          case PARTNER ->
              objectMapper.writeValueAsString(
                  new RecurringPaymentRequest(
                      thirdPillarConfig.getBankAccounts().get(paymentData.getPaymentChannel()),
                      thirdPillarConfig.getRecipientName(),
                      paymentData.getAmount(),
                      paymentData.getCurrency(),
                      thirdPillarConfig.getDescription(),
                      contactDetails.getPensionAccountNumber(),
                      MONTHLY,
                      paymentDateProvider.tenthDayOfMonth()));
          default ->
              throw new ErrorsResponseException(
                  ErrorsResponse.ofSingleError(
                      "payment.channel.not.supported",
                      "Coop Pank payment links to the specified payment channel are not"
                          + " supported."));
        };
    return new PrefilledLink(
        url,
        thirdPillarConfig.getRecipientName(),
        thirdPillarConfig.getBankAccounts().get(paymentData.getPaymentChannel()),
        thirdPillarConfig.getDescription(),
        formattedAmount(paymentData));
  }

  private String singlePaymentPath(PaymentData paymentData, ContactDetails contactDetails) {
    var params = new LinkedHashMap<String, String>();
    params.put("bname", thirdPillarConfig.getRecipientName());
    params.put("bacc", thirdPillarConfig.getBankAccounts().get(paymentData.getPaymentChannel()));
    if (paymentData.getAmount() != null) {
      params.put("amt", paymentData.getAmount().toPlainString());
    }
    params.put("cur", "EUR");
    params.put("desc", thirdPillarConfig.getDescription());
    params.put("ref", contactDetails.getPensionAccountNumber());
    params.put("lang", lang());
    return SINGLE_PATH + "?" + PaymentUrlEncoder.encode(params);
  }

  private String recurringPaymentPath(PaymentData paymentData, ContactDetails contactDetails) {
    var params = new LinkedHashMap<String, String>();
    params.put("bname", thirdPillarConfig.getRecipientName());
    params.put("bacc", thirdPillarConfig.getBankAccounts().get(paymentData.getPaymentChannel()));
    if (paymentData.getAmount() != null) {
      params.put("amt", paymentData.getAmount().toPlainString());
    }
    params.put("cur", "EUR");
    params.put("desc", thirdPillarConfig.getDescription());
    params.put("ref", contactDetails.getPensionAccountNumber());
    params.put("date", format(paymentDateProvider.tenthDayOfMonth()));
    params.put("freq", MONTHLY_FREQ);
    params.put("lang", lang());
    return STANDING_ORDER_PATH + "?" + PaymentUrlEncoder.encode(params);
  }

  private static String formattedAmount(PaymentData paymentData) {
    return paymentData.getAmount() == null ? null : paymentData.getAmount().toPlainString();
  }

  private String lang() {
    // Coop uses country code "ee" for Estonian, not ISO 639 "et".
    return switch (localeService.getCurrentLanguage()) {
      case "en" -> "en";
      case "ru" -> "ru";
      default -> "ee";
    };
  }
}
