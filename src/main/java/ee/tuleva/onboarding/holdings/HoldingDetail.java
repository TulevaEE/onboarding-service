package ee.tuleva.onboarding.holdings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.persistence.oxm.annotations.XmlPath;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@Entity
@Table(name = "holding_details")
@AllArgsConstructor
@NoArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name="HoldingDetail")
public class HoldingDetail{
    @XmlTransient
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @XmlAttribute(name="_Id")
    @NotNull
    private String _id;

    @XmlAttribute(name="_ExternalId")
    @NotNull
    private String _externalId;

    @XmlElement(name="Symbol")
    @NotNull
    private String symbol;

    @XmlPath("Country/@_Id")
    @NotNull
    private String country_id;

    @XmlElement(name="CUSIP")
    @NotNull
    private String cusip;

    @XmlPath("Currency/@_Id")
    @NotNull
    private String currency_id;

    @XmlElement(name="SecurityName")
    @NotNull
    private String securityName;

    @XmlElement(name="LegalType")
    @NotNull
    private String legalType;

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
    @NotNull
    private Long sector;

    @XmlElement(name="HoldingYTDReturn")
    @NotNull
    private BigDecimal holdingYtdReturn;

    @XmlElement(name="Region")
    @NotNull
    private Long region;

    @XmlElement(name="ISIN")
    @NotNull
    private String isin;

    @XmlElement(name="StyleBox")
    @NotNull
    private Long styleBox;

    @XmlElement(name="SEDOL")
    @NotNull
    private String sedol;

    @XmlElement(name="FirstBoughtDate")
    @XmlJavaTypeAdapter(XmlDateAdapter.class)
    private LocalDate firstBoughtDate;

    @XmlTransient
    private LocalDate createdDate;
}
