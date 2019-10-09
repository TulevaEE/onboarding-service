package ee.tuleva.onboarding.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class ThirdPillarStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private Long mandateId;

    private BigDecimal singlePayment;

    private BigDecimal recurringPayment;
}
