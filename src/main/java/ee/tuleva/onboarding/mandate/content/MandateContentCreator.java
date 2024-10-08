package ee.tuleva.onboarding.mandate.content;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MandateContentCreator {

  private final MandateContentService mandateContentService;

  public List<MandateContentFile> getContentFiles(
      User user, Mandate mandate, List<Fund> funds, ContactDetails contactDetails) {
    List<MandateContentFile> files =
        new ArrayList<>(getFundTransferMandateContentFiles(user, mandate, funds, contactDetails));

    if (mandate.getFutureContributionFundIsin().isPresent()) {
      files.add(getFutureContributionsFundMandateContentFile(user, mandate, funds, contactDetails));
    }

    if (mandate.isPaymentRateApplication()) {
      files.add(getContentFileForPaymentRateChange(user, mandate, contactDetails));
    }

    return files;
  }

  private MandateContentFile getContentFileForPaymentRateChange(
      User user, Mandate mandate, ContactDetails contactDetails) {
    String htmlContent =
        mandateContentService.getRateChangeHtml(
            user, mandate, contactDetails, mandate.getPaymentRate());
    String documentNumber = mandate.getId().toString();

    return MandateContentFile.builder()
        .name("makse_maara_muutmise_avaldus_" + documentNumber + ".html")
        .content(htmlContent.getBytes())
        .build();
  }

  private MandateContentFile getFutureContributionsFundMandateContentFile(
      User user, Mandate mandate, List<Fund> funds, ContactDetails contactDetails) {
    String documentNumber = mandate.getId().toString();

    String html =
        mandateContentService.getFutureContributionsFundHtml(user, mandate, funds, contactDetails);

    return MandateContentFile.builder()
        .name("valikuavaldus_" + documentNumber + ".html")
        .content(html.getBytes())
        .build();
  }

  private List<MandateContentFile> getFundTransferMandateContentFiles(
      User user, Mandate mandate, List<Fund> funds, ContactDetails contactDetails) {
    return allocateAndGetFundTransferFiles(
        mandate.getFundTransferExchangesBySourceIsin(), user, mandate, funds, contactDetails);
  }

  private List<MandateContentFile> allocateAndGetFundTransferFiles(
      Map<String, List<FundTransferExchange>> exchangeMap,
      User user,
      Mandate mandate,
      List<Fund> funds,
      ContactDetails contactDetails) {
    return exchangeMap.keySet().stream()
        .map(
            sourceIsin ->
                getFundTransferMandateContentFile(
                    new ArrayList<>(exchangeMap.get(sourceIsin)),
                    user,
                    mandate,
                    funds,
                    contactDetails))
        .collect(toList());
  }

  private MandateContentFile getFundTransferMandateContentFile(
      List<FundTransferExchange> fundTransferExchanges,
      User user,
      Mandate mandate,
      List<Fund> funds,
      ContactDetails contactDetails) {
    String documentNumber = fundTransferExchanges.getFirst().getId().toString();

    String html =
        mandateContentService.getFundTransferHtml(
            fundTransferExchanges, user, mandate, funds, contactDetails);

    return MandateContentFile.builder()
        .name("vahetuseavaldus_" + documentNumber + ".html")
        .content(html.getBytes())
        .build();
  }
}
