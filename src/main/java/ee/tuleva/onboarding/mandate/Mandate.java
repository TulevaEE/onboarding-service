package ee.tuleva.onboarding.mandate;

import ee.tuleva.domain.fund.Fund;
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

    String futureContributionFundIsin;

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
    Mandate(User user, String futureContributionFundIsin, List<FundTransferExchange> fundTransferExchanges){
        this.user = user;
        this.futureContributionFundIsin = futureContributionFundIsin;
        this.fundTransferExchanges = fundTransferExchanges;
    }

}
