package ee.tuleva.onboarding.holdings.converters;

import ee.tuleva.onboarding.holdings.persistence.HoldingDetail;
import ee.tuleva.onboarding.holdings.xml.XmlHoldingDetail;

import org.springframework.core.convert.converter.Converter;

public class HoldingDetailConverter implements Converter<XmlHoldingDetail, HoldingDetail> {

    @Override
    public HoldingDetail convert(XmlHoldingDetail source) {
        return HoldingDetail.builder()
            .country(source.getCountry())
            .currency(source.getCurrency())
            .firstBoughtDate(source.getFirstBoughtDate())
            .holdingYtdReturn(source.getHoldingYtdReturn())
            .isin(source.getIsin())
            .marketValue(source.getMarketValue())
            .numberOfShare(source.getNumberOfShare())
            .region(source.getRegion())
            .sector(source.getSector())
            .securityName(source.getSecurityName())
            .shareChange(source.getShareChange())
            .styleBox(source.getStyleBox())
            .symbol(source.getSymbol())
            .weighting(source.getWeighting())
            .build();
    }
}
