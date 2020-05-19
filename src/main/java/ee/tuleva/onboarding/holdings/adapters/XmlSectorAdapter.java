package ee.tuleva.onboarding.holdings.adapters;

import ee.tuleva.onboarding.holdings.models.Region;
import ee.tuleva.onboarding.holdings.models.Sector;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class XmlSectorAdapter extends XmlAdapter<Long, Sector> {
    @Override
    public Sector unmarshal(Long xml){
        return Sector.valueOf(xml.intValue());
    }

    @Override
    public Long marshal(Sector sector){
        return (long) sector.getValue();
    }
}
