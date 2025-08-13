package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractFixture.sampleCapitalTransferContract;
import static ee.tuleva.onboarding.user.MemberFixture.memberFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.member.Member;
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
  private byte[] sellerSignedContainer;
  private byte[] buyerSignedContainer;
  private Member seller;
  private Member buyer;
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
    seller = memberFixture().id(1L).memberNumber(101).build();
    buyer = memberFixture().id(2L).memberNumber(102).build();
    sellerSignedContainer = "seller_signed".getBytes();
    buyerSignedContainer = "buyer_signed".getBytes();
    contractBuilder =
        sampleCapitalTransferContract()
            .seller(seller)
            .buyer(buyer)
            .iban("EE471000001020145685")
            .totalPrice(new BigDecimal("100.0"))
            .unitCount(new BigDecimal("100.0"))
            .unitsOfMemberBonus(new BigDecimal("2.0"))
            .originalContent("original".getBytes())
            .digiDocContainer(null)
            .state(CapitalTransferContractState.CREATED);
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
      assertThat(contract.getState()).isEqualTo(CapitalTransferContractState.CREATED);

      // when / then: sign by seller
      contract.signBySeller(sellerSignedContainer);
      assertThat(contract.getState()).isEqualTo(CapitalTransferContractState.SELLER_SIGNED);
      assertThat(contract.getDigiDocContainer()).isEqualTo(sellerSignedContainer);

      // when / then: sign by buyer
      contract.signByBuyer(buyerSignedContainer);
      assertThat(contract.getState()).isEqualTo(CapitalTransferContractState.BUYER_SIGNED);
      assertThat(contract.getDigiDocContainer()).isEqualTo(buyerSignedContainer);

      // when / then: confirm payment by buyer
      contract.confirmPaymentByBuyer();
      assertThat(contract.getState())
          .isEqualTo(CapitalTransferContractState.PAYMENT_CONFIRMED_BY_BUYER);

      // when / then: confirm payment by seller
      contract.confirmPaymentBySeller();
      assertThat(contract.getState())
          .isEqualTo(CapitalTransferContractState.PAYMENT_CONFIRMED_BY_SELLER);
    }

    @Test
    @DisplayName("can assign a buyer member")
    void assignBuyer() {
      // given
      CapitalTransferContract contract = contractBuilder.build();
      Member newBuyer = Member.builder().id(3L).build();

      // when
      contract.assignBuyer(newBuyer);

      // then
      assertThat(contract.getBuyer()).isEqualTo(newBuyer);
    }

    @Test
    @DisplayName("throws exception for invalid transitions")
    void invalidTransitions() {
      // given
      CapitalTransferContract contractInCreatedState = contractBuilder.build();
      CapitalTransferContract contractInSellerSignedState =
          contractBuilder.state(CapitalTransferContractState.SELLER_SIGNED).build();

      // then
      assertThrows(
          IllegalStateException.class,
          () -> contractInCreatedState.signByBuyer(buyerSignedContainer));
      assertThrows(IllegalStateException.class, contractInCreatedState::confirmPaymentByBuyer);
      assertThrows(
          IllegalStateException.class, contractInSellerSignedState::confirmPaymentBySeller);
    }

    @Test
    @DisplayName("can be cancelled from any non-completed state")
    void cancel() {
      // given
      CapitalTransferContract contract =
          contractBuilder.state(CapitalTransferContractState.PAYMENT_CONFIRMED_BY_SELLER).build();

      // when
      contract.cancel();

      // then
      assertThat(contract.getState()).isEqualTo(CapitalTransferContractState.CANCELLED);
    }

    @Test
    @DisplayName("throws exception when cancelling an approved contract")
    void cancel_failsOnCompleted() {
      // given
      CapitalTransferContract contract =
          contractBuilder.state(CapitalTransferContractState.APPROVED).build();

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
    @DisplayName("fails for a invalid IBAN")
    void validation_failsForInvalidIban() {
      // given
      CapitalTransferContract contract = contractBuilder.iban("DE75512108001245126190").build();

      // when
      Set<ConstraintViolation<CapitalTransferContract>> violations = validator.validate(contract);

      // then
      assertThat(violations).hasSize(1);
      ConstraintViolation<CapitalTransferContract> violation = violations.iterator().next();
      assertThat(violation.getPropertyPath().toString()).isEqualTo("iban");
      assertThat(violation.getMessage())
          .isEqualTo("{ee.tuleva.onboarding.capital.transfer.iban.ValidIban.message}");
    }

    @Test
    @DisplayName("fails when seller is null")
    void validation_failsForNullSeller() {
      // given
      CapitalTransferContract contract = contractBuilder.seller(null).build();

      // when
      Set<ConstraintViolation<CapitalTransferContract>> violations = validator.validate(contract);

      // then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("seller");
    }

    @Test
    @DisplayName("fails when buyer is null")
    void validation_failsForNullBuyer() {
      // given
      CapitalTransferContract contract = contractBuilder.buyer(null).build();

      // when
      Set<ConstraintViolation<CapitalTransferContract>> violations = validator.validate(contract);

      // then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("buyer");
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
    @DisplayName("passes when digiDocContainer is null")
    void validation_passesForNullDigiDocContainer() {
      // given
      CapitalTransferContract contract = contractBuilder.digiDocContainer(null).build();

      // when
      Set<ConstraintViolation<CapitalTransferContract>> violations = validator.validate(contract);

      // then
      assertThat(violations).isEmpty();
    }
  }
}
