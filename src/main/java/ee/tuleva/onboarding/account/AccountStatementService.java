package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.common.Utils;
import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.EpisAccountOverviewProvider;
import ee.tuleva.onboarding.comparisons.overview.Transaction;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.account.FundBalanceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountStatementService {
    private static final Instant START_TIME = Utils.parseInstant("2002-01-01");
    private final EpisService episService;
    private final FundBalanceDtoToFundBalanceConverter fundBalanceConverter;
    private final EpisAccountOverviewProvider episAccountOverviewProvider;

    public List<FundBalance> getAccountStatement(Person person) {
        return getAccountStatement(person, false);

    }

    public List<FundBalance> getAccountStatement(Person person, boolean calculateContributionSum) {
        List<FundBalanceDto> accountStatement = episService.getAccountStatement(person);

        List<FundBalance> fundBalances = accountStatement.stream()
            .filter(fundBalanceDto -> fundBalanceDto.getIsin() != null)
            .map(fundBalanceDto -> convertToFundBalance(fundBalanceDto, person))
            .collect(toList());

        if (calculateContributionSum) {
            calculateContributionSum(person, fundBalances);

        }

        return fundBalances;
    }

    private void calculateContributionSum(Person person, List<FundBalance> fundBalances) {
        fundBalances.stream().forEach(fundBalance -> {
            AccountOverview accountOverview = episAccountOverviewProvider.getAccountOverview(person, START_TIME, fundBalance.getPillar());

            BigDecimal sumOfAllContributions = accountOverview.getTransactions().stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            fundBalance.setContributionSum(sumOfAllContributions);
        });
    }

    private FundBalance convertToFundBalance(FundBalanceDto fundBalanceDto, Person person) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Fund Balance DTO: {}", fundBalanceDto);
            }
            return fundBalanceConverter.convert(fundBalanceDto);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Could not convert fund balance for person " + person, e);
        }
    }
}
