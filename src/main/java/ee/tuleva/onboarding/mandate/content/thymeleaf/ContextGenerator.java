package ee.tuleva.onboarding.mandate.content.thymeleaf;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import org.thymeleaf.context.Context;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ContextGenerator {

    public static Context addUserDate(Context ctx, User user) {
        ctx.setVariable("email", user.getEmail());
        ctx.setVariable("firstName", user.getFirstName());
        ctx.setVariable("lastName", user.getLastName());
        ctx.setVariable("idCode", user.getPersonalCode());
        ctx.setVariable("phoneNumber", user.getPhoneNumber());
        ctx.setVariable("addressLine1", "Tatari 19-17");
        ctx.setVariable("addressLine2", "TALLINN");
        ctx.setVariable("settlement", "TALLINN");
        ctx.setVariable("countryCode", "EE");
        ctx.setVariable("postCode", "10131");
        ctx.setVariable("districtCode", "123");

        return ctx;
    }

    public static Context addDatesToContext(Context ctx, Mandate mandate){

        DateTimeFormatter formatterEst = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
        String documentDate = formatterEst.format(mandate.getCreatedDate());

        DateTimeFormatter formatterEst2 = DateTimeFormatter.ofPattern("MM.dd.yyyy").withZone(ZoneId.systemDefault());
        String documentDatePPKKAAAA = formatterEst2.format(mandate.getCreatedDate());

        ctx.setVariable("documentDate", documentDate);
        ctx.setVariable("documentDatePPKKAAAA", documentDatePPKKAAAA);

        return ctx;
    }
}
