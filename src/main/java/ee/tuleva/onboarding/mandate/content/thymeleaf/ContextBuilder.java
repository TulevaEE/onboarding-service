package ee.tuleva.onboarding.mandate.content.thymeleaf;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserPreferences;
import org.thymeleaf.context.Context;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class ContextBuilder {

    private Context ctx = new Context();

    public Context build(){
        return this.ctx;
    }

    public static ContextBuilder builder(){
        return new ContextBuilder();
    }

    public ContextBuilder user(User user) {
        this.ctx.setVariable("email", user.getEmail());
        this.ctx.setVariable("firstName", user.getFirstName());
        this.ctx.setVariable("lastName", user.getLastName());
        this.ctx.setVariable("idCode", user.getPersonalCode());
        this.ctx.setVariable("phoneNumber", user.getPhoneNumber());
        return this;
    }

    public ContextBuilder mandate(Mandate mandate){
        DateTimeFormatter formatterEst = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
        String documentDate = formatterEst.format(mandate.getCreatedDate());

        DateTimeFormatter formatterEst2 = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault());
        String documentDatePPKKAAAA = formatterEst2.format(mandate.getCreatedDate());

        this.ctx.setVariable("documentDate", documentDate);
        this.ctx.setVariable("documentDatePPKKAAAA", documentDatePPKKAAAA);

        return this;
    }

    public ContextBuilder funds(List<Fund> funds) {
        //sort because by law, funds need to be in alphabetical order
        funds.sort((Fund fund1, Fund fund2) -> fund1.getName().compareToIgnoreCase(fund2.getName()));
        this.ctx.setVariable("funds", funds);
        this.ctx.setVariable(
                "fundIsinNames",
                funds.stream().collect(Collectors.toMap(Fund::getIsin, Fund::getName))
        );
        return this;
    }

    public ContextBuilder transactionId(String transactionId) {
        this.ctx.setVariable("transactionId", transactionId);
        return this;
    }

    public ContextBuilder futureContributionFundIsin(String futureContributionFundIsin) {
        this.ctx.setVariable("selectedFundIsin", futureContributionFundIsin);
        return this;
    }

    public ContextBuilder documentNumber(String documentNumber) {
        this.ctx.setVariable("documentNumber", documentNumber);
        return this;
    }

    public ContextBuilder fundTransferExchanges(List<FundTransferExchange> fundTransferExchanges) {
        this.ctx.setVariable("fundTransferExchanges", fundTransferExchanges);
        return this;
    }

    public ContextBuilder userPreferences(UserPreferences userPreferences) {
        this.ctx.setVariable("userPreferences", userPreferences);

        this.ctx.setVariable("addressLine1", userPreferences.getAddressRow1());
        this.ctx.setVariable("addressLine2", userPreferences.getAddressRow2());
        this.ctx.setVariable("settlement", userPreferences.getAddressRow2());
        this.ctx.setVariable("countryCode", userPreferences.getCountry());
        this.ctx.setVariable("postCode", userPreferences.getPostalIndex());
        this.ctx.setVariable("districtCode", userPreferences.getDistrictCode());

        return this;
    }


}
