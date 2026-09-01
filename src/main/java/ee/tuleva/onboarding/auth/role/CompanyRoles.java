package ee.tuleva.onboarding.auth.role;

import java.util.List;

public interface CompanyRoles {
  List<CompanyRole> boardMemberCompanies(String personalCode);

  CompanyRole company(String registryCode);

  boolean isBoardMember(String personalCode, String registryCode);

  record CompanyRole(String registryCode, String name) {}
}
