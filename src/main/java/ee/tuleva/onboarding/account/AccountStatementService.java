package ee.tuleva.onboarding.account;

import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType;
import ee.eesti.xtee6.kpr.PensionAccountBalanceType;
import ee.eesti.xtee6.kpr.PersonalSelectionResponseType;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.kpr.KPRClient;
import ee.tuleva.onboarding.mandate.statistics.FundValueStatistics;
import ee.tuleva.onboarding.mandate.statistics.FundValueStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatementService {

    private final KPRClient kprClient;
    private final FundRepository fundRepository;
    private final KPRUnitsOsakudToFundBalanceConverter kprUnitsOsakudToFundBalanceConverter;
    private final FundValueStatisticsRepository fundValueStatisticsRepository;

    public List<FundBalance> getMyPensionAccountStatement(Person person, UUID statisticsIdentifier) {
        List<FundBalance> fundBalances = convertXRoadResponse(getPensionAccountBalance(person));

        fundBalances = handleActiveFundBalance(fundBalances, getActiveFundName(person));
        saveFundValueStatistics(fundBalances, statisticsIdentifier);

        return fundBalances;
    }

    private void saveFundValueStatistics(List<FundBalance> fundBalances, UUID fundValueStatisticsIdentifier) {
        fundBalances.stream().map( fundBalance -> FundValueStatistics.builder()
                .isin(fundBalance.getFund().getIsin())
                .value(fundBalance.getValue())
                .identifier(fundValueStatisticsIdentifier)
                .build())
                .forEach(fundValueStatisticsRepository::save);
    }

    private PensionAccountBalanceResponseType getPensionAccountBalance(Person person) {
        PensionAccountBalanceType request = new PensionAccountBalanceType();
        request.setBalanceDate(null);
        return kprClient.pensionAccountBalance(request, person.getPersonalCode());
    }

    private List<FundBalance> convertXRoadResponse(PensionAccountBalanceResponseType response) {
        return
                response.getUnits().getBalance().stream()
                        .map(kprUnitsOsakudToFundBalanceConverter::convert)
                        .collect(Collectors.toList());
    }

    private String getActiveFundName(Person person) {
        PersonalSelectionResponseType csdPersonalSelection = kprClient.personalSelection(person.getPersonalCode());
        return csdPersonalSelection.getPensionAccount().getSecurityName();
    }

    private List<FundBalance> handleActiveFundBalance(List<FundBalance> fundBalances, String activeFundName) {
        Optional<FundBalance> activeFundBalance = fundBalances.stream()
          .filter(fb -> fb.getFund().getName().equals(activeFundName))
          .findFirst();
        activeFundBalance.ifPresent( fb -> fb.setActiveContributions(true));
        if(!activeFundBalance.isPresent()) {
            fundBalances.add(constructActiveFundBalance(activeFundName));
        }
        return fundBalances;
    }

    private FundBalance constructActiveFundBalance(String activeFundName) {
        FundBalance activeFundBalance = FundBalance.builder()
          .value(BigDecimal.ZERO)
          .currency("EUR")
          .activeContributions(true)
          .build();

        Fund activeFund = fundRepository.findByNameIgnoreCase(activeFundName);
        if (activeFund != null) {
            activeFundBalance.setFund(activeFund);
        } else {
            log.error("Fund with name not found {}", activeFundName);
            activeFundBalance.setFund(Fund.builder().name(activeFundName).build());
        }

        return activeFundBalance;
    }



}
