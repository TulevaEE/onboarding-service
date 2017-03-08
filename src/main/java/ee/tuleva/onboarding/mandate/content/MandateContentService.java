package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.onboarding.mandate.Mandate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class MandateContentService {

    ClassLoaderTemplateResolver templateResolver;
    TemplateEngine templateEngine;

    @PostConstruct
    private void initialize() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/mandate/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode("XHTML");
        templateResolver.setCharacterEncoding("UTF-8");

        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);
    }

    public List<byte[]> getContentFiles(Mandate mandate) {

        return Arrays.asList(
            getFutureContributionsFundMandateContentString().getBytes(),
            getFundTransferMandateContentString().getBytes()
        );
    }

    private String getFutureContributionsFundMandateContentString() {
        Context ctx = getContextWithUserData();
        ctx.setVariable("selectedFundIsin", "EE3600019774");
        ctx.setVariable("futureContributionsFundsMandateNumber", "");

        String htmlContent = templateEngine.process("future_contributions_fund", ctx);
        log.info(htmlContent);

        return htmlContent;
    }

    private String getFundTransferMandateContentString() {
        Context ctx = getContextWithUserData();
        ctx.setVariable("sourceIsin", "EE3600103248");

        String htmlContent = templateEngine.process("fund_transfer", ctx);
//        log.info(htmlContent);

        return htmlContent;
    }

    private Context getContextWithUserData() {
        Context ctx = new Context();

        ctx.setVariable("documentDate", "2017-03-01");
        ctx.setVariable("transactionId", UUID.randomUUID());
        ctx.setVariable("documentNumber", 4020745);
        ctx.setVariable("email", "nurks2@gmail.com");
        ctx.setVariable("firstName", "Mart");
        ctx.setVariable("lastName", "Tamm");
        ctx.setVariable("idCode", "39006070274");
        ctx.setVariable("phoneNumber", "+37256268868");
        ctx.setVariable("addressRow1", "Tatari 19-17");
        ctx.setVariable("addressRow2", "TALLINN");
        ctx.setVariable("countryCode", "EE");
        ctx.setVariable("postCode", "10131");

        return ctx;
    }

}
