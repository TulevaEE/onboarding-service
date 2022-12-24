package ee.tuleva.onboarding.mandate;

import static ee.tuleva.onboarding.time.ClockHolder.clock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.jetbrains.annotations.Nullable;

@Data
@Entity
@Table(name = "mandate")
@NoArgsConstructor
public class Mandate implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @JsonView(MandateView.Default.class)
  private Long id;

  @ManyToOne @NotNull private User user;

  @JsonView(MandateView.Default.class)
  @Nullable
  private String futureContributionFundIsin;

  @NotNull
  @Min(2)
  @Max(3)
  private Integer pillar;

  @NotNull
  @JsonView(MandateView.Default.class)
  private Instant createdDate;

  @PrePersist
  protected void onCreate() {
    createdDate = clock().instant();
  }

  @Nullable private byte[] mandate;

  @OneToMany(
      cascade = {CascadeType.ALL},
      mappedBy = "mandate")
  @JsonView(MandateView.Default.class)
  @Nullable
  private List<FundTransferExchange> fundTransferExchanges;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  @NotNull
  @JsonView(MandateView.Default.class)
  private Address address;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  @Convert(disableConversion = true)
  @NotNull
  private Map<String, Object> metadata = new HashMap<>();

  @Builder
  Mandate(
      User user,
      String futureContributionFundIsin,
      List<FundTransferExchange> fundTransferExchanges,
      Integer pillar,
      @Nullable Address address,
      Map<String, Object> metadata) {
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

  public boolean isSigned() {
    return mandate != null;
  }

  public Optional<String> getFutureContributionFundIsin() {
    return Optional.ofNullable(futureContributionFundIsin);
  }

  public Map<String, List<FundTransferExchange>> getFundTransferExchangesBySourceIsin() {
    Map<String, List<FundTransferExchange>> exchangeMap = new HashMap<>();

    fundTransferExchanges.stream()
        .filter(
            exchange ->
                exchange.getAmount() == null || exchange.getAmount().compareTo(BigDecimal.ZERO) > 0)
        .forEach(
            exchange -> {
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

  @JsonIgnore
  public boolean isWithdrawalCancellation() {
    return metadata != null && metadata.containsKey("applicationTypeToCancel");
  }

  @JsonIgnore
  public ApplicationType getApplicationTypeToCancel() {
    if (isWithdrawalCancellation()) {
      return ApplicationType.valueOf((String) metadata.get("applicationTypeToCancel"));
    }
    return null;
  }

  public byte[] getSignedFile() {
    return getMandate()
        .orElseThrow(() -> new IllegalStateException("Expecting mandate to be signed"));
  }

  @JsonIgnore
  public boolean isTransferCancellation() {
    return fundTransferExchanges != null
        && fundTransferExchanges.size() == 1
        && fundTransferExchanges.get(0).getSourceFundIsin() != null
        && fundTransferExchanges.get(0).getTargetFundIsin() == null
        && fundTransferExchanges.get(0).getAmount() == null;
  }

  @JsonIgnore
  public String getEmail() {
    return user.getEmail();
  }

  @JsonIgnore
  public String getPhoneNumber() {
    return user.getPhoneNumber();
  }
}
