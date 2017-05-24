package ee.tuleva.onboarding.conversion;

import ee.tuleva.onboarding.account.AccountStatementService;
import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.processor.implementation.EpisService;
import ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication.TransferExchangeDTO;
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
        return getIsinsOfPendingTransfersToConvertedFundManager(person)
                .containsAll(unConvertedIsins(fundBalances));
  }

    List<String> getIsinsOfPendingTransfersToConvertedFundManager(Person person) {
        return getPendingTransfers(person).stream().filter(transferExchangeDTO ->
                fundRepository
                        .findByIsin(transferExchangeDTO.getTargetFundIsin())
                        .getFundManager()
                        .getName()
                        .equalsIgnoreCase(CONVERTED_FUND_MANAGER_NAME)

        ).map(transferExchangeDTO -> transferExchangeDTO.getSourceFundIsin())
                .collect(Collectors.toList());
    }

    List<TransferExchangeDTO> getPendingTransfers(Person person) {
        List<TransferExchangeDTO> transferApplications = episService.getTransferApplications(person);

        return transferApplications.stream().filter(transferExchangeDTO ->
                transferExchangeDTO.getStatus().equals(PENDING)
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
