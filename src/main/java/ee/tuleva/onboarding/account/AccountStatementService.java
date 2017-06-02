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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    private final String ACCOUNT_STATEMENT_CACHE_NAME = "accountStatement";

    @Cacheable(value=ACCOUNT_STATEMENT_CACHE_NAME, key="#person.personalCode")
    public List<FundBalance> getMyPensionAccountStatement(Person person, UUID statisticsIdentifier) {
        log.info("Getting pension account statement for {} {}", person.getFirstName(), person.getLastName());
        List<FundBalance> fundBalances = convertXRoadResponse(getPensionAccountBalance(person));

        fundBalances = handleActiveFundBalance(fundBalances, getActiveFundName(person));
        saveFundValueStatistics(fundBalances, statisticsIdentifier);

        return fundBalances;
    }

    @CacheEvict(value=ACCOUNT_STATEMENT_CACHE_NAME, key="#person.personalCode")
    public void clearCache(Person person) {
        log.info("Clearning exchanges cache for {} {}",
                person.getFirstName(), person.getLastName());
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
        String activeFundName = csdPersonalSelection.getPensionAccount().getSecurityName();
        log.info("Active fund name is {}", activeFundName);
        return activeFundName;
    }

    private List<FundBalance> handleActiveFundBalance(List<FundBalance> fundBalances, String activeFundName) {
        fundBalances.stream().forEach( fb-> {
            log.info("Having fund {} with isin {}",
                    fb.getFund().getName(), fb.getFund().getIsin());
        });

        Optional<FundBalance> activeFundBalance = fundBalances.stream()
                .filter(fb -> fb.getFund().getName().equalsIgnoreCase(activeFundName))
                .findFirst();

        activeFundBalance.ifPresent( fb -> {
            fb.setActiveContributions(true);
            log.info("Setting active fund {}", fb.getFund().getName());
        });
        if(!activeFundBalance.isPresent()) {
            log.info("Didn't find active fund {} from the list, creating one.", activeFundName);
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

        log.info("Constructed active fund for {} with isin {}",
                activeFund.getName(), activeFund.getIsin());

        return activeFundBalance;
    }



}
