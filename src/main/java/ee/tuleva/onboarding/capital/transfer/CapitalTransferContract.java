package ee.tuleva.onboarding.capital.transfer;

import ee.tuleva.onboarding.capital.transfer.iban.ValidIban;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.member.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "capital_transfer_contract")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapitalTransferContract {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "seller_id")
  private Member seller;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "buyer_id")
  private Member buyer;

  @NotNull @ValidIban private String iban;

  @NotNull private BigDecimal totalPrice;

  @NotNull private BigDecimal unitCount;

  @NotNull private BigDecimal unitsOfMemberBonus;

  @NotNull
  @Enumerated(EnumType.STRING)
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
    if (this.state != requiredStatus) {
      throw new IllegalStateException(
          "Action requires state " + requiredStatus + ", but current state is " + this.state + ".");
    }
  }

  public CapitalTransferContract assignBuyer(Member buyer) {
    this.setBuyer(buyer);
    return this;
  }

  public CapitalTransferContract signBySeller(byte[] container) {
    requireState(CapitalTransferContractState.CREATED);
    this.setDigiDocContainer(container);
    this.setState(CapitalTransferContractState.SELLER_SIGNED);
    return this;
  }

  public CapitalTransferContract signByBuyer(byte[] updatedContainer) {
    requireState(CapitalTransferContractState.SELLER_SIGNED);
    this.setDigiDocContainer(updatedContainer);
    this.setState(CapitalTransferContractState.BUYER_SIGNED);
    return this;
  }

  public CapitalTransferContract confirmPaymentByBuyer() {
    requireState(CapitalTransferContractState.BUYER_SIGNED);
    this.setState(CapitalTransferContractState.PAYMENT_CONFIRMED_BY_BUYER);
    return this;
  }

  public CapitalTransferContract confirmPaymentBySeller() {
    requireState(CapitalTransferContractState.PAYMENT_CONFIRMED_BY_BUYER);
    this.setState(CapitalTransferContractState.PAYMENT_CONFIRMED_BY_SELLER);
    return this;
  }

  public CapitalTransferContract cancel() {
    if (this.state == CapitalTransferContractState.APPROVED) {
      throw new IllegalStateException("Cannot cancel a contract that is already approved.");
    }
    this.setState(CapitalTransferContractState.CANCELLED);
    return this;
  }

  public boolean canBeAccessedBy(User user) {
    var isSeller = user.getMemberOrThrow().getId().equals(seller.getId());
    var isBuyer = user.getMemberOrThrow().getId().equals(buyer.getId());

    return isBuyer || isSeller;
  }
}
