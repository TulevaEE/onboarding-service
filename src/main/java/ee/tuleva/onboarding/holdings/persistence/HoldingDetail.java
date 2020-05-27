package ee.tuleva.onboarding.holdings.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@Entity
@Table(name = "holding_details")
@AllArgsConstructor
@NoArgsConstructor
public class HoldingDetail{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;

    private String country;

    private String currency;

    @NotNull
    private String securityName;

    private BigDecimal weighting;

    private Long numberOfShare;

    private Long shareChange;

    private Long marketValue;

    @Enumerated(EnumType.STRING)
    private Sector sector;

    private BigDecimal holdingYtdReturn;

    @Enumerated(EnumType.STRING)
    private Region region;

    private String isin;

    private LocalDate firstBoughtDate;

    @NotNull
    private LocalDate createdDate;
}
