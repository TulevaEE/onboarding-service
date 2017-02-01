package ee.tuleva.onboarding.account;


import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType;
import ee.tuleva.domain.fund.Fund;
import ee.tuleva.domain.fund.FundRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Just to append ISIN based on fund name, silly but no key from KPR api.
 * Modifies source object instead of creating new!
 */
public class IsinAppender implements Converter<FundBalance, FundBalance> {

    private FundRepository fundRepository;

    @Autowired
    public IsinAppender(FundRepository fundRepository) {
        this.fundRepository = fundRepository;
    }

    @Override
    public FundBalance convert(FundBalance source) {
        Fund f = fundRepository.findByName(source.getName());
        if (f == null) {
            // todo not hiding but we haven't figured out good business actions here
            throw new RuntimeException("Unable to resolve fund by name!");
        }

        source.setIsin(f.getIsin());
        return source;
    }


    /**
     * Kept void for clarity, applies changes to same list and list objects.
     */
    public void convertList(List<FundBalance> in) {
        for (FundBalance fb : in) {
            Fund f = fundRepository.findByName(fb.getName());

            if (f == null) {
                throw new RuntimeException("Unable to resolve fund by name!");
            }

            fb.setIsin(f.getName());
        }
    }

}
