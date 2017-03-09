package ee.tuleva.onboarding.mandate.content.thymeleaf;

import ee.tuleva.domain.fund.Fund;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import org.thymeleaf.context.Context;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ContextBuilder {

    private Context ctx = new Context();
    private User user;
    private Mandate mandate;

    public Context build(){
        return this.ctx;
    }

    public static ContextBuilder builder(){
        return new ContextBuilder();
    }

    public ContextBuilder user(User user) {
        this.user = user;

        this.ctx.setVariable("email", user.getEmail());
        this.ctx.setVariable("firstName", user.getFirstName());
        this.ctx.setVariable("lastName", user.getLastName());
        this.ctx.setVariable("idCode", user.getPersonalCode());
        this.ctx.setVariable("phoneNumber", user.getPhoneNumber());
        this.ctx.setVariable("addressLine1", "Tatari 19-17");
        this.ctx.setVariable("addressLine2", "TALLINN");
        this.ctx.setVariable("settlement", "TALLINN");
        this.ctx.setVariable("countryCode", "EE");
        this.ctx.setVariable("postCode", "10131");
        this.ctx.setVariable("districtCode", "123");

        return this;
    }

    public ContextBuilder mandate(Mandate mandate){
        this.mandate = mandate;
        DateTimeFormatter formatterEst = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
        String documentDate = formatterEst.format(mandate.getCreatedDate());

        DateTimeFormatter formatterEst2 = DateTimeFormatter.ofPattern("MM.dd.yyyy").withZone(ZoneId.systemDefault());
        String documentDatePPKKAAAA = formatterEst2.format(mandate.getCreatedDate());

        this.ctx.setVariable("documentDate", documentDate);
        this.ctx.setVariable("documentDatePPKKAAAA", documentDatePPKKAAAA);

        //add isin name map

        return this;
    }

    public ContextBuilder funds(List<Fund> funds) {
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



}
