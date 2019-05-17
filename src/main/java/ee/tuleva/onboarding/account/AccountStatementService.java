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
    private static final Instant START_TIME_3RD_PILLAR = Utils.parseInstant("2018-08-06"); // When 3rd pillar was moved to pensionifond, no cash transactions available before
    private static final Instant START_TIME_2ND_PILLAR = Utils.parseInstant("2002-01-01"); // Beginning of 2nd pillar
    private final EpisService episService;
    private final FundBalanceDtoToFundBalanceConverter fundBalanceConverter;
    private final EpisAccountOverviewProvider episAccountOverviewProvider;

    private Instant getStartTimeForPillar(int pillar) {
        if (pillar == 2) {
            return START_TIME_2ND_PILLAR;
        } else if (pillar == 3) {
            return START_TIME_3RD_PILLAR;
        } else {
            throw new RuntimeException("Unknown pillar: " + pillar);
        }
    }

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
            calculateAndUpdateContributionSum(person, fundBalances);

        }

        return fundBalances;
    }

    private void calculateAndUpdateContributionSum(Person person, List<FundBalance> fundBalances) {
        fundBalances.stream().forEach(fundBalance -> {
            AccountOverview accountOverview = episAccountOverviewProvider.getAccountOverview(person, getStartTimeForPillar(fundBalance.getPillar()), fundBalance.getPillar());

            BigDecimal sumOfAllContributions = accountOverview.getTransactions().stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Adding initial balance as a transaction
            if (fundBalance.getPillar() == 3) {
                sumOfAllContributions = sumOfAllContributions.add(accountOverview.getBeginningBalance());
            }

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
