package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.content.thymeleaf.ContextBuilder;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserPreferences;
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
    UserPreferences userPreferences;

    @Override
    public List<MandateContentFile> getContentFiles(User user, Mandate mandate, List<Fund> funds, UserPreferences userPreferences) {
        this.user = user;
        this.mandate = mandate;
        this.userPreferences = userPreferences;
        this.funds = funds;


        List<MandateContentFile> files = new ArrayList<MandateContentFile>(Arrays.asList(getFutureContributionsFundMandateContentFile(mandate)));
        files.addAll(getFundTransferMandateContentFiles(mandate));

        return files;
    }

    private MandateContentFile getFutureContributionsFundMandateContentFile(Mandate mandate) {
        String transactionId = UUID.randomUUID().toString();

        String documentNumber = mandate.getId().toString();

        Context ctx = ContextBuilder.builder()
                .mandate(mandate)
                .user(user)
                .userPreferences(userPreferences)
                .transactionId(transactionId)
                .documentNumber(documentNumber)
                .futureContributionFundIsin(mandate.getFutureContributionFundIsin())
                .funds(funds)
                .build();

        String htmlContent = templateEngine.process("future_contributions_fund", ctx);

        return MandateContentFile.builder()
                .name("valikuavaldus_" + documentNumber + ".html")
                .mimeType("text/html")
                .content(htmlContent.getBytes())
                .build();
    }

    private List<MandateContentFile> getFundTransferMandateContentFiles(Mandate mandate) {
        return allocateAndGetFundTransferFiles(
                getPrintableFundExchangeStructure(mandate)
        );
    }

    private List<MandateContentFile> allocateAndGetFundTransferFiles(Map<String, List<FundTransferExchange>> exchangeMap) {
        return exchangeMap.keySet().stream().map(
                sourceIsin -> getFundTransferMandateContentFile(new ArrayList<>(
                        exchangeMap.get(sourceIsin)
                ))).collect(Collectors.toList());
    }

    private Map<String, List<FundTransferExchange>> getPrintableFundExchangeStructure(Mandate mandate) {
        Map<String, List<FundTransferExchange>> exchangeMap = new HashMap<>();

        mandate.getFundTransferExchanges().stream().forEach(exchange -> {
            if (!exchangeMap.containsKey(exchange.getSourceFundIsin())) {
                exchangeMap.put(exchange.getSourceFundIsin(), new ArrayList<>());
            }
            exchangeMap.get(exchange.getSourceFundIsin()).add(exchange);
        });

        return exchangeMap;
    }

    private MandateContentFile getFundTransferMandateContentFile(List<FundTransferExchange> fundTransferExchanges) {
        String transactionId = UUID.randomUUID().toString();
        String documentNumber = fundTransferExchanges.get(0).getId().toString();

        Context ctx = ContextBuilder.builder()
                .mandate(mandate)
                .user(user)
                .userPreferences(userPreferences)
                .transactionId(transactionId)
                .documentNumber(documentNumber)
                .fundTransferExchanges(fundTransferExchanges)
                .funds(funds)
                .build();

        String htmlContent = templateEngine.process("fund_transfer", ctx);

        return MandateContentFile.builder()
                .name("vahetuseavaldus_" + documentNumber + ".html")
                .mimeType("text/html")
                .content(htmlContent.getBytes())
                .build();
    }

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


}
