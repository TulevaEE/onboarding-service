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

    @NotNull
    private String symbol;

    @NotNull
    private String country;

    @NotNull
    private String currency;

    @NotNull
    private String securityName;

    @NotNull
    private BigDecimal weighting;

    @NotNull
    private Long numberOfShare;

    @NotNull
    private Long shareChange;

    @NotNull
    private Long marketValue;

    @Enumerated(EnumType.STRING)
    @NotNull
    private Sector sector;

    @NotNull
    private BigDecimal holdingYtdReturn;

    @Enumerated(EnumType.STRING)
    @NotNull
    private Region region;

    @NotNull
    private String isin;

    @NotNull
    private Long styleBox;

    private LocalDate firstBoughtDate;

    private LocalDate createdDate;
}
