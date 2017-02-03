package ee.tuleva.onboarding.mandate;

import org.springframework.core.convert.converter.Converter;

import java.util.List;
import java.util.stream.Collectors;

public class CreateMandateCommandToMandateConverter implements Converter<CreateMandateCommand, Mandate> {

    @Override
    public Mandate convert(CreateMandateCommand createMandateCommand) {
        List<FundTransferExchange> fundTransferExchanges =
                createMandateCommand.getFundTransferExchanges().stream().map( fte -> {

                    return FundTransferExchange.builder()
                            .sourceFundIsin(fte.getSourceFundIsin())
                            .targetFundIsin(fte.getTargetFundIsin())
                            .percent(fte.getPercent()).build();
                }).collect(Collectors.toList());


        return Mandate.builder()
                .futureContributionFundIsin(createMandateCommand.getFutureContributionFundIsin())
                .fundTransferExchanges(fundTransferExchanges)
                .build();
    }
}
