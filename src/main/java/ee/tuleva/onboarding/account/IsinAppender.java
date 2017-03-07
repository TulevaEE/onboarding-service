package ee.tuleva.onboarding.account;


import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType;
import ee.tuleva.domain.fund.Fund;
import ee.tuleva.domain.fund.FundRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Just to append ISIN based on fund name, silly but no key from KPR api.
 * Modifies source object instead of creating new!
 */
@Service
public class IsinAppender implements Converter<FundBalance, FundBalance> {

    private FundRepository fundRepository;

    @Autowired
    public IsinAppender(FundRepository fundRepository) {
        this.fundRepository = fundRepository;
    }

    @Override
    public FundBalance convert(FundBalance source) {
        Fund f = fundRepository.findByNameIgnoreCase(source.getName());
        if (f == null) {
            // todo not hiding but we haven't figured out good business actions here
            throw new RuntimeException("Unable to resolve fund by name! name=" + source.getName());
        }

        source.setIsin(f.getIsin());
        return source;
    }


    /**
     * Kept void for clarity, applies changes to same list and list objects.
     */
    public List<FundBalance> convertList(List<FundBalance> in) {
        for (FundBalance fb : in) {
            Fund f = fundRepository.findByNameIgnoreCase(fb.getName());

            if (f == null) {
                throw new RuntimeException("Unable to resolve fund by name! name=" + fb.getName());
            }

            fb.setIsin(f.getIsin());
        }

        return in;
    }

}
