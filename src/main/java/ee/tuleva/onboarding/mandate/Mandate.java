package ee.tuleva.onboarding.mandate;

import static ee.tuleva.onboarding.time.ClockHolder.clock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import ee.tuleva.onboarding.mandate.payment.rate.ValidPaymentRate;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  @JsonView(MandateView.Default.class)
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

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  @JsonView(MandateView.Default.class)
  private Address address;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  @Convert(disableConversion = true)
  @NotNull
  private Map<String, Object> metadata = new HashMap<>();

  @ValidPaymentRate
  @JsonView(MandateView.Default.class)
  private BigDecimal paymentRate;

  @Builder
  Mandate(
      User user,
      String futureContributionFundIsin,
      List<FundTransferExchange> fundTransferExchanges,
      Integer pillar,
      @Nullable Address address,
      Map<String, Object> metadata,
      @Nullable BigDecimal paymentRate) {
    this.user = user;
    this.futureContributionFundIsin = futureContributionFundIsin;
    this.fundTransferExchanges = fundTransferExchanges;
    this.pillar = pillar;
    this.address = address;
    this.metadata = metadata;
    this.paymentRate = paymentRate;
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
  public boolean isPaymentRateApplication() {
    return paymentRate != null;
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
        && fundTransferExchanges.getFirst().getSourceFundIsin() != null
        && fundTransferExchanges.getFirst().getTargetFundIsin() == null
        && fundTransferExchanges.getFirst().getAmount() == null;
  }

  @JsonIgnore
  public String getEmail() {
    return user.getEmail();
  }

  @JsonIgnore
  public String getPhoneNumber() {
    return user.getPhoneNumber();
  }

  public boolean isThirdPillar() {
    return pillar == 3;
  }
}
