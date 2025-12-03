package ee.tuleva.onboarding.mandate.content.thymeleaf;

import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.mandate.details.FundPensionOpeningMandateDetails;
import ee.tuleva.onboarding.epis.mandate.details.PartialWithdrawalMandateDetails;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.thymeleaf.context.Context;

public class ContextBuilder {

  private Context ctx = new Context();

  public static ContextBuilder builder() {
    return new ContextBuilder();
  }

  public Context build() {
    return ctx;
  }

  public ContextBuilder user(User user) {
    ctx.setVariable("email", user.getEmail());
    ctx.setVariable("firstName", user.getFirstName());
    ctx.setVariable("lastName", user.getLastName());
    ctx.setVariable("idCode", user.getPersonalCode());
    ctx.setVariable("phoneNumber", user.getPhoneNumber());
    return this;
  }

  public ContextBuilder mandate(Mandate mandate) {
    DateTimeFormatter formatterEst =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    String documentDate = formatterEst.format(mandate.getCreatedDate());

    DateTimeFormatter formatterEst2 =
        DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneOffset.UTC);
    String documentDatePPKKAAAA = formatterEst2.format(mandate.getCreatedDate());

    ctx.setVariable("documentDate", documentDate);
    ctx.setVariable("documentDatePPKKAAAA", documentDatePPKKAAAA);

    return this;
  }

  public ContextBuilder funds(List<Fund> funds) {
    List<Fund> sortableFunds = new ArrayList<>(funds);
    // sort because by law, funds need to be in alphabetical order
    sortableFunds.sort(
        (Fund fund1, Fund fund2) ->
            fund1.getNameEstonian().compareToIgnoreCase(fund2.getNameEstonian()));
    ctx.setVariable("funds", sortableFunds);
    ctx.setVariable(
        "fundIsinNames",
        sortableFunds.stream().collect(Collectors.toMap(Fund::getIsin, Fund::getNameEstonian)));
    return this;
  }

  public ContextBuilder transactionId(String transactionId) {
    ctx.setVariable("transactionId", transactionId);
    return this;
  }

  public ContextBuilder futureContributionFundIsin(String futureContributionFundIsin) {
    ctx.setVariable("selectedFundIsin", futureContributionFundIsin);
    return this;
  }

  public ContextBuilder documentNumber(String documentNumber) {
    ctx.setVariable("documentNumber", documentNumber);
    return this;
  }

  public ContextBuilder fundPensionOpeningDetails(FundPensionOpeningMandateDetails details) {
    ctx.setVariable("fundPensionOpeningDetails", details);
    ctx.setVariable("bankAccountDetails", details.getBankAccountDetails());
    return this;
  }

  public ContextBuilder partialWithdrawalDetails(
      PartialWithdrawalMandateDetails details, List<Fund> funds) {
    ctx.setVariable("partialWithdrawalDetails", details);
    ctx.setVariable("bankAccountDetails", details.getBankAccountDetails());
    return this.funds(funds);
  }

  public ContextBuilder fundTransferExchanges(List<FundTransferExchange> fundTransferExchanges) {
    ctx.setVariable("fundTransferExchanges", fundTransferExchanges);
    return this;
  }

  public ContextBuilder applicationTypeToCancel(ApplicationType applicationTypeToCancel) {
    ctx.setVariable("applicationTypeToCancel", applicationTypeToCancel);
    return this;
  }

  public ContextBuilder contactDetails(ContactDetails contactDetails) {
    ctx.setVariable("contactDetails", contactDetails);
    if (ctx.getVariable("email") == null) {
      ctx.setVariable("email", contactDetails.getEmail());
    }
    return this;
  }

  public ContextBuilder address(Country address) {
    ctx.setVariable("countryCode", address.getCountryCode());
    return this;
  }

  public ContextBuilder newPaymentRate(BigDecimal rate) {
    ctx.setVariable("newPaymentRate", rate);
    return this;
  }
}
