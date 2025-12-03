package ee.tuleva.onboarding.mandate;

import static ee.tuleva.onboarding.mandate.MandateType.*;
import static ee.tuleva.onboarding.time.ClockHolder.clock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.epis.mandate.GenericMandateDto;
import ee.tuleva.onboarding.epis.mandate.details.*;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.mandate.generic.MandateDto;
import ee.tuleva.onboarding.mandate.payment.rate.ValidPaymentRate;
import ee.tuleva.onboarding.user.User;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.*;
import org.hibernate.annotations.Type;

@Data
@Entity
@Table(name = "mandate")
@NoArgsConstructor
@ToString(exclude = {"mandateBatch"})
public class Mandate implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @JsonView(MandateView.Default.class)
  private Long id;

  @ManyToOne @NotNull private User user;

  @JsonView(MandateView.Default.class)
  @Nullable
  private String futureContributionFundIsin; // TODO: refactor this field into details

  @Deprecated
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
  @Column(name = "address", columnDefinition = "jsonb")
  @NotNull
  @JsonView(MandateView.Default.class)
  private Country address;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  @Convert(disableConversion = true)
  @NotNull
  private Map<String, Object> metadata = new HashMap<>(); // TODO: refactor this field into details

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  @Convert(disableConversion = true)
  @JsonView(MandateView.Default.class)
  private MandateDetails details;

  @Nullable
  @ManyToOne(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
  @JoinColumn(
      name = "mandate_batch_id",
      referencedColumnName = "id",
      foreignKey = @ForeignKey(name = "fk_mandate_batch"))
  private MandateBatch mandateBatch;

  @Deprecated
  @ValidPaymentRate
  @JsonView(MandateView.Default.class)
  private BigDecimal paymentRate;

  @Builder
  Mandate(
      User user,
      String futureContributionFundIsin,
      List<FundTransferExchange> fundTransferExchanges,
      Integer pillar,
      @Nullable Country address,
      Map<String, Object> metadata,
      @Nullable BigDecimal paymentRate,
      MandateDetails details) {
    this.user = user;
    this.futureContributionFundIsin = futureContributionFundIsin;
    this.fundTransferExchanges = fundTransferExchanges;
    this.pillar = pillar;
    this.address = address;
    this.metadata = metadata;
    this.paymentRate = paymentRate;
    this.details = details;
  }

  @JsonIgnore
  private <T extends MandateDetails> GenericMandateDto<T> buildGenericMandateDto(T details) {
    return GenericMandateDto.<T>builder()
        .id(id)
        .createdDate(createdDate)
        .address(address)
        .email(getEmail())
        .phoneNumber(getPhoneNumber())
        .details(details)
        .build();
  }

  @JsonIgnore
  private <T extends MandateDetails> MandateDto<T> buildMandateDto(T details) {
    return MandateDto.<T>builder().id(id).createdDate(createdDate).details(details).build();
  }

  @JsonIgnore
  public GenericMandateDto<?> getGenericMandateDto() {
    if (!supportsGenericMandateDto()) {
      throw new IllegalStateException("Mandate DTO not yet supported for given application");
    }

    return buildGenericMandateDto(details);
  }

  @JsonIgnore
  public MandateDto<?> getMandateDto() {
    if (!supportsGenericMandateDto()) {
      throw new IllegalStateException("Mandate DTO not yet supported for given application");
    }

    return buildMandateDto(details);
  }

  @JsonIgnore
  public boolean supportsGenericMandateDto() {
    return details != null;
  }

  @PrePersist
  protected void onCreate() {
    createdDate = clock().instant();
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
  public MandateType getMandateType() {
    return Optional.ofNullable(details).map(MandateDetails::getMandateType).orElse(UNKNOWN);
  }

  @JsonIgnore
  public boolean isWithdrawalCancellation() {
    return getMandateType() == WITHDRAWAL_CANCELLATION;
  }

  @JsonIgnore
  public boolean isEarlyWithdrawalCancellation() {
    return getMandateType() == EARLY_WITHDRAWAL_CANCELLATION;
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
    return getMandateType() == TRANSFER_CANCELLATION;
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

  @JsonIgnore
  public boolean isPartOfBatch() {
    return getMandateBatch() != null;
  }

  @JsonIgnore
  public Country getCountry() {
    return address;
  }
}
