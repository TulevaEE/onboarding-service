package ee.tuleva.onboarding.mandate;

import static ee.tuleva.onboarding.mandate.MandateType.*;
import static ee.tuleva.onboarding.time.ClockHolder.clock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.epis.mandate.GenericMandateDto;
import ee.tuleva.onboarding.epis.mandate.details.EarlyWithdrawalCancellationMandateDetails;
import ee.tuleva.onboarding.epis.mandate.details.MandateDetails;
import ee.tuleva.onboarding.epis.mandate.details.TransferCancellationMandateDetails;
import ee.tuleva.onboarding.epis.mandate.details.WithdrawalCancellationMandateDetails;
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
import java.util.*;

import lombok.*;
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
  private String futureContributionFundIsin; // TODO: refactor this field into details

  @JsonView(MandateView.Default.class)
  @Nullable
  @Getter(AccessLevel.NONE)
  // TODO: check if ApplicationType serialized correctly, not using the Estonian translation
  private MandateType mandateType;

  @NotNull
  @Min(2)
  @Max(3)
  @JsonView(MandateView.Default.class)
  private Integer pillar; // TODO: refactor this field into details

  @NotNull
  @JsonView(MandateView.Default.class)
  private Instant createdDate;

  @Nullable private byte[] mandate;

  @OneToMany(
      cascade = {CascadeType.ALL},
      mappedBy = "mandate")
  @JsonView(MandateView.Default.class)
  @Nullable
  private List<FundTransferExchange>
      fundTransferExchanges; // TODO: refactor this field into details

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  @JsonView(MandateView.Default.class)
  private Address address;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  @Convert(disableConversion = true)
  @NotNull
  private Map<String, Object> metadata = new HashMap<>(); // TODO: refactor this field into details

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  @Convert(disableConversion = true)
  @NotNull
  private Map<String, Object> details = new HashMap<>();

  @ValidPaymentRate
  @JsonView(MandateView.Default.class)
  private BigDecimal paymentRate; // TODO: refactor this field into details

  @Builder
  Mandate(
      User user,
      String futureContributionFundIsin,
      List<FundTransferExchange> fundTransferExchanges,
      Integer pillar,
      @Nullable Address address,
      Map<String, Object> metadata,
      @Nullable BigDecimal paymentRate,
      MandateType mandateType,
      Map<String, Object> details) {
    this.user = user;
    this.futureContributionFundIsin = futureContributionFundIsin;
    this.fundTransferExchanges = fundTransferExchanges;
    this.pillar = pillar;
    this.address = address;
    this.metadata = metadata;
    this.paymentRate = paymentRate;
    this.mandateType = mandateType;
    this.details = details;
  }

  private <T extends MandateDetails> GenericMandateDto<T> buildGenericMandateDto(MandateType mandateType, T details) {
    return GenericMandateDto.<T>builder()
        .mandateType(mandateType)
        .id(id)
        .createdDate(createdDate)
        .address(address)
        .email(getEmail())
        .phoneNumber(getPhoneNumber())
        .details(details)
        .build();
  }

  public GenericMandateDto<?> getGenericMandateDto() {
    if (isWithdrawalCancellation()) {
      return buildGenericMandateDto(MandateType.WITHDRAWAL_CANCELLATION, new WithdrawalCancellationMandateDetails());
    } else if (isEarlyWithdrawalCancellation()) {
      return buildGenericMandateDto(MandateType.EARLY_WITHDRAWAL_CANCELLATION, new EarlyWithdrawalCancellationMandateDetails());
    } else if (isTransferCancellation()) {
      return buildGenericMandateDto(MandateType.TRANSFER_CANCELLATION, new TransferCancellationMandateDetails());
    }
    throw new IllegalStateException("Mandate DTO not yet supported for given application");
  }

  @PrePersist
  protected void onCreate() {
    createdDate = clock().instant(); // TODO column default value NOW() in database
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

  @JsonIgnore
  public boolean isWithdrawalCancellation() {
    return mandateType == WITHDRAWAL_CANCELLATION;
  }

  @JsonIgnore
  public boolean isEarlyWithdrawalCancellation() {
    return mandateType == EARLY_WITHDRAWAL_CANCELLATION;
  }

  @JsonIgnore
  public boolean isPaymentRateApplication() {
    return paymentRate != null;
  }

  public byte[] getSignedFile() {
    return getMandate()
        .orElseThrow(() -> new IllegalStateException("Expecting mandate to be signed"));
  }

  @JsonIgnore
  public boolean isTransferCancellation() {
    return mandateType == TRANSFER_CANCELLATION;
    /*
        return fundTransferExchanges != null
        && fundTransferExchanges.size() == 1
        && fundTransferExchanges.getFirst().getSourceFundIsin() != null
        && fundTransferExchanges.getFirst().getTargetFundIsin() == null
        && fundTransferExchanges.getFirst().getAmount() == null;
     */
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
