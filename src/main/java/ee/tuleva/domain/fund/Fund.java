package ee.tuleva.domain.fund;

import com.fasterxml.jackson.annotation.JsonView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Builder
@Entity
@Table(name = "fund")
@AllArgsConstructor
@NoArgsConstructor
public class Fund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(FundView.SkipFundManager.class)
    private Long id;

    @ManyToOne
    private FundManager fundManager;

    @NotBlank
    @JsonView(FundView.SkipFundManager.class)
    private String isin;

    @NotBlank
    @JsonView(FundView.SkipFundManager.class)
    private String name;

    @NotNull
    @JsonView(FundView.SkipFundManager.class)
    private BigDecimal managementFeeRate;

}
