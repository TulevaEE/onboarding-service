package ee.tuleva.onboarding.mandate;

import static ee.tuleva.onboarding.mandate.MandateType.*;
import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static java.util.Objects.requireNonNull;
import static org.hibernate.type.SqlTypes.JSON;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.mandate.details.*;
import ee.tuleva.onboarding.mandate.generic.MandateDto;
import ee.tuleva.onboarding.mandate.payment.rate.ValidPaymentRate;
import ee.tuleva.onboarding.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.jspecify.annotations.Nullable;

@Data
@Entity
@Table(name = "mandate")
@NoArgsConstructor
@ToString(exclude = {"mandateBatch"})
public class Mandate implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @JsonView(MandateView.Default.class)
  private @Nullable Long id;

  @ManyToOne @NotNull private User user;

  @JsonView(MandateView.Default.class)
  @Nullable
  private String futureContributionFundIsin; // TODO: refactor this field into details

  @NotNull
  @Min(2)
  @Max(3)
  @JsonView(MandateView.Default.class)
  private Integer pillar; // TODO: refactor this field into details

  @NotNull
  @JsonView(MandateView.Default.class)
  private @Nullable Instant createdDate;

  private byte @Nullable [] mandate;

  @OneToMany(
      cascade = {CascadeType.ALL},
      mappedBy = "mandate")
  @JsonView(MandateView.Default.class)
  @Nullable
  private List<FundTransferExchange>
      fundTransferExchanges; // TODO: refactor this field into details

  @JdbcTypeCode(JSON)
  @Column(name = "address")
  @NotNull
  @JsonView(MandateView.Default.class)
  @Nullable
  private Country address;

  @JdbcTypeCode(JSON)
  @NotNull
  private Map<String, @Nullable Object> metadata =
      new HashMap<>(); // TODO: refactor this field into details

  @JdbcTypeCode(JSON)
  @JsonView(MandateView.Default.class)
  private MandateDetails details;

  @Nullable
  @ManyToOne(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
  @JoinColumn(
      name = "mandate_batch_id",
      referencedColumnName = "id",
      foreignKey = @ForeignKey(name = "fk_mandate_batch"))
  private MandateBatch mandateBatch;

  @ValidPaymentRate
  @JsonView(MandateView.Default.class)
  private @Nullable BigDecimal paymentRate;

  @Builder
  Mandate(
      User user,
      String futureContributionFundIsin,
      List<FundTransferExchange> fundTransferExchanges,
      Integer pillar,
      @Nullable Country address,
      Map<String, @Nullable Object> metadata,
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
  private <T extends MandateDetails> GenericMandateSubmission<T> buildSubmission(T details) {
    return GenericMandateSubmission.<T>builder()
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
  public GenericMandateSubmission<?> toSubmission() {
    if (!supportsSubmission()) {
      throw new IllegalStateException("Mandate DTO not yet supported for given application");
    }

    return buildSubmission(details);
  }

  @JsonIgnore
  public MandateDto<?> getMandateDto() {
    if (!supportsSubmission()) {
      throw new IllegalStateException("Mandate DTO not yet supported for given application");
    }

    return buildMandateDto(details);
  }

  @JsonIgnore
  public boolean supportsSubmission() {
    return details != null;
  }

  @PrePersist
  protected void onCreate() {
    createdDate = clock().instant();
    syncLegacyColumns();
  }

  @PreUpdate
  protected void onUpdate() {
    syncLegacyColumns();
  }

  private void syncLegacyColumns() {
    pillar = getPillar();
    paymentRate = getPaymentRate();
  }

  public Integer getPillar() {
    return details != null ? details.pillar().toInt() : pillar;
  }

  public @Nullable BigDecimal getPaymentRate() {
    return details instanceof PaymentRateChangeMandateDetails rateChange
        ? rateChange.getPaymentRate().getNumericValue()
        : paymentRate;
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

    List<FundTransferExchange> exchanges =
        fundTransferExchanges != null ? fundTransferExchanges : List.of();
    exchanges.stream()
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
    return requireNonNull(user.getEmail(), "User missing email: mandateId=" + id);
  }

  @JsonIgnore
  public String getPhoneNumber() {
    return requireNonNull(user.getPhoneNumber(), "User missing phone number: mandateId=" + id);
  }

  public boolean isThirdPillar() {
    return pillar == 3;
  }

  @JsonIgnore
  public boolean isPartOfBatch() {
    return getMandateBatch() != null;
  }

  @JsonIgnore
  public @Nullable Country getCountry() {
    return address;
  }

  @JsonIgnore
  public Long getIdOrThrow() {
    return requireNonNull(id, "Mandate id missing (not yet persisted)");
  }
}
