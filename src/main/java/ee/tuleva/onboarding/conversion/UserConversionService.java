package ee.tuleva.onboarding.conversion;

import ee.tuleva.onboarding.account.AccountStatementService;
import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.processor.implementation.EpisService;
import ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication.TransferApplicationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication.MandateApplicationStatus.PENDING;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserConversionService {

    private final AccountStatementService accountStatementService;
    private final EpisService episService;
    private final FundRepository fundRepository;

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
        return getIsinsOfPendingTransfersToConvertedFundManager()
                .containsAll(unConvertedIsins(fundBalances));
  }

    List<String> getIsinsOfPendingTransfersToConvertedFundManager() {
        return getPendingTransfers().stream().filter(transferApplicationDTO ->
                fundRepository
                        .findByIsin(transferApplicationDTO.getTargetFundIsin())
                        .getFundManager()
                        .getName()
                        .equalsIgnoreCase(CONVERTED_FUND_MANAGER_NAME)

        ).map(transferApplicationDTO -> transferApplicationDTO.getSourceFundIsin())
                .collect(Collectors.toList());
    }

    List<TransferApplicationDTO> getPendingTransfers() {
        List<TransferApplicationDTO> transferApplications = episService.getFundTransferExchanges();

        return transferApplications.stream().filter(transferApplicationDTO ->
                transferApplicationDTO.getStatus().equals(PENDING)
        ).collect(Collectors.toList());
    }

    List<String> unConvertedIsins(List<FundBalance> fundBalances) {
        return fundBalances.stream()
                .filter(
                        fundBalance ->
                                !fundBalance.getFund()
                                        .getFundManager()
                                        .getName()
                                        .equalsIgnoreCase(CONVERTED_FUND_MANAGER_NAME)
                )
                .map(fundBalance -> fundBalance.getFund().getIsin())
                .collect(Collectors.toList());
    }

}
