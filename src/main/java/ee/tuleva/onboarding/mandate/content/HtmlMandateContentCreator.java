package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.onboarding.config.TemplateEngineWrapper;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.content.thymeleaf.ContextBuilder;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.preferences.UserPreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class HtmlMandateContentCreator implements MandateContentCreator {

    private final TemplateEngineWrapper templateEngine;

    @Override
    public List<MandateContentFile> getContentFiles(User user, Mandate mandate, List<Fund> funds, UserPreferences userPreferences) {
        List<MandateContentFile> files = new ArrayList<>(getFundTransferMandateContentFiles(
                user, mandate, funds, userPreferences
        ));

        if (mandate.getFutureContributionFundIsin().isPresent()) {
            files.add(getFutureContributionsFundMandateContentFile(
                    user, mandate, funds, userPreferences
            ));
        }

        return files;
    }

    private MandateContentFile getFutureContributionsFundMandateContentFile(
            User user, Mandate mandate, List<Fund> funds, UserPreferences userPreferences) {
        String transactionId = UUID.randomUUID().toString();

        String documentNumber = mandate.getId().toString();

        Context ctx = ContextBuilder.builder()
                .mandate(mandate)
                .user(user)
                .userPreferences(userPreferences)
                .transactionId(transactionId)
                .documentNumber(documentNumber)
                .futureContributionFundIsin(mandate.getFutureContributionFundIsin().orElse(null))
                .funds(funds)
                .build();

        String htmlContent = templateEngine.process("/mandate/future_contributions_fund", ctx);

        return MandateContentFile.builder()
                .name("valikuavaldus_" + documentNumber + ".html")
                .mimeType("text/html")
                .content(htmlContent.getBytes())
                .build();
    }

    private List<MandateContentFile> getFundTransferMandateContentFiles(
            User user, Mandate mandate, List<Fund> funds, UserPreferences userPreferences) {
        return allocateAndGetFundTransferFiles(
                getPrintableFundExchangeStructure(mandate),
                user, mandate, funds, userPreferences
        );
    }

    private List<MandateContentFile> allocateAndGetFundTransferFiles(
            Map<String, List<FundTransferExchange>> exchangeMap,
            User user, Mandate mandate, List<Fund> funds, UserPreferences userPreferences
            ) {
        return exchangeMap.keySet().stream().map(
                sourceIsin -> getFundTransferMandateContentFile(new ArrayList<>(
                        exchangeMap.get(sourceIsin)
                ), user, mandate, funds, userPreferences)).collect(Collectors.toList());
    }

    private Map<String, List<FundTransferExchange>> getPrintableFundExchangeStructure(Mandate mandate) {
        Map<String, List<FundTransferExchange>> exchangeMap = new HashMap<>();

        mandate.getFundTransferExchanges().stream()
                .filter(fte -> fte.getAmount().compareTo(BigDecimal.ZERO) == 1)
                .forEach(exchange -> {
            if (!exchangeMap.containsKey(exchange.getSourceFundIsin())) {
                exchangeMap.put(exchange.getSourceFundIsin(), new ArrayList<>());
            }
            exchangeMap.get(exchange.getSourceFundIsin()).add(exchange);
        });

        return exchangeMap;
    }

    private MandateContentFile getFundTransferMandateContentFile(
            List<FundTransferExchange> fundTransferExchanges,
            User user, Mandate mandate, List<Fund> funds, UserPreferences userPreferences
    ) {
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

        String htmlContent = templateEngine.process("/mandate/fund_transfer", ctx);

        return MandateContentFile.builder()
                .name("vahetuseavaldus_" + documentNumber + ".html")
                .mimeType("text/html")
                .content(htmlContent.getBytes())
                .build();
    }

}
