package ee.tuleva.onboarding.conversion;

import ee.tuleva.onboarding.account.AccountStatementService;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.mandate.transfer.TransferExchange;
import ee.tuleva.onboarding.mandate.transfer.TransferExchangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

import static ee.tuleva.onboarding.epis.mandate.MandateApplicationStatus.PENDING;
import static java.util.stream.Collectors.toList;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserConversionService {

    private final AccountStatementService accountStatementService;
    private final TransferExchangeService transferExchangeService;

    private static final String CONVERTED_FUND_MANAGER_NAME = "Tuleva";

    public ConversionResponse getConversion(Person person) {

        List<FundBalance> fundBalances = accountStatementService.getAccountStatement(person);

        return ConversionResponse.builder()
                .selectionComplete(isSelectionComplete(fundBalances))
                .transfersComplete(isTransfersComplete(person, fundBalances))
                .build();
    }

    boolean isSelectionComplete(List<FundBalance> fundBalances) {
        return fundBalances.stream()
                .anyMatch(
                        fundBalance ->
                                fundBalance.getFund()
                                        .getFundManager()
                                        .getName()
                                        .equalsIgnoreCase(CONVERTED_FUND_MANAGER_NAME)
                                        && fundBalance.isActiveContributions()
                );
    }

    boolean isTransfersComplete(Person person, List<FundBalance> fundBalances) {
        return getIsinsOfFullPendingTransfersToConvertedFundManager(person)
                .containsAll(unConvertedIsins(fundBalances));
    }

    List<String> getIsinsOfFullPendingTransfersToConvertedFundManager(Person person) {
        return getPendingTransfers(person).stream().filter(transferExchange -> {
                    return transferExchange
                            .getTargetFund()
                            .getFundManager()
                            .getName()
                            .equalsIgnoreCase(CONVERTED_FUND_MANAGER_NAME) &&
                            transferExchange.getAmount().intValue() == 1;

                }
        ).map(transferExchange -> transferExchange.getSourceFund().getIsin())
                .collect(toList());
    }

    List<TransferExchange> getPendingTransfers(Person person) {

        return transferExchangeService.get(person).stream()
                .filter(transferExchange ->
                        transferExchange.getStatus().equals(PENDING)
                ).collect(toList());
    }

    List<String> unConvertedIsins(List<FundBalance> fundBalances) {
        return fundBalances.stream()
                .filter(fundBalance -> !fundBalance.getFund()
                        .getFundManager()
                        .getName()
                        .equalsIgnoreCase(CONVERTED_FUND_MANAGER_NAME) &&
                        fundBalance.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(fundBalance -> fundBalance.getFund().getIsin())
                .collect(toList());
    }

}
