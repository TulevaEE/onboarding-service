package ee.tuleva.onboarding.holdings.xml;

import ee.tuleva.onboarding.holdings.adapters.XmlDateAdapter;
import ee.tuleva.onboarding.holdings.adapters.XmlRegionAdapter;
import ee.tuleva.onboarding.holdings.adapters.XmlSectorAdapter;
import ee.tuleva.onboarding.holdings.persistence.Region;
import ee.tuleva.onboarding.holdings.persistence.Sector;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.persistence.oxm.annotations.XmlPath;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name="HoldingDetail")
public class XmlHoldingDetail{
    @XmlElement(name="Symbol")
    @NotNull
    private String symbol;

    @XmlPath("Country/@_Id")
    @NotNull
    private String country;

    @XmlPath("Currency/@_Id")
    @NotNull
    private String currency;

    @XmlElement(name="SecurityName")
    @NotNull
    private String securityName;

    @XmlElement(name="Weighting")
    @NotNull
    private BigDecimal weighting;

    @XmlElement(name="NumberOfShare")
    @NotNull
    private Long numberOfShare;

    @XmlElement(name="ShareChange")
    @NotNull
    private Long shareChange;

    @XmlElement(name="MarketValue")
    @NotNull
    private Long marketValue;

    @XmlElement(name="Sector")
    @XmlJavaTypeAdapter(XmlSectorAdapter.class)
    @NotNull
    private Sector sector;

    @XmlElement(name="HoldingYTDReturn")
    @NotNull
    private BigDecimal holdingYtdReturn;

    @XmlElement(name="Region")
    @XmlJavaTypeAdapter(XmlRegionAdapter.class)
    @NotNull
    private Region region;

    @XmlElement(name="ISIN")
    @NotNull
    private String isin;

    @XmlElement(name="FirstBoughtDate")
    @XmlJavaTypeAdapter(XmlDateAdapter.class)
    private LocalDate firstBoughtDate;
}

