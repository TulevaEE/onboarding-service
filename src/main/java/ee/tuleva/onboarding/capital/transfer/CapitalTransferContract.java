package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.*;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.BUYER_SIGNED;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.CREATED;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.PAYMENT_CONFIRMED_BY_BUYER;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.PAYMENT_CONFIRMED_BY_SELLER;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.SELLER_SIGNED;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;
import static java.math.BigDecimal.ZERO;
import static lombok.AccessLevel.PRIVATE;
import static org.hibernate.type.SqlTypes.JSON;

import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import ee.tuleva.onboarding.capital.transfer.iban.ValidIban;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.member.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "capital_transfer_contract")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapitalTransferContract {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = LAZY)
  @JoinColumn(name = "seller_id")
  private Member seller;

  @NotNull
  @ManyToOne(fetch = LAZY)
  @JoinColumn(name = "buyer_id")
  private Member buyer;

  @NotNull @ValidIban private String iban;

  @JdbcTypeCode(JSON)
  @NotNull
  private List<CapitalTransferAmount> transferAmounts;

  @NotNull
  @Enumerated(STRING)
  @Setter(PRIVATE)
  private CapitalTransferContractState state;

  @NotNull private byte[] originalContent;

  private byte[] digiDocContainer;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now(ClockHolder.getClock());
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now(ClockHolder.getClock());
  }

  private void requireState(CapitalTransferContractState requiredStatus) {
    if (state != requiredStatus) {
      throw new IllegalStateException(
          "Action requires state " + requiredStatus + ", but current state is " + state + ".");
    }
  }

  public CapitalTransferContract assignBuyer(Member buyer) {
    setBuyer(buyer);
    return this;
  }

  public CapitalTransferContract signBySeller(byte[] container) {
    requireState(CREATED);
    setDigiDocContainer(container);
    setState(SELLER_SIGNED);
    return this;
  }

  public CapitalTransferContract signByBuyer(byte[] updatedContainer) {
    requireState(SELLER_SIGNED);
    setDigiDocContainer(updatedContainer);
    setState(BUYER_SIGNED);
    return this;
  }

  public CapitalTransferContract confirmPaymentByBuyer() {
    requireState(BUYER_SIGNED);
    setState(PAYMENT_CONFIRMED_BY_BUYER);
    return this;
  }

  public CapitalTransferContract confirmPaymentBySeller() {
    requireState(PAYMENT_CONFIRMED_BY_BUYER);
    setState(PAYMENT_CONFIRMED_BY_SELLER);
    return this;
  }

  public CapitalTransferContract executed() {
    requireState(APPROVED);
    setState(EXECUTED);
    return this;
  }

  public CapitalTransferContract approvedAndNotified() {
    requireState(CapitalTransferContractState.EXECUTED);
    this.setState(CapitalTransferContractState.APPROVED_AND_NOTIFIED);
    return this;
  }

  public CapitalTransferContract cancel() {
    if (state == APPROVED || state == APPROVED_AND_NOTIFIED || state == EXECUTED) {
      throw new IllegalStateException(
          "Cannot cancel a contract that is already approved or executed.");
    }
    setState(CapitalTransferContractState.CANCELLED);
    return this;
  }

  public boolean canBeAccessedBy(User user) {
    var isSeller = user.getMemberOrThrow().getId().equals(seller.getId());
    var isBuyer = user.getMemberOrThrow().getId().equals(buyer.getId());

    return isBuyer || isSeller;
  }

  public BigDecimal getTotalPrice() {
    return transferAmounts.stream().map(CapitalTransferAmount::price).reduce(ZERO, BigDecimal::add);
  }

  public String getSellerFirstName() {
    return seller.getFirstName();
  }

  public String getSellerLastName() {
    return seller.getLastName();
  }

  public String getSellerFullName() {
    return seller.getFullName();
  }

  public String getBuyerFirstName() {
    return buyer.getFirstName();
  }

  public String getBuyerLastName() {
    return buyer.getLastName();
  }

  public String getBuyerFullName() {
    return buyer.getFullName();
  }

  public record CapitalTransferAmount(
      MemberCapitalEventType type, BigDecimal price, BigDecimal bookValue) {}
}
