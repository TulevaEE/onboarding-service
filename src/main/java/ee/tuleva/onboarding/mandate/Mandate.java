package ee.tuleva.onboarding.mandate;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.config.JsonbType;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.jetbrains.annotations.Nullable;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Data
@Entity
@Table(name = "mandate")
@NoArgsConstructor
@TypeDefs({
    @TypeDef(name = "jsonb", typeClass = JsonbType.class),
})
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
    private Integer pillar;

    @NotNull
    @JsonView(MandateView.Default.class)
    private Instant createdDate;

    @PrePersist
    protected void onCreate() {
        createdDate = Instant.now();
    }

    @Nullable
    private byte[] mandate;

    @OneToMany(cascade = {CascadeType.ALL}, mappedBy = "mandate")
    @JsonView(MandateView.Default.class)
    List<FundTransferExchange> fundTransferExchanges;

    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    @Nullable
    @JsonView(MandateView.Default.class)
    private Address address;

    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    @Convert(disableConversion = true)
    private Map<String, Object> metadata = new HashMap<>();

    @Builder
    Mandate(User user, String futureContributionFundIsin, List<FundTransferExchange> fundTransferExchanges,
            Integer pillar, @Nullable Address address, Map<String, Object> metadata) {
        this.user = user;
        this.futureContributionFundIsin = futureContributionFundIsin;
        this.fundTransferExchanges = fundTransferExchanges;
        this.pillar = pillar;
        this.address = address;
        this.metadata = metadata;
    }

    public Optional<byte[]> getMandate() {
        return Optional.ofNullable(mandate);
    }

    public Optional<String> getFutureContributionFundIsin() {
        return Optional.ofNullable(futureContributionFundIsin);
    }

    public Map<String, List<FundTransferExchange>> getFundTransferExchangesBySourceIsin() {
        Map<String, List<FundTransferExchange>> exchangeMap = new HashMap<>();

        getFundTransferExchanges().stream()
            .filter(exchange -> exchange.getAmount().compareTo(BigDecimal.ZERO) > 0)
            .forEach(exchange -> {
                if (!exchangeMap.containsKey(exchange.getSourceFundIsin())) {
                    exchangeMap.put(exchange.getSourceFundIsin(), new ArrayList<>());
                }
                exchangeMap.get(exchange.getSourceFundIsin()).add(exchange);
            });

        return exchangeMap;
    }

    public void putMetadata(String key, Object value) {
        metadata.put(key, value);
    }
}
