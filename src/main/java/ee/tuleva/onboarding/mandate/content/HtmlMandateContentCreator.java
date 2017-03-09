package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.domain.fund.Fund;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.content.thymeleaf.ContextGenerator;
import ee.tuleva.onboarding.user.User;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class HtmlMandateContentCreator implements MandateContentCreator {

    TemplateEngine templateEngine;
    User user;
    Mandate mandate;
    List<Fund> funds;

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

    @Override
    public List<MandateContentFile> getContentFiles(User user, Mandate mandate, List<Fund> funds) {
        this.user = user;
        this.mandate = mandate;
        this.funds = funds;

        List<MandateContentFile> files = new ArrayList<MandateContentFile>(Arrays.asList(getFutureContributionsFundMandateContentFile(mandate)));
        files.addAll(getFundTransferMandateContentFiles(mandate));

        return files;
    }

    private MandateContentFile getFutureContributionsFundMandateContentFile(Mandate mandate) {
        Context ctx = new Context();
        ctx = ContextGenerator.addUserDate(ctx, user);

        String transactionId = UUID.randomUUID().toString();
        ctx.setVariable("transactionId", transactionId);
        ctx.setVariable("selectedFundIsin", mandate.getFutureContributionFundIsin());
        ctx.setVariable("documentNumber", mandate.getId());

        ctx = ContextGenerator.addDatesToContext(ctx, mandate);

        funds.sort((Fund fund1, Fund fund2) -> fund1.getName().compareToIgnoreCase(fund2.getName()));

        ctx.setVariable("funds", funds);

        String htmlContent = templateEngine.process("future_contributions_fund", ctx);

        return MandateContentFile.builder()
                .name("valikuavaldus.html")
                .mimeType("text/html")
                .content(htmlContent.getBytes())
                .build();
    }

    private List<MandateContentFile> getFundTransferMandateContentFiles(Mandate mandate) {

        Map<String, List<FundTransferExchange>> exchangeMap = new HashMap<>();

        mandate.getFundTransferExchanges().stream().forEach(exchange -> {
            if (!exchangeMap.containsKey(exchange.getSourceFundIsin())) {
               exchangeMap.put(exchange.getSourceFundIsin(), new ArrayList<>());
            }

            exchangeMap.get(exchange.getSourceFundIsin()).add(exchange);

        });

        List<MandateContentFile> fundTransferFiles =
            exchangeMap.keySet().stream().map(
                    sourceIsin -> getFundTransferMandateContentFile(new ArrayList<>(
                            exchangeMap.get(sourceIsin)
                    ))).collect(Collectors.toList());

        return fundTransferFiles;
    }

    private MandateContentFile getFundTransferMandateContentFile(List<FundTransferExchange> fundTransferExchanges) {
        Context ctx = new Context();
        ctx = ContextGenerator.addUserDate(ctx, user);

        Long documentNumber = fundTransferExchanges.get(0).getId();

        String transactionId = UUID.randomUUID().toString();
        ctx.setVariable("transactionId", transactionId);
        ctx.setVariable("fundTransferExchanges", fundTransferExchanges);
        ctx.setVariable("documentNumber", documentNumber);
        ctx = ContextGenerator.addDatesToContext(ctx, mandate);

        String htmlContent = templateEngine.process("fund_transfer", ctx);

        return MandateContentFile.builder()
                .name("vahetuseavaldus_" + documentNumber + ".html")
                .mimeType("text/html")
                .content(htmlContent.getBytes())
                .build();
    }


}
