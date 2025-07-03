package ee.tuleva.onboarding.capital.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class CapitalTransferContractRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private CapitalTransferContractRepository repository;

  private String buyerPersonalCode;
  private Long sellerMemberId;

  private static final Instant NOW_INSTANT = Instant.parse("2025-05-16T12:00:00.00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(NOW_INSTANT, ZoneId.of("UTC"));

  @BeforeAll
  static void setupClock() {
    ClockHolder.setClock(FIXED_CLOCK);
  }

  @AfterAll
  static void resetClock() {
    ClockHolder.setDefaultClock();
  }

  @BeforeEach
  void setUp() {
    buyerPersonalCode = "37605030299";
    sellerMemberId = 101L;
  }

  private CapitalTransferContract createAndSaveContract(
      Long sellerId, String buyerCode, String iban, CapitalTransferContractStatus status) {
    CapitalTransferContract contract =
        CapitalTransferContract.builder()
            .sellerMemberId(sellerId)
            .buyerPersonalCode(buyerCode)
            .iban(iban)
            .unitPrice(BigDecimal.ONE)
            .unitCount(1)
            .shareType(ShareType.MEMBER_CAPITAL)
            .originalContent(new byte[] {})
            .digiDocContainer(new byte[] {})
            .status(status)
            .build();
    return entityManager.persistAndFlush(contract);
  }

  @Test
  @DisplayName("saves and retrieves a contract, setting creation details via @PrePersist")
  void saveAndRetrieve() {
    // given
    CapitalTransferContract contractToSave =
        CapitalTransferContract.builder()
            .sellerMemberId(sellerMemberId)
            .buyerPersonalCode(buyerPersonalCode)
            .iban("EE471000001020145685")
            .unitPrice(new BigDecimal("12.34"))
            .unitCount(50)
            .shareType(ShareType.MEMBER_BONUS)
            .originalContent(new byte[] {1, 2})
            .digiDocContainer(new byte[] {3, 4})
            .build();

    // when
    entityManager.persistAndFlush(contractToSave);
    entityManager.clear();
    CapitalTransferContract retrievedContract =
        repository.findById(contractToSave.getId()).orElseThrow();

    // then
    assertThat(retrievedContract.getId()).isNotNull();
    assertThat(retrievedContract.getSellerMemberId()).isEqualTo(sellerMemberId);
    assertThat(retrievedContract.getBuyerPersonalCode()).isEqualTo(buyerPersonalCode);
    assertThat(retrievedContract.getIban()).isEqualTo("EE471000001020145685");
    assertThat(retrievedContract.getUnitPrice()).isEqualByComparingTo("12.34");
    assertThat(retrievedContract.getShareType()).isEqualTo(ShareType.MEMBER_BONUS);
    assertThat(retrievedContract.getStatus())
        .isEqualTo(CapitalTransferContractStatus.SELLER_SIGNED);
    assertThat(retrievedContract.getCreatedAt())
        .isEqualTo(NOW_INSTANT.atZone(FIXED_CLOCK.getZone()).toLocalDateTime());
  }

  @Test
  @DisplayName("finds contracts by status")
  void findAllByStatus() {
    // given
    createAndSaveContract(
        1L,
        "37007100232",
        "EE471000001020145685",
        CapitalTransferContractStatus.PAYMENT_CONFIRMED_BY_SELLER);
    createAndSaveContract(
        2L, "39107050268", "EE471000001020145685", CapitalTransferContractStatus.SELLER_SIGNED);

    // when
    List<CapitalTransferContract> foundContracts =
        repository.findAllByStatus(CapitalTransferContractStatus.PAYMENT_CONFIRMED_BY_SELLER);

    // then
    assertThat(foundContracts).hasSize(1);
    assertThat(foundContracts.get(0).getSellerMemberId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("finds a contract for a buyer awaiting signature")
  void findByBuyerPersonalCodeAndStatus() {
    // given
    String otherBuyerCode = "38801010000";
    CapitalTransferContract targetContract =
        createAndSaveContract(
            sellerMemberId,
            buyerPersonalCode,
            "EE471000001020145685",
            CapitalTransferContractStatus.SELLER_SIGNED);
    createAndSaveContract(
        sellerMemberId + 1,
        otherBuyerCode,
        "EE471000001020145685",
        CapitalTransferContractStatus.SELLER_SIGNED);
    createAndSaveContract(
        sellerMemberId + 2,
        buyerPersonalCode,
        "EE471000001020145685",
        CapitalTransferContractStatus.BUYER_SIGNED);

    // when
    Optional<CapitalTransferContract> foundContract =
        repository.findByBuyerPersonalCodeAndStatus(
            buyerPersonalCode, CapitalTransferContractStatus.SELLER_SIGNED);

    // then
    assertThat(foundContract).isPresent();
    assertThat(foundContract.get().getId()).isEqualTo(targetContract.getId());
    assertThat(foundContract.get().getSellerMemberId()).isEqualTo(sellerMemberId);
  }
}
