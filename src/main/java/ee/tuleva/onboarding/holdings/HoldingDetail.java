package ee.tuleva.onboarding.holdings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@Entity
@Table(name = "holding_details")
@AllArgsConstructor
@NoArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
public class HoldingDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @XmlAttribute
    @NotNull
    private String _id;

    @XmlAttribute
    @NotNull
    private String _externalId;

    @NotNull
    private String symbol;

    @NotNull
    private String country;

    @NotNull
    private String cuisp;

    @NotNull
    private String currency;

    @NotNull
    private String securityName;

    @NotNull
    private String legalType;

    @NotNull
    private BigDecimal weighting;

    @NotNull
    private Long numberOfShare;

    @NotNull
    private Long shareChange;

    @NotNull
    private Long marketValue;

    @NotNull
    private Long sector;

    @NotNull
    private BigDecimal holdingYTDReturn;

    @NotNull
    private Long region;

    @NotNull
    private String isin;

    @NotNull
    private String styleBox;

    @NotNull
    private String sedol;

    @NotNull
    private LocalDate firstBoughtDate;
}
