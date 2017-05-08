package ee.tuleva.onboarding.conversion;

import ee.tuleva.onboarding.account.AccountStatementService;
import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.auth.principal.Person;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserConversionService {

    private final AccountStatementService accountStatementService;

    private static final String CONVERTED_FUND_MANAGER_NAME = "Tuleva";

    public ConversionResponse getConversion(Person person) {

        List<FundBalance> fundBalances =
                accountStatementService.getMyPensionAccountStatement(person, UUID.randomUUID());

        return ConversionResponse.builder()
                .selectionComplete(isSelectionComplete(fundBalances))
                .transfersComplete(isTransfersComplete(fundBalances))
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

    boolean isTransfersComplete(List<FundBalance> fundBalances) {
        return !fundBalances.stream()
                .anyMatch(
                        fundBalance ->
                                !fundBalance.getFund()
                                        .getFundManager()
                                        .getName()
                                        .equalsIgnoreCase(CONVERTED_FUND_MANAGER_NAME)
                );
    }

}
