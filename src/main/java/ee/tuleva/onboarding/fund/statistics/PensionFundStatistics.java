package ee.tuleva.onboarding.fund.statistics;

import java.io.Serializable;
import java.math.BigDecimal;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
public class PensionFundStatistics implements Serializable {

  private static final long serialVersionUID = 6065879655793615150L;

  @XmlAttribute(name = "ISIN")
  private String isin;

  @XmlAttribute(name = "VOLUME")
  private BigDecimal volume;

  @XmlAttribute(name = "NAV")
  private BigDecimal nav;

  @XmlAttribute(name = "ACTIVE_COUNT")
  private Integer activeCount;

  public static PensionFundStatistics getNull() {
    return new PensionFundStatistics();
  }
}
