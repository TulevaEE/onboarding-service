package ee.tuleva.onboarding.mandate.command;

import ee.tuleva.onboarding.account.AccountStatementService;
import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Component
@RequiredArgsConstructor
public class CreateMandateCommandToMandateConverter implements Converter<CreateMandateCommandWithUser, Mandate> {

    private final AccountStatementService accountStatementService;

    @Override
    public Mandate convert(CreateMandateCommandWithUser createMandateCommandWithUser) {
        Mandate mandate = new Mandate();
        mandate.setUser(createMandateCommandWithUser.getUser());

        val createMandateCommand = createMandateCommandWithUser.getCreateMandateCommand();

        if (createMandateCommand.getPillar() != null) {
            mandate.setPillar(createMandateCommand.getPillar());
        } else {
            // Temporary until frontend will give us the active pillar
            mandate.setPillar(2);
        }

        List<FundTransferExchange> fundTransferExchanges =
            createMandateCommand.getFundTransferExchanges()
                .stream()
                .map(exchange -> FundTransferExchange.builder()
                    .sourceFundIsin(exchange.getSourceFundIsin())
                    .targetFundIsin(exchange.getTargetFundIsin())
                    .amount(getAmount(exchange, mandate))
                    .mandate(mandate)
                    .build())
                .collect(toList());

        mandate.setFundTransferExchanges(fundTransferExchanges);
        mandate.setFutureContributionFundIsin(createMandateCommand.getFutureContributionFundIsin());

        return mandate;
    }

    private BigDecimal getAmount(MandateFundTransferExchangeCommand exchange, Mandate mandate) {
        val pillar = mandate.getPillar();
        if (pillar.equals(2)) {
            return exchange.getAmount();
        } else if (pillar.equals(3)) {
            val statement = accountStatementService.getAccountStatement(mandate.getUser());
            val balance = getFundBalance(statement, exchange.getSourceFundIsin());
            val exchangeAmount = balance.getUnits().multiply(exchange.getAmount());
            return exchangeAmount.setScale(3, RoundingMode.HALF_UP);
        } else {
            throw new IllegalStateException("Unknown pillar " + pillar);
        }
    }

    private FundBalance getFundBalance(List<FundBalance> accountStatement, String isin) {
        return accountStatement.stream()
            .filter(fundBalance -> fundBalance.getFund().getIsin().equals(isin))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Fund not found: " + isin));
    }
}
