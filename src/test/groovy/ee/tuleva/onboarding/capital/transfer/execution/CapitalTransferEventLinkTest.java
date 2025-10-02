package ee.tuleva.onboarding.capital.transfer.execution;

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractRepository;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractService;
import ee.tuleva.onboarding.user.member.Member;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Capital Transfer Event Link Tests")
class CapitalTransferEventLinkTest {

  @Mock private CapitalTransferContractRepository contractRepository;
  @Mock private MemberCapitalEventRepository memberCapitalEventRepository;
  @Mock private CapitalTransferValidator validator;
  @Mock private CapitalTransferContractService capitalTransferContractService;
  @Mock private CapitalTransferEventLinkRepository linkRepository;

  @Mock private CapitalTransferContract contract;
  @Mock private Member sellerMember;
  @Mock private Member buyerMember;

  private CapitalTransferExecutor executor;

  private final BigDecimal OWNERSHIP_UNIT_PRICE = new BigDecimal("1.25000");
  private final BigDecimal BOOK_VALUE = new BigDecimal("100.00000");

  @BeforeEach
  void setUp() {
    executor =
        new CapitalTransferExecutor(
            contractRepository,
            memberCapitalEventRepository,
            validator,
            capitalTransferContractService,
            linkRepository);
  }

  @Test
  @DisplayName("Should create links for each member capital event")
  void shouldCreateLinkForEachMemberCapitalEvent() {
    // Given
    setupBasicMocks();

    CapitalTransferAmount payment =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT, new BigDecimal("125.00"), BOOK_VALUE, new BigDecimal("1.0"));
    when(contract.getTransferAmounts()).thenReturn(List.of(payment));

    // Mock repository calls for CAPITAL_PAYMENT only
    when(memberCapitalEventRepository.getTotalFiatValueByMemberIdAndType(101L, CAPITAL_PAYMENT))
        .thenReturn(new BigDecimal("1000.00"));
    when(memberCapitalEventRepository.getTotalOwnershipUnitsByMemberIdAndType(
            101L, CAPITAL_PAYMENT))
        .thenReturn(new BigDecimal("800.00"));

    when(memberCapitalEventRepository.save(any(MemberCapitalEvent.class)))
        .thenAnswer(
            invocation -> {
              MemberCapitalEvent event = invocation.getArgument(0);
              event.setId(System.nanoTime());
              return event;
            });

    // When
    executor.execute(contract);

    // Then
    verify(memberCapitalEventRepository, times(2)).save(any(MemberCapitalEvent.class));

    // Verify 2 links created
    ArgumentCaptor<CapitalTransferEventLink> linkCaptor =
        ArgumentCaptor.forClass(CapitalTransferEventLink.class);
    verify(linkRepository, times(2)).save(linkCaptor.capture());

    List<CapitalTransferEventLink> links = linkCaptor.getAllValues();

