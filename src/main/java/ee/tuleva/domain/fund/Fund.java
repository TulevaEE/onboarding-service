package ee.tuleva.domain.fund;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Data
@Builder
@Entity
@Table(name = "fund")
@AllArgsConstructor
@NoArgsConstructor
public class Fund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    private FundManager fundManager;
    @NotBlank
    private String isin;
    @NotBlank
    private String name;
    @NotNull
    private Float managementFeePercent;

}
