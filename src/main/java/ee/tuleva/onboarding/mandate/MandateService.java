package ee.tuleva.onboarding.mandate;

import ee.tuleva.domain.fund.Fund;
import ee.tuleva.domain.fund.FundRepository;
import ee.tuleva.onboarding.user.User;
import org.springframework.stereotype.Component;

@Component
public class MandateService {

    FundRepository fundRepository;

    public Mandate save(User user, CreateMandateCommand createMandateCommand) {
/*
        createMandateCommand.fundTransferExchanges.stream().forEach( fte -> {

        });

        FundTransferExchange fundTransferExchange = FundTransferExchange.builder()
                .

        Fund futureContributionFund = fundRepository.findOne(createMandateCommand.futureContributionFundId);

        Mandate.builder()
                .user(user)
                .futureContributionFund(futureContributionFund)
                .fundTransferExchanges()
*/
        return null;
    }

}
