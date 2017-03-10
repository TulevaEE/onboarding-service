package ee.tuleva.onboarding.account;

import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType;
import ee.eesti.xtee6.kpr.PensionAccountBalanceType;
import ee.eesti.xtee6.kpr.PersonalSelectionResponseType;
import ee.tuleva.domain.fund.Fund;
import ee.tuleva.domain.fund.FundRepository;
import ee.tuleva.onboarding.kpr.KPRClient;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountStatementService {

    private final KPRClient kprClient;
    private final FundRepository fundRepository;

    KPRUnitsOsakudToFundBalanceConverter kprUnitsOsakudToFundBalanceConverter;

    public List<FundBalance> getMyPensionAccountStatement(User user) {
        PensionAccountBalanceType request = new PensionAccountBalanceType();
        request.setBalanceDate(null);
        PensionAccountBalanceResponseType response = kprClient.pensionAccountBalance(request, user.getPersonalCode());

        List<FundBalance> fbs =
                response.getUnits().getBalance().stream()
                        .map(b->kprUnitsOsakudToFundBalanceConverter.convert(b))
                        .collect(Collectors.toList());

        // assembling active fund bit and managementFeeRate to here.
        PersonalSelectionResponseType csdPersonalSelection = kprClient.personalSelection(user.getPersonalCode());
        String activeFundName = csdPersonalSelection.getPensionAccount().getSecurityName();

        // there are rare moments when active fund doesn't have the balance
        boolean containsActiveFund = false;

        for (FundBalance balance : fbs) {
            if (balance.getFund().getName().equals(activeFundName)) {
                balance.setActiveContributions(true);
                containsActiveFund = true;
            }
        }

        if (!containsActiveFund) {
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

            fbs.add(activeFundBalance);
        }

        return fbs;
    }



}
