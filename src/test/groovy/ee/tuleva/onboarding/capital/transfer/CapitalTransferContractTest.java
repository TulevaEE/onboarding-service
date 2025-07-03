package ee.tuleva.onboarding.capital.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CapitalTransferContractTest {

  private CapitalTransferContract.CapitalTransferContractBuilder contractBuilder;
  private byte[] updatedContainer;
  private static Validator validator;

  @BeforeAll
  static void beforeAll() {
    // given
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @BeforeEach
  void setUp() {
    // given
    updatedContainer = "buyer_signed".getBytes();
    contractBuilder =
        CapitalTransferContract.builder()
            .sellerMemberId(1L)
            .buyerPersonalCode("38801010000")
            .iban("EE471000001020145685")
            .unitPrice(BigDecimal.TEN)
            .unitCount(100)
            .shareType(ShareType.MEMBER_CAPITAL)
            .originalContent("original".getBytes())
            .digiDocContainer("seller_signed".getBytes())
            .status(CapitalTransferContractStatus.SELLER_SIGNED);
  }

  @Nested
  @DisplayName("Lifecycle Callbacks")
  class LifecycleCallbacks {
    @Test
    @DisplayName("onCreate sets creation and update timestamps")
    void onCreate_setsTimestamps() {
      // given
      CapitalTransferContract contract = contractBuilder.build();

      // when
      contract.onCreate();

      // then
      assertThat(contract.getCreatedAt()).isNotNull();
      assertThat(contract.getUpdatedAt()).isNotNull();
      assertThat(contract.getCreatedAt()).isEqualTo(contract.getUpdatedAt());
    }

    @Test
    @DisplayName("onCreate sets default status if it is null")
    void onCreate_setsDefaultStatus() {
      // given
      CapitalTransferContract contract = contractBuilder.status(null).build();

      // when
      contract.onCreate();

      // then
      assertThat(contract.getStatus()).isEqualTo(CapitalTransferContractStatus.SELLER_SIGNED);
    }

    @Test
    @DisplayName("onUpdate changes the update timestamp")
    void onUpdate_changesTimestamp() {
      // given
      Clock fixedClock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
      ClockHolder.setClock(fixedClock);
      CapitalTransferContract contract = contractBuilder.build();
      contract.onCreate();
      LocalDateTime initialUpdateTime = contract.getUpdatedAt();

      // when
      Clock laterClock = Clock.offset(fixedClock, java.time.Duration.ofSeconds(10));
      ClockHolder.setClock(laterClock);
      contract.onUpdate();

      // then
      assertThat(contract.getUpdatedAt()).isAfter(initialUpdateTime);
      ClockHolder.setDefaultClock();
    }
  }

  @Nested
  @DisplayName("State Transitions")
  class StateTransitions {
    @Test
    @DisplayName("follows the happy path correctly")
    void happyPath() {
      // given
      CapitalTransferContract contract = contractBuilder.build();

      // when / then: sign by buyer
      contract.signByBuyer(updatedContainer);
      assertThat(contract.getStatus()).isEqualTo(CapitalTransferContractStatus.BUYER_SIGNED);
      assertThat(contract.getDigiDocContainer()).isEqualTo(updatedContainer);

      // when / then: confirm payment by buyer
      contract.confirmPaymentByBuyer();
      assertThat(contract.getStatus())
          .isEqualTo(CapitalTransferContractStatus.PAYMENT_CONFIRMED_BY_BUYER);

      // when / then: confirm payment by seller
      contract.confirmPaymentBySeller();
      assertThat(contract.getStatus())
          .isEqualTo(CapitalTransferContractStatus.PAYMENT_CONFIRMED_BY_SELLER);

      // when / then: approve by board
      contract.approveByBoard();
      assertThat(contract.getStatus()).isEqualTo(CapitalTransferContractStatus.BOARD_APPROVED);

      // when / then: complete
      contract.complete();
      assertThat(contract.getStatus()).isEqualTo(CapitalTransferContractStatus.COMPLETED);
    }

    @Test
    @DisplayName("can assign a buyer member id")
    void assignBuyer() {
      // given
      CapitalTransferContract contract = contractBuilder.build();
      Long buyerId = 2L;

      // when
      contract.assignBuyer(buyerId);

      // then
      assertThat(contract.getBuyerMemberId()).isEqualTo(buyerId);
    }

    @Test
    @DisplayName("throws exception for invalid transitions")
    void invalidTransitions() {
      // given
      CapitalTransferContract contract = contractBuilder.build();

      // then
      assertThrows(IllegalStateException.class, contract::confirmPaymentByBuyer);
      assertThrows(IllegalStateException.class, contract::confirmPaymentBySeller);
      assertThrows(IllegalStateException.class, contract::approveByBoard);
      assertThrows(IllegalStateException.class, contract::complete);
    }

    @Test
    @DisplayName("can be cancelled from any non-completed state")
    void cancel() {
      // given
      CapitalTransferContract contract =
          contractBuilder.status(CapitalTransferContractStatus.PAYMENT_CONFIRMED_BY_SELLER).build();

      // when
      contract.cancel();

      // then
      assertThat(contract.getStatus()).isEqualTo(CapitalTransferContractStatus.CANCELLED);
    }

    @Test
    @DisplayName("throws exception when cancelling a completed contract")
    void cancel_failsOnCompleted() {
      // given
      CapitalTransferContract contract =
          contractBuilder.status(CapitalTransferContractStatus.COMPLETED).build();

      // then
      assertThrows(IllegalStateException.class, contract::cancel);
    }
  }

  @Nested
  @DisplayName("Validation")
  class ValidationTests {
    @Test
    @DisplayName("passes for a valid contract")
    void validation_passesForValidData() {
      // given
      CapitalTransferContract contract = contractBuilder.build();

      // when
      Set<ConstraintViolation<CapitalTransferContract>> violations = validator.validate(contract);

      // then
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("fails for a non-Estonian IBAN")
    void validation_failsForInvalidIban() {
      // given
      CapitalTransferContract contract = contractBuilder.iban("DE75512108001245126199").build();

      // when
      Set<ConstraintViolation<CapitalTransferContract>> violations = validator.validate(contract);

      // then
      assertThat(violations).hasSize(1);
      ConstraintViolation<CapitalTransferContract> violation = violations.iterator().next();
      assertThat(violation.getPropertyPath().toString()).isEqualTo("iban");
      assertThat(violation.getMessage())
          .isEqualTo("IBAN must be an Estonian IBAN (starting with EE).");
    }

    @Test
    @DisplayName("fails for an invalid personal code")
    void validation_failsForInvalidPersonalCode() {
      // given
      CapitalTransferContract contract = contractBuilder.buyerPersonalCode("123").build();

      // when
      Set<ConstraintViolation<CapitalTransferContract>> violations = validator.validate(contract);

      // then
      assertThat(violations).hasSize(1);
      assertThat(
              violations
                  .iterator()
                  .next()
                  .getConstraintDescriptor()
                  .getAnnotation()
                  .annotationType())
          .isEqualTo(ValidPersonalCode.class);
    }

    @Test
    @DisplayName("fails when sellerMemberId is null")
    void validation_failsForNullSellerMemberId() {
      // given
      CapitalTransferContract contract = contractBuilder.sellerMemberId(null).build();

      // when
      Set<ConstraintViolation<CapitalTransferContract>> violations = validator.validate(contract);

      // then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getPropertyPath().toString())
          .isEqualTo("sellerMemberId");
    }

    @Test
    @DisplayName("fails when unitPrice is null")
    void validation_failsForNullUnitPrice() {
      // given
      CapitalTransferContract contract = contractBuilder.unitPrice(null).build();

      // when
      Set<ConstraintViolation<CapitalTransferContract>> violations = validator.validate(contract);

      // then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("unitPrice");
    }

    @Test
    @DisplayName("fails when unitCount is null")
    void validation_failsForNullUnitCount() {
      // given
      CapitalTransferContract contract = contractBuilder.unitCount(null).build();

      // when
      Set<ConstraintViolation<CapitalTransferContract>> violations = validator.validate(contract);

      // then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("unitCount");
    }

    @Test
    @DisplayName("fails when shareType is null")
    void validation_failsForNullShareType() {
      // given
      CapitalTransferContract contract = contractBuilder.shareType(null).build();

      // when
      Set<ConstraintViolation<CapitalTransferContract>> violations = validator.validate(contract);

      // then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("shareType");
    }

    @Test
    @DisplayName("fails when originalContent is null")
    void validation_failsForNullOriginalContent() {
      // given
      CapitalTransferContract contract = contractBuilder.originalContent(null).build();

      // when
      Set<ConstraintViolation<CapitalTransferContract>> violations = validator.validate(contract);

      // then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getPropertyPath().toString())
          .isEqualTo("originalContent");
    }

    @Test
    @DisplayName("fails when digiDocContainer is null")
    void validation_failsForNullDigiDocContainer() {
      // given
      CapitalTransferContract contract = contractBuilder.digiDocContainer(null).build();

      // when
      Set<ConstraintViolation<CapitalTransferContract>> violations = validator.validate(contract);

      // then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getPropertyPath().toString())
          .isEqualTo("digiDocContainer");
    }
  }
}
