package ee.tuleva.onboarding.statistics;

import lombok.Data;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import java.math.BigDecimal;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class PensionFundStatistics {

  @XmlAttribute(name = "ISIN")
  private String isin;
  @XmlAttribute(name = "VOLUME")
  private BigDecimal volume;
  @XmlAttribute(name = "NAV")
  private BigDecimal nav;

  /*
  <PENSION_FUND_STATISTICS
  DATE="27/06/17"
  ISIN="EE3600019717"
  VOLUME="59899459.39470"
  FUNDUNITS="65456021.019"
  NAV="0.91511"
  NAV_DATE="27/06/17"
  CHANGE="-0.20067"
  NAV52WLOW="0.91009"
  NAV52WHIGH="0.92160"
  RETURN1MO="-0.04151"
  RETURN3MO="0.33001"
  RETURN6MO="0.23220"
  RETURN12MO="0.54276"
  RETURN24MO="0.64992"
  RETURN36MO="0.15346"
  RETURN48MO="0.76089"
  RETURN60MO="0.68822"
  RETURN120MO="2.46842"
  RTD="2.42237"
  VOLATILITY="1.52311"
  BUY_PRICE="0.91511"
  REDEMPTION_PRICE="0.91511"
  YTD="0.20367"
  CURRENCY="EUR"
  NAV52WLOW_CURRENCY="EUR"
  NAV52WHIGH_CURRENCY="EUR"
  ACTIVE_COUNT="12614"/>
   */

}
