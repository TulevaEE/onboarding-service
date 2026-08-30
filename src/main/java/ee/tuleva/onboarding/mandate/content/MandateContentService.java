package ee.tuleva.onboarding.mandate.content;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.epis.ContactDetails;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.mandate.ApplicationType;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.content.thymeleaf.ContextBuilder;
import ee.tuleva.onboarding.mandate.details.FundPensionOpeningMandateDetails;
import ee.tuleva.onboarding.mandate.details.PartialWithdrawalMandateDetails;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
class MandateContentService {

  private final ITemplateEngine templateEngine;

  String getFundTransferHtml(
      List<FundTransferExchange> fundTransferExchanges,
      User user,
      Mandate mandate,
      List<Fund> funds,
      ContactDetails contactDetails) {
    String transactionId = UUID.randomUUID().toString();
    String documentNumber = fundTransferExchanges.getFirst().getId().toString();

    Context ctx =
        ContextBuilder.builder()
            .mandate(mandate)
            .user(user)
            .address(requireAddress(mandate))
            .contactDetails(contactDetails)
            .transactionId(transactionId)
            .documentNumber(documentNumber)
            .fundTransferExchanges(fundTransferExchanges)
            .funds(funds)
            .build();

    return templateEngine.process("fund_transfer_pillar_" + mandate.getPillar(), ctx);
  }

  String getFutureContributionsFundHtml(
      User user, Mandate mandate, List<Fund> funds, ContactDetails contactDetails) {
    String transactionId = UUID.randomUUID().toString();

    String documentNumber = mandate.getIdOrThrow().toString();

    Context ctx =
        ContextBuilder.builder()
            .mandate(mandate)
            .user(user)
            .address(requireAddress(mandate))
            .contactDetails(contactDetails)
            .transactionId(transactionId)
            .documentNumber(documentNumber)
            .futureContributionFundIsin(
                mandate
                    .getFutureContributionFundIsin()
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Future contribution fund ISIN missing: mandateId="
                                    + mandate.getId())))
            .funds(funds)
            .build();

    return templateEngine.process("future_contributions_fund_pillar_" + mandate.getPillar(), ctx);
  }

  String getPartialWithdrawalHtml(
      User user, Mandate mandate, ContactDetails contactDetails, List<Fund> funds) {
    String transactionId = UUID.randomUUID().toString();

    String documentNumber = mandate.getIdOrThrow().toString();

    PartialWithdrawalMandateDetails mandateDetails =
        (PartialWithdrawalMandateDetails) mandate.toSubmission().details();

    Context ctx =
        ContextBuilder.builder()
            .mandate(mandate)
            .user(user)
            .address(requireAddress(mandate))
            .contactDetails(contactDetails)
            .transactionId(transactionId)
            .documentNumber(documentNumber)
            .partialWithdrawalDetails(mandateDetails, funds)
            .build();

    return templateEngine.process(
        "partial_withdrawal_pillar_" + mandateDetails.getPillar().toInt(), ctx);
  }

  String getFundPensionOpeningHtml(User user, Mandate mandate, ContactDetails contactDetails) {
    String transactionId = UUID.randomUUID().toString();

    String documentNumber = mandate.getIdOrThrow().toString();

    FundPensionOpeningMandateDetails mandateDetails =
        (FundPensionOpeningMandateDetails) mandate.toSubmission().details();

    Context ctx =
        ContextBuilder.builder()
            .mandate(mandate)
            .user(user)
            .address(requireAddress(mandate))
            .contactDetails(contactDetails)
            .transactionId(transactionId)
            .documentNumber(documentNumber)
            .fundPensionOpeningDetails(mandateDetails)
            .build();

    return templateEngine.process(
        "fund_pension_opening_pillar_" + mandateDetails.getPillar().toInt(), ctx);
  }

  String getMandateCancellationHtml(
      User user,
      Mandate mandate,
      ContactDetails contactDetails,
      ApplicationType applicationTypeToCancel) {
    String transactionId = UUID.randomUUID().toString();
    String documentNumber = mandate.getIdOrThrow().toString();

    Context ctx =
        ContextBuilder.builder()
            .mandate(mandate)
            .user(user)
            .address(requireAddress(mandate))
            .contactDetails(contactDetails)
            .transactionId(transactionId)
            .documentNumber(documentNumber)
            .applicationTypeToCancel(applicationTypeToCancel)
            .build();

    return templateEngine.process("mandate_cancellation_mandate", ctx);
  }

  String getRateChangeHtml(
      User user, Mandate mandate, ContactDetails contactDetails, BigDecimal rate) {
    String transactionId = UUID.randomUUID().toString();
    String documentNumber = mandate.getIdOrThrow().toString();

    BigDecimal flooredRate = rate.setScale(0, RoundingMode.FLOOR);
    Context ctx =
        ContextBuilder.builder()
            .mandate(mandate)
            .newPaymentRate(flooredRate)
            .user(user)
            .address(requireAddress(mandate))
            .contactDetails(contactDetails)
            .transactionId(transactionId)
            .documentNumber(documentNumber)
            .build();

    return templateEngine.process("payment_rate_change", ctx);
  }

  private static Country requireAddress(Mandate mandate) {
    return requireNonNull(mandate.getAddress(), "Address missing: mandateId=" + mandate.getId());
  }
}
