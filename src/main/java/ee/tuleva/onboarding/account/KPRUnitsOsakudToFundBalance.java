package ee.tuleva.onboarding.account;

import com.google.common.annotations.VisibleForTesting;
import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType.Units.Balance;
import org.springframework.core.convert.converter.Converter;

import java.util.ArrayList;
import java.util.List;


public class KPRUnitsOsakudToFundBalance implements Converter<Balance, FundBalance> {

    public FundBalance convert(Balance source) {
        return FundBalance.builder()
                //.isin("") todo matching from securityName, no key from API?
                .name(source.getSecurityName())
                .manager(getFundManagerName(source.getSecurityName()))
                //source.getNav();
                .price(source.getAmount())
                .currency(source.getCurrency())
                .build();
    }


    /**
     * We designed to use grouping for funds but don't have links by KPR. Decided to take name from fund names that
     * for names in Estonian looked to be prefix till first space. Apply to Estonian fund names only!
     */
    String getFundManagerName(String fundname) {
        int i = fundname.indexOf(' ');

        if (i < 1) {
            throw new RuntimeException("Unable to find fund manager from fund name!");
        }

        return fundname.substring(0, i - 1);
    }


    /**
     * todo satisfy CollectionToCollectionConverter interface maybe later.
     */
    public static List<FundBalance> convertList(List<Balance> in) {
        ArrayList<FundBalance> ret = new ArrayList<FundBalance>(in.size());
        KPRUnitsOsakudToFundBalance converter = new KPRUnitsOsakudToFundBalance();

        for (Balance i : in) {
            ret.add(converter.convert(i));
        }

        return ret;
    }

}
