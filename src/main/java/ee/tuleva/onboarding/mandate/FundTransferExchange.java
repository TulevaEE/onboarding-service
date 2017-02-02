package ee.tuleva.onboarding.mandate;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.domain.fund.FundView;
import lombok.Builder;
import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "mandate")
@Builder
public class FundTransferExchange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    Mandate mandate;

}
