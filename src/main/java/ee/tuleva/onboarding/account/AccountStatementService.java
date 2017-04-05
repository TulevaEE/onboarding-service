package ee.tuleva.onboarding.account;

import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType;
import ee.eesti.xtee6.kpr.PensionAccountBalanceType;
import ee.eesti.xtee6.kpr.PersonalSelectionResponseType;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.kpr.KPRClient;
import ee.tuleva.onboarding.mandate.statistics.FundValueStatistics;
import ee.tuleva.onboarding.mandate.statistics.FundValueStatisticsRepository;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountStatementService {

    private final KPRClient kprClient;
    private final FundRepository fundRepository;
    private final KPRUnitsOsakudToFundBalanceConverter kprUnitsOsakudToFundBalanceConverter;
    private final FundValueStatisticsRepository fundValueStatisticsRepository;

    public List<FundBalance> getMyPensionAccountStatement(User user, UUID statisticsIdentifier) {
        List<FundBalance> fundBalances = convertXRoadResponse(getPensionAccountBalance(user));

        fundBalances = handleActiveFundBalance(fundBalances, getActiveFundName(user));
        saveFundValueStatistics(fundBalances, statisticsIdentifier);

        return fundBalances;
    }

    private void saveFundValueStatistics(List<FundBalance> fundBalances, UUID fundValueStatisticsIdentifier) {
        fundBalances.stream().map( fundBalance -> FundValueStatistics.builder()
                .isin(fundBalance.getFund().getIsin())
                .value(fundBalance.getValue())
                .identifier(fundValueStatisticsIdentifier)
                .build())
                .forEach(fundValueStatistics -> {
                    fundValueStatisticsRepository.save(fundValueStatistics);
                });
    }

    private PensionAccountBalanceResponseType getPensionAccountBalance(User user) {
        PensionAccountBalanceType request = new PensionAccountBalanceType();
        request.setBalanceDate(null);
        return kprClient.pensionAccountBalance(request, user.getPersonalCode());
    }

    private List<FundBalance> convertXRoadResponse(PensionAccountBalanceResponseType response) {
        return
                response.getUnits().getBalance().stream()
                        .map(b->kprUnitsOsakudToFundBalanceConverter.convert(b))
                        .collect(Collectors.toList());
    }

    private String getActiveFundName(User user) {
        PersonalSelectionResponseType csdPersonalSelection = kprClient.personalSelection(user.getPersonalCode());
        return csdPersonalSelection.getPensionAccount().getSecurityName();
    }

    private List<FundBalance> handleActiveFundBalance(List<FundBalance> fundBalances, String activeFundName) {
        Optional<FundBalance> activeFundBalance = fundBalances.stream().filter(fb -> fb.getFund().getName().equals(activeFundName)).findFirst();
        activeFundBalance.ifPresent( fb -> fb.setActiveContributions(true));
        if(!activeFundBalance.isPresent()) {
            fundBalances.add(constructActiveFundBalance(activeFundName));
        }
        return fundBalances;
    }

    private FundBalance constructActiveFundBalance(String activeFundName) {
        FundBalance activeFundBalance = FundBalance.builder()
                .fund(
                        Fund.builder().name(activeFundName).build()
                )
                .value(BigDecimal.ZERO)
                .currency("EUR")
                .activeContributions(true)
                .build();

        Fund activeFund = fundRepository.findByNameIgnoreCase(activeFundName);
        if (activeFund != null) {
            activeFundBalance.getFund().setIsin(activeFund.getIsin());
            activeFundBalance.getFund().setManagementFeeRate(activeFund.getManagementFeeRate());
        }

        return activeFundBalance;
    }



}
