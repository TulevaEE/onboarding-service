package ee.tuleva.onboarding.mandate;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.user.User;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Data
@Entity
@Table(name = "mandate")
@NoArgsConstructor
public class Mandate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(MandateView.Default.class)
    private Long id;

    @ManyToOne
    private User user;

    @JsonView(MandateView.Default.class)
    private String futureContributionFundIsin;

    @NotNull
    @JsonView(MandateView.Default.class)
    private Instant createdDate;

    @PrePersist
    protected void onCreate() {
        createdDate = Instant.now();
    }

    private byte[] mandate;

    @OneToMany(cascade = {CascadeType.ALL}, mappedBy = "mandate")
    @JsonView(MandateView.Default.class)
    List<FundTransferExchange> fundTransferExchanges;

    @Builder
    Mandate(User user, String futureContributionFundIsin, List<FundTransferExchange> fundTransferExchanges){
        this.user = user;
        this.futureContributionFundIsin = futureContributionFundIsin;
        this.fundTransferExchanges = fundTransferExchanges;
    }

    public Optional<byte[]> getMandate() {
        return Optional.ofNullable(mandate);
    }

}
