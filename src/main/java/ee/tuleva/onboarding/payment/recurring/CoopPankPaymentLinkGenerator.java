package ee.tuleva.onboarding.payment.recurring;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SINGLE;
import static ee.tuleva.onboarding.payment.recurring.PaymentDateProvider.format;
import static ee.tuleva.onboarding.payment.recurring.RecurringPaymentRequest.PaymentInterval.MONTHLY;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.PaymentLinkGenerator;
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
                      "EE362200221067235244", // Swedbank account
                      "AS Pensionikeskus",
                      paymentData.getAmount(),
                      paymentData.getCurrency(),
                      "30101119828, EE3600001707",
                      contactDetails.getPensionAccountNumber(),
                      MONTHLY,
                      paymentDateProvider.tenthDayOfMonth()));
          default ->
              throw new IllegalArgumentException(
                  "Unsupported payment channel: " + paymentData.getPaymentChannel());
        };
    return new PaymentLink(url);
  }

  private String singlePaymentPath(PaymentData paymentData, ContactDetails contactDetails) {
    return "newpmt"
        + language()
        + "?SaajaNimi=AS%20Pensionikeskus"
        + "&SaajaKonto=EE362200221067235244" // Swedbank account
        + (paymentData.getAmount() != null ? "&MuutMakseSumma=" + paymentData.getAmount() : "")
        + "&MaksePohjus=30101119828%2c%20EE3600001707"
        + "&ViiteNumber="
        + contactDetails.getPensionAccountNumber();
  }

  private String recurringPaymentPath(PaymentData paymentData, ContactDetails contactDetails) {
    return "newpmt"
        + language()
        + "?whatform=PermPaymentNew"
        + "&SaajaNimi=AS%20Pensionikeskus"
        + "&SaajaKonto=EE362200221067235244" // Swedbank account
        + (paymentData.getAmount() != null ? "&MakseSumma=" + paymentData.getAmount() : "")
        + "&MaksePohjus=30101119828%2c%20EE3600001707"
        + "&ViiteNumber="
        + contactDetails.getPensionAccountNumber()
        + "&MakseSagedus=3" // Monthly
        + "&MakseEsimene="
        + format(paymentDateProvider.tenthDayOfMonth());
  }

  private String language() {
    return Locale.ENGLISH.getLanguage().equals(localeService.getCurrentLanguage()) ? "-eng" : "";
  }
}
