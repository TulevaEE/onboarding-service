package ee.tuleva.onboarding.savings.fund.nav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class NavReleaseApprovalTest {

  @Test
  void twoDifferentApproversReleaseANav() {
    var approval = approvedBy(List.of("approver-one", "approver-two"));

    assertThat(approval.approvedBy()).containsExactly("approver-one", "approver-two");
  }

  @Test
  void approversWhoDifferOnlyInCaseAndSurroundingSpacesAreOnePerson() {
    assertThatThrownBy(() -> approvedBy(List.of("Approver", "approver ")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aBlankApproverIsNoApprover() {
    assertThatThrownBy(() -> approvedBy(List.of("approver-one", " ")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void oneApproverIsNotEnough() {
    assertThatThrownBy(() -> approvedBy(List.of("approver-one")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static NavReleaseApproval approvedBy(List<String> approvers) {
    return new NavReleaseApproval(
        new BigDecimal("1.4066"), new BigDecimal("16000000.00"), approvers);
  }
}
