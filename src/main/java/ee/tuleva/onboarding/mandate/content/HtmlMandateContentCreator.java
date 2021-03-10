package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.content.thymeleaf.ContextBuilder;
import ee.tuleva.onboarding.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@Component
@RequiredArgsConstructor
public class HtmlMandateContentCreator implements MandateContentCreator {

  private final ITemplateEngine templateEngine;

  @Override
  public List<MandateContentFile> getContentFiles(
      User user, Mandate mandate, List<Fund> funds, UserPreferences userPreferences) {
    List<MandateContentFile> files =
        new ArrayList<>(getFundTransferMandateContentFiles(user, mandate, funds, userPreferences));

    if (mandate.getFutureContributionFundIsin().isPresent()) {
      files.add(
          getFutureContributionsFundMandateContentFile(user, mandate, funds, userPreferences));
    }

    return files;
  }

  @Override
  public MandateContentFile getContentFileForMandateCancellation(
      User user, Mandate mandate, UserPreferences userPreferences, String mandateTypeToCancel) {
    String transactionId = UUID.randomUUID().toString();
    String documentNumber = mandate.getId().toString();

    Context ctx =
        ContextBuilder.builder()
            .mandate(mandate)
            .user(user)
            .userPreferences(userPreferences)
            .transactionId(transactionId)
            .documentNumber(documentNumber)
            .mandateTypeToCancel(mandateTypeToCancel)
            .build();

    String htmlContent = templateEngine.process("mandate_cancellation_mandate", ctx);

    return MandateContentFile.builder()
        .name("avalduse_tyhistamise_avaldus_" + documentNumber + ".html")
        .mimeType("text/html")
        .content(htmlContent.getBytes())
        .build();
  }

  private MandateContentFile getFutureContributionsFundMandateContentFile(
      User user, Mandate mandate, List<Fund> funds, UserPreferences userPreferences) {
    String transactionId = UUID.randomUUID().toString();

    String documentNumber = mandate.getId().toString();

    Context ctx =
        ContextBuilder.builder()
            .mandate(mandate)
            .user(user)
            .userPreferences(userPreferences)
            .transactionId(transactionId)
            .documentNumber(documentNumber)
            .futureContributionFundIsin(mandate.getFutureContributionFundIsin().orElse(null))
            .funds(funds)
            .build();

    String htmlContent =
        templateEngine.process("future_contributions_fund_pillar_" + mandate.getPillar(), ctx);

    return MandateContentFile.builder()
        .name("valikuavaldus_" + documentNumber + ".html")
        .mimeType("text/html")
        .content(htmlContent.getBytes())
        .build();
  }

  private List<MandateContentFile> getFundTransferMandateContentFiles(
      User user, Mandate mandate, List<Fund> funds, UserPreferences userPreferences) {
    return allocateAndGetFundTransferFiles(
        mandate.getFundTransferExchangesBySourceIsin(), user, mandate, funds, userPreferences);
  }

  private List<MandateContentFile> allocateAndGetFundTransferFiles(
      Map<String, List<FundTransferExchange>> exchangeMap,
      User user,
      Mandate mandate,
      List<Fund> funds,
      UserPreferences userPreferences) {
    return exchangeMap.keySet().stream()
        .map(
            sourceIsin ->
                getFundTransferMandateContentFile(
                    new ArrayList<>(exchangeMap.get(sourceIsin)),
                    user,
                    mandate,
                    funds,
                    userPreferences))
        .collect(Collectors.toList());
  }

  private MandateContentFile getFundTransferMandateContentFile(
      List<FundTransferExchange> fundTransferExchanges,
      User user,
      Mandate mandate,
      List<Fund> funds,
      UserPreferences userPreferences) {
    String transactionId = UUID.randomUUID().toString();
    String documentNumber = fundTransferExchanges.get(0).getId().toString();

    Context ctx =
        ContextBuilder.builder()
            .mandate(mandate)
            .user(user)
            .userPreferences(userPreferences)
            .transactionId(transactionId)
            .documentNumber(documentNumber)
            .fundTransferExchanges(fundTransferExchanges)
            .funds(funds)
            .build();

    String htmlContent = templateEngine.process("fund_transfer_pillar_" + mandate.getPillar(), ctx);

    return MandateContentFile.builder()
        .name("vahetuseavaldus_" + documentNumber + ".html")
        .mimeType("text/html")
        .content(htmlContent.getBytes())
        .build();
  }
}
