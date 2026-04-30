package ee.tuleva.onboarding.payment.recurring;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SINGLE;
import static ee.tuleva.onboarding.payment.PaymentDateProvider.format;
import static ee.tuleva.onboarding.payment.recurring.RecurringPaymentRequest.PaymentInterval.MONTHLY;
import static java.nio.charset.StandardCharsets.UTF_8;

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
import ee.tuleva.onboarding.payment.PrefilledLink;
import java.net.URLEncoder;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@AllArgsConstructor
public class CoopPankPaymentLinkGenerator implements PaymentLinkGenerator {

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
          case COOP -> "https://i.cooppank.ee/" + recurringPaymentPath(paymentData, contactDetails);
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
    return "newpmt"
        + language()
        + "?SaajaNimi="
        + urlEncode(thirdPillarConfig.getRecipientName())
        + "&SaajaKonto="
        + thirdPillarConfig.getBankAccounts().get(paymentData.getPaymentChannel())
        + (paymentData.getAmount() != null
            ? "&MuutMakseSumma=" + paymentData.getAmount().toPlainString()
            : "")
        + "&MaksePohjus="
        // Coop Pank's form only accepts lowercase %2c; URLEncoder emits %2C
        + urlEncode(thirdPillarConfig.getDescription()).replace("%2C", "%2c")
        + "&ViiteNumber="
        + contactDetails.getPensionAccountNumber();
  }

  private String recurringPaymentPath(PaymentData paymentData, ContactDetails contactDetails) {
    return "newpmt"
        + language()
        + "?whatform=PermPaymentNew"
        + "&SaajaNimi="
        + urlEncode(thirdPillarConfig.getRecipientName())
        + "&SaajaKonto="
        + thirdPillarConfig.getBankAccounts().get(paymentData.getPaymentChannel())
        + (paymentData.getAmount() != null
            ? "&MakseSumma=" + paymentData.getAmount().toPlainString()
            : "")
        + "&MaksePohjus="
        // Coop Pank's form only accepts lowercase %2c; URLEncoder emits %2C
        + urlEncode(thirdPillarConfig.getDescription()).replace("%2C", "%2c")
        + "&ViiteNumber="
        + contactDetails.getPensionAccountNumber()
        + "&MakseSagedus=3" // Monthly
        + "&MakseEsimene="
        + format(paymentDateProvider.tenthDayOfMonth());
  }

  private static String formattedAmount(PaymentData paymentData) {
    return paymentData.getAmount() == null ? null : paymentData.getAmount().toPlainString();
  }

  private String language() {
    return Locale.ENGLISH.getLanguage().equals(localeService.getCurrentLanguage()) ? "-eng" : "";
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, UTF_8).replace("+", "%20");
  }
}
