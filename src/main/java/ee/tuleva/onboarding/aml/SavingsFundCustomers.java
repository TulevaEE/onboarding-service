package ee.tuleva.onboarding.aml;

import java.util.List;

public interface SavingsFundCustomers {

  List<String> personalCodes();

  List<SavingsFundGuardian> guardians();
}
