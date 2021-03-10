package ee.tuleva.onboarding.fund.statistics;

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Data;

@Data
@XmlRootElement(name = "RESPONSE")
@XmlAccessorType(XmlAccessType.FIELD)
public class PensionFundStatisticsResponse {

  @XmlElement(name = "PENSION_FUND_STATISTICS")
  private List<PensionFundStatistics> pensionFundStatistics;
}