    assertThat(links).hasSize(2);
    assertThat(links).allMatch(link -> link.getCapitalTransferContract().equals(contract));
    assertThat(links).allMatch(link -> link.getMemberCapitalEvent() != null);
    assertThat(links).allMatch(link -> link.getMemberCapitalEvent().getId() != null);
  }

  @Test
  @DisplayName("Should create correct number of links for multiple transfer amounts")
  void shouldCreateCorrectNumberOfLinksForMultipleTransferAmounts() {
    // Given
    setupBasicMocks();

    CapitalTransferAmount payment =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT, new BigDecimal("125.00"), BOOK_VALUE, new BigDecimal("1.0"));
    CapitalTransferAmount bonus =
        new CapitalTransferAmount(
            MEMBERSHIP_BONUS,
            new BigDecimal("62.50"),
            new BigDecimal("50.00"),
            new BigDecimal("1.0"));
    when(contract.getTransferAmounts()).thenReturn(List.of(payment, bonus));

    // Mock repository calls for both types
    when(memberCapitalEventRepository.getTotalFiatValueByMemberIdAndType(101L, CAPITAL_PAYMENT))
        .thenReturn(new BigDecimal("1000.00"));
    when(memberCapitalEventRepository.getTotalOwnershipUnitsByMemberIdAndType(
            101L, CAPITAL_PAYMENT))
        .thenReturn(new BigDecimal("800.00"));
    when(memberCapitalEventRepository.getTotalFiatValueByMemberIdAndType(101L, MEMBERSHIP_BONUS))
        .thenReturn(new BigDecimal("500.00"));
    when(memberCapitalEventRepository.getTotalOwnershipUnitsByMemberIdAndType(
            101L, MEMBERSHIP_BONUS))
        .thenReturn(new BigDecimal("400.00"));

    when(memberCapitalEventRepository.save(any(MemberCapitalEvent.class)))
        .thenAnswer(
            invocation -> {
              MemberCapitalEvent event = invocation.getArgument(0);
              event.setId(System.nanoTime());
              return event;
            });

    // When
    executor.execute(contract);

    // Then
    verify(memberCapitalEventRepository, times(4)).save(any(MemberCapitalEvent.class));

    verify(linkRepository, times(4)).save(any(CapitalTransferEventLink.class));
  }

  @Test
  @DisplayName("Should not create links when transfers are skipped")
  void shouldNotCreateLinksWhenTransfersAreSkipped() {
    // Given
    setupBasicMocks();

    CapitalTransferAmount zeroAmount =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("1.0"));
    when(contract.getTransferAmounts()).thenReturn(List.of(zeroAmount));
    when(validator.shouldSkipTransfer(zeroAmount)).thenReturn(true);

    // When
    executor.execute(contract);

    // Then
    verify(memberCapitalEventRepository, never()).save(any(MemberCapitalEvent.class));
    verify(linkRepository, never()).save(any(CapitalTransferEventLink.class));
  }

  @Test
  @DisplayName("Should handle mixed skipped and valid transfers correctly")
  void shouldHandleMixedSkippedAndValidTransfersCorrectly() {
    // Given
    setupBasicMocks();

    CapitalTransferAmount zeroAmount =
        new CapitalTransferAmount(
            CAPITAL_PAYMENT, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("1.0"));
    CapitalTransferAmount validAmount =
        new CapitalTransferAmount(
            MEMBERSHIP_BONUS,
            new BigDecimal("62.50"),
            new BigDecimal("50.00"),
            new BigDecimal("1.0"));
    when(contract.getTransferAmounts()).thenReturn(List.of(zeroAmount, validAmount));

    when(validator.shouldSkipTransfer(zeroAmount)).thenReturn(true);
    when(validator.shouldSkipTransfer(validAmount)).thenReturn(false);

    // Mock repository calls for MEMBERSHIP_BONUS only (since CAPITAL_PAYMENT is skipped)
    when(memberCapitalEventRepository.getTotalFiatValueByMemberIdAndType(101L, MEMBERSHIP_BONUS))
        .thenReturn(new BigDecimal("500.00"));
    when(memberCapitalEventRepository.getTotalOwnershipUnitsByMemberIdAndType(
            101L, MEMBERSHIP_BONUS))
        .thenReturn(new BigDecimal("400.00"));

    when(memberCapitalEventRepository.save(any(MemberCapitalEvent.class)))
        .thenAnswer(
            invocation -> {
              MemberCapitalEvent event = invocation.getArgument(0);
              event.setId(System.nanoTime());
              return event;
            });

    // When
    executor.execute(contract);

    // Then
    verify(memberCapitalEventRepository, times(2)).save(any(MemberCapitalEvent.class));
    verify(linkRepository, times(2)).save(any(CapitalTransferEventLink.class));
  }

  private void setupBasicMocks() {
    when(contract.getId()).thenReturn(1L);
    when(contract.getSeller()).thenReturn(sellerMember);
    when(contract.getBuyer()).thenReturn(buyerMember);
    when(sellerMember.getId()).thenReturn(101L);
    when(buyerMember.getId()).thenReturn(102L);
  }
}
