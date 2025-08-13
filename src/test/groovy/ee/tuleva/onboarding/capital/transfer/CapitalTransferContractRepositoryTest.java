package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractFixture.sampleCapitalTransferContract;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.CREATED;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.SELLER_SIGNED;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.member.Member;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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

  private Member seller;
  private Member buyer;

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

  private User createUser(String personalCode, String firstName, String lastName, String email) {
    User user = new User();
    user.setPersonalCode(personalCode);
    user.setFirstName(firstName);
    user.setLastName(lastName);
    user.setEmail(email);
    return entityManager.persist(user);
  }

  private Member createMember(User user, int memberNumber) {
    Member member = Member.builder().user(user).memberNumber(memberNumber).build();
    return entityManager.persist(member);
  }

  @BeforeEach
  void setUp() {
    // given
    User sellerUser = createUser("37605030299", "Tõnu", "Test", "tonu.test@example.com");
    this.seller = createMember(sellerUser, 101);

    User buyerUser = createUser("60001019906", "Mari", "Maasikas", "mari.maasikas@example.com");
    this.buyer = createMember(buyerUser, 102);
  }

  private CapitalTransferContract createAndSaveContract(
      Member contractSeller, Member contractBuyer) {
    CapitalTransferContract contract =
        sampleCapitalTransferContract()
            .id(null)
            .state(CREATED)
            .seller(contractSeller)
            .buyer(contractBuyer)
            .iban("EE471000001020145685")
            .totalPrice(BigDecimal.ONE)
            .unitCount(BigDecimal.ONE)
            .unitsOfMemberBonus(BigDecimal.ZERO)
            .originalContent(new byte[] {})
            .digiDocContainer(new byte[] {})
            .build();
    return entityManager.persistAndFlush(contract);
  }

  @Test
  @DisplayName("saves and retrieves a contract, setting creation details via @PrePersist")
  void saveAndRetrieve() {
    // given
    CapitalTransferContract contractToSave =
        sampleCapitalTransferContract()
            .id(null)
            .state(SELLER_SIGNED)
            .seller(seller)
            .buyer(buyer)
            .iban("EE471000001020145685")
            .totalPrice(new BigDecimal("200"))
            .unitCount(new BigDecimal("50"))
            .unitsOfMemberBonus(BigDecimal.ONE)
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
    assertThat(retrievedContract.getSeller().getId()).isEqualTo(seller.getId());
    assertThat(retrievedContract.getBuyer().getId()).isEqualTo(buyer.getId());
    assertThat(retrievedContract.getIban()).isEqualTo("EE471000001020145685");
    assertThat(retrievedContract.getTotalPrice()).isEqualByComparingTo("200");
    assertThat(retrievedContract.getUnitsOfMemberBonus()).isEqualByComparingTo("1");
    assertThat(retrievedContract.getUnitCount()).isEqualByComparingTo("50");
    assertThat(retrievedContract.getState()).isEqualTo(CapitalTransferContractState.SELLER_SIGNED);
    assertThat(retrievedContract.getCreatedAt())
        .isEqualTo(NOW_INSTANT.atZone(FIXED_CLOCK.getZone()).toLocalDateTime());
  }

  @Test
  @DisplayName("finds all contracts for a given seller")
  void findAllBySellerId() {
    // given
    User otherUser = createUser("39201070898", "Teine", "Müüja", "teine@example.com");
    Member otherSeller = createMember(otherUser, 103);

    CapitalTransferContract contract1 = createAndSaveContract(seller, buyer);
    CapitalTransferContract contract2 = createAndSaveContract(seller, buyer);
    createAndSaveContract(otherSeller, buyer);

    // when
    List<CapitalTransferContract> contracts = repository.findAllBySellerId(seller.getId());

    // then
    assertThat(contracts).hasSize(2).containsExactlyInAnyOrder(contract1, contract2);
  }

  @Test
  @DisplayName("finds all contracts for a given buyer")
  void findAllByBuyerId() {
    // given
    User otherUser = createUser("38801010000", "Kolmas", "Ostja", "kolmas@example.com");
    Member otherBuyer = createMember(otherUser, 104);

    CapitalTransferContract contract1 = createAndSaveContract(seller, buyer);
    CapitalTransferContract contract2 = createAndSaveContract(seller, buyer);
    createAndSaveContract(seller, otherBuyer);

    // when
    List<CapitalTransferContract> contracts = repository.findAllByBuyerId(buyer.getId());

    // then
    assertThat(contracts).hasSize(2).containsExactlyInAnyOrder(contract1, contract2);
  }

  @Test
  @DisplayName("returns an empty list when no contracts are found for a seller")
  void findAllBySellerId_returnsEmptyList() {
    // when
    List<CapitalTransferContract> contracts = repository.findAllBySellerId(999L);

    // then
    assertThat(contracts).isEmpty();
  }

  @Test
  @DisplayName("returns an empty list when no contracts are found for a buyer")
  void findAllByBuyerId_returnsEmptyList() {
    // when
    List<CapitalTransferContract> contracts = repository.findAllByBuyerId(999L);

    // then
    assertThat(contracts).isEmpty();
  }
}
