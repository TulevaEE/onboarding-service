package ee.tuleva.onboarding.savings.fund;

import org.springframework.stereotype.Repository;

import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class IdentityCheckRepository {

  void identityCheckSuccess(SavingFundPayment payment, User user) {
  }

  void identityCheckFailure(SavingFundPayment payment, String reason) {
  }
}
