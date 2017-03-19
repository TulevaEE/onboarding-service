package ee.tuleva.onboarding.mandate.command;

import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CreateMandateCommandToMandateConverter implements Converter<CreateMandateCommand, Mandate> {

    @Override
    public Mandate convert(CreateMandateCommand createMandateCommand) {
        Mandate mandate = new Mandate();

        List<FundTransferExchange> fundTransferExchanges =
                createMandateCommand.getFundTransferExchanges().stream().map(exchange -> FundTransferExchange.builder()
                        .sourceFundIsin(exchange.getSourceFundIsin())
                        .targetFundIsin(exchange.getTargetFundIsin())
                        .amount(exchange.getAmount())
                        .mandate(mandate)
                        .build()).collect(Collectors.toList());

        mandate.setFundTransferExchanges(fundTransferExchanges);
        mandate.setFutureContributionFundIsin(createMandateCommand.getFutureContributionFundIsin());

        return mandate;
    }
}
