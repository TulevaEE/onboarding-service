package ee.tuleva.onboarding.mandate;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.domain.fund.Fund;
import ee.tuleva.domain.fund.FundView;
import ee.tuleva.onboarding.user.User;
import lombok.Builder;
import lombok.Data;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Past;
import java.time.Instant;
import java.util.List;

@Data
@Entity
@Table(name = "mandate")
public class Mandate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    User user;

    @ManyToOne
    Fund futureContributionFund;

    @NotNull
    @Past
    private Instant createdDate;

    @PrePersist
    protected void onCreate() {
        createdDate = Instant.now();
    }

    byte[] mandate;

    @NotNull
    @OneToMany
    List<FundTransferExchange> fundTransferExchanges;

    @Builder
    Mandate(User user, Fund futureContributionFund, List<FundTransferExchange> fundTransferExchanges){
        this.user = user;
        this.futureContributionFund = futureContributionFund;
        this.fundTransferExchanges = fundTransferExchanges;
    }

}
