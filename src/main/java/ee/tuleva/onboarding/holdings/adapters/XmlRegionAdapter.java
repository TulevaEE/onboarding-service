package ee.tuleva.onboarding.holdings.adapters;

import ee.tuleva.onboarding.holdings.persistence.Region;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class XmlRegionAdapter extends XmlAdapter<Long, Region> {
    @Override
    public Region unmarshal(Long xml){
        return Region.valueOf(xml.intValue());
    }

    @Override
    public Long marshal(Region region){
        return (long) region.getValue();
    }
}
