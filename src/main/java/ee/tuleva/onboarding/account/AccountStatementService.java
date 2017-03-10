package ee.tuleva.onboarding.account;

import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType;
import ee.eesti.xtee6.kpr.PensionAccountBalanceType;
import ee.eesti.xtee6.kpr.PersonalSelectionResponseType;
import ee.tuleva.domain.fund.Fund;
import ee.tuleva.domain.fund.FundRepository;
import ee.tuleva.onboarding.kpr.KPRClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountStatementService {

    private final KPRClient kprClient;
    private final IsinAppender isinAppender;
    private final FundRepository fundRepository;

    public List<FundBalance> getAccountStatement(String userIdCode) {
        PensionAccountBalanceType request = new PensionAccountBalanceType();
        request.setBalanceDate(null);
        PensionAccountBalanceResponseType response = kprClient.pensionAccountBalance(request, userIdCode);

        List<FundBalance> fbs = KPRUnitsOsakudToFundBalance.convertList(response.getUnits().getBalance());
        fbs = isinAppender.convertList(fbs);

        // assembling active fund bit and managementFeeRate to here.
        PersonalSelectionResponseType csdPersonalSelection = kprClient.personalSelection(userIdCode);
        String activeFundName = csdPersonalSelection.getPensionAccount().getSecurityName();

        // there are rare moments when active fund doesn't have the balance
        boolean containsActiveFund = false;

        for (FundBalance balance : fbs) {
            if (balance.getName().equals(activeFundName)) {
                balance.setActiveFund(true);
                containsActiveFund = true;
            }
        }

        if (!containsActiveFund) {
            FundBalance activeFundBalance = FundBalance.builder()
                    .name(activeFundName)
                    .price(BigDecimal.ZERO)
                    .currency("EUR")
                    .activeFund(true)
                    .build();

            Fund activeFund = fundRepository.findByNameIgnoreCase(activeFundName);
            if (activeFund != null) {
                activeFundBalance.setIsin(activeFund.getIsin());
                activeFundBalance.setManagementFeeRate(activeFund.getManagementFeeRate());
            }

            fbs.add(activeFundBalance);
        }

        return fbs;

    }

}
