package ee.tuleva.onboarding.fund.statistics;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;

@Data
@XmlRootElement(name = "RESPONSE")
@XmlAccessorType(XmlAccessType.FIELD)
public class PensionFundStatisticsResponse {

  @XmlElement(name = "PENSION_FUND_STATISTICS")
  private List<PensionFundStatistics> pensionFundStatistics;
}
