package ee.tuleva.onboarding.capital.transfer;

import ee.tuleva.onboarding.capital.transfer.iban.ValidEstonianIban;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
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

  @NotNull private Long sellerMemberId;

  @NotNull @ValidPersonalCode private String buyerPersonalCode;

  @NotNull @ValidEstonianIban private String iban;

  @NotNull private BigDecimal unitPrice;

  @NotNull private Integer unitCount;

  @NotNull
  @Enumerated(EnumType.STRING)
  private ShareType shareType;

  @NotNull
  @Enumerated(EnumType.STRING)
  private CapitalTransferContractStatus status;

  private Long buyerMemberId;

  @NotNull @Lob private byte[] originalContent;

  @NotNull @Lob private byte[] digiDocContainer;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now =
        ClockHolder.getClock().instant().atZone(ClockHolder.getClock().getZone()).toLocalDateTime();
    createdAt = now;
    updatedAt = now;
    if (status == null) {
      status = CapitalTransferContractStatus.SELLER_SIGNED;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt =
        ClockHolder.getClock().instant().atZone(ClockHolder.getClock().getZone()).toLocalDateTime();
  }

  private void requireState(CapitalTransferContractStatus requiredStatus) {
    if (this.status != requiredStatus) {
      throw new IllegalStateException(
          "Action requires state "
              + requiredStatus
              + ", but current state is "
              + this.status
              + ".");
    }
  }

  public void assignBuyer(Long buyerMemberId) {
    this.setBuyerMemberId(buyerMemberId);
  }

  public void signByBuyer(byte[] updatedContainer) {
    requireState(CapitalTransferContractStatus.SELLER_SIGNED);
    this.setDigiDocContainer(updatedContainer);
    this.setStatus(CapitalTransferContractStatus.BUYER_SIGNED);
  }

  public void confirmPaymentByBuyer() {
    requireState(CapitalTransferContractStatus.BUYER_SIGNED);
    this.setStatus(CapitalTransferContractStatus.PAYMENT_CONFIRMED_BY_BUYER);
  }

  public void confirmPaymentBySeller() {
    requireState(CapitalTransferContractStatus.PAYMENT_CONFIRMED_BY_BUYER);
    this.setStatus(CapitalTransferContractStatus.PAYMENT_CONFIRMED_BY_SELLER);
  }

  public void approveByBoard() {
    requireState(CapitalTransferContractStatus.PAYMENT_CONFIRMED_BY_SELLER);
    this.setStatus(CapitalTransferContractStatus.BOARD_APPROVED);
  }

  public void complete() {
    requireState(CapitalTransferContractStatus.BOARD_APPROVED);
    this.setStatus(CapitalTransferContractStatus.COMPLETED);
  }

  public void cancel() {
    if (this.status == CapitalTransferContractStatus.COMPLETED) {
      throw new IllegalStateException("Cannot cancel a contract that is already completed.");
    }
    this.setStatus(CapitalTransferContractStatus.CANCELLED);
  }
}
