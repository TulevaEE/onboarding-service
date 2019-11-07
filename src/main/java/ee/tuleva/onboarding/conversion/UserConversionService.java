package ee.tuleva.onboarding.conversion;

import ee.tuleva.onboarding.account.AccountStatementService;
import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.conversion.ConversionResponse.Conversion;
import ee.tuleva.onboarding.mandate.transfer.TransferExchange;
import ee.tuleva.onboarding.mandate.transfer.TransferExchangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static ee.tuleva.onboarding.epis.mandate.MandateApplicationStatus.PENDING;
import static java.util.stream.Collectors.toList;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserConversionService {

    private final AccountStatementService accountStatementService;
    private final TransferExchangeService transferExchangeService;

    private static final String CONVERTED_FUND_MANAGER_NAME = "Tuleva";
    public static final String EXIT_RESTRICTED_FUND = "EE3600109484";


    public ConversionResponse getConversion(Person person) {
        List<FundBalance> fundBalances = accountStatementService.getAccountStatement(person);

        return ConversionResponse.builder()
            .secondPillar(Conversion.builder()
                .selectionComplete(isSelectionComplete(fundBalances, 2))
                .transfersComplete(isTransfersComplete(fundBalances, 2, person))
                .build()
            ).thirdPillar(Conversion.builder()
                .selectionComplete(isSelectionComplete(fundBalances, 3))
                .transfersComplete(isTransfersComplete(fundBalances, 3, person))
                .build()
            ).build();
    }

    private boolean isSelectionComplete(List<FundBalance> fundBalances, Integer pillar) {
        return filter(fundBalances, pillar).findFirst().isPresent() &&
            filter(fundBalances, pillar).anyMatch(FundBalance::isActiveContributions) &&
            filter(fundBalances, pillar).filter(FundBalance::isActiveContributions)
                .allMatch(fundBalance ->
                    fundBalance.getFund()
                        .getFundManager()
                        .getName()
                        .equalsIgnoreCase(CONVERTED_FUND_MANAGER_NAME)
                );
    }

    private Stream<FundBalance> filter(List<FundBalance> fundBalances, Integer pillar) {
        return fundBalances.stream().filter(fundBalance -> pillar.equals(fundBalance.getPillar()));
    }

    private boolean isTransfersComplete(List<FundBalance> fundBalances, Integer pillar, Person person) {
        return getIsinsOfFullPendingTransfersToConvertedFundManager(person, fundBalances, pillar)
            .containsAll(unConvertedIsins(fundBalances, pillar));
    }

    private List<String> getIsinsOfFullPendingTransfersToConvertedFundManager(Person person, List<FundBalance> fundBalances, Integer pillar) {
        return getPendingTransfers(person).stream()
            .filter(transferExchange -> pillar.equals(transferExchange.getPillar()))
            .filter(transferExchange ->
                transferExchange
                    .getTargetFund()
                    .getFundManager()
                    .getName()
                    .equalsIgnoreCase(CONVERTED_FUND_MANAGER_NAME) &&
                    amountMatches(transferExchange, fundBalances)
            )
            .map(transferExchange -> transferExchange.getSourceFund().getIsin())
            .collect(toList());
    }

    private boolean amountMatches(TransferExchange transferExchange, List<FundBalance> fundBalances) {
        if (transferExchange.getPillar() == 2) {
            return transferExchange.getAmount().intValue() == 1;
        }
        if (transferExchange.getPillar() == 3) {
            FundBalance fundBalance = fundBalance(transferExchange, fundBalances);
            return transferExchange.getAmount().equals(fundBalance.getTotalValue());
        }
        throw new IllegalStateException("Invalid pillar: " + transferExchange.getPillar());
    }

    private FundBalance fundBalance(TransferExchange transferExchange, List<FundBalance> fundBalances) {
        return fundBalances.stream()
            .filter(fundBalance -> transferExchange.getSourceFund().getIsin().equals(fundBalance.getIsin()))
            .findFirst()
            .orElse(FundBalance.builder().build());
    }

    private List<TransferExchange> getPendingTransfers(Person person) {
        return transferExchangeService.get(person).stream()
            .filter(transferExchange -> transferExchange.getStatus().equals(PENDING))
            .collect(toList());
    }

    private List<String> unConvertedIsins(List<FundBalance> fundBalances, Integer pillar) {
        return fundBalances.stream()
            .filter(fundBalance -> pillar.equals(fundBalance.getPillar()))
            .filter(fundBalance ->
                !fundBalance.getFund()
                    .getFundManager()
                    .getName()
                    .equalsIgnoreCase(CONVERTED_FUND_MANAGER_NAME)
                    && fundBalance.getValue().compareTo(BigDecimal.ZERO) > 0
                    && !EXIT_RESTRICTED_FUND.equals(fundBalance.getIsin())
            )
            .map(fundBalance -> fundBalance.getFund().getIsin())
            .collect(toList());
    }

}
