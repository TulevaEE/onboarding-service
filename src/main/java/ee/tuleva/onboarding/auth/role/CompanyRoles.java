package ee.tuleva.onboarding.auth.role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRoles {
  List<CompanyRole> boardMemberCompanies(String personalCode);

  CompanyRole company(String registryCode);

  Optional<CompanyRole> findCompany(String registryCode);

  boolean isBoardMember(String personalCode, String registryCode);

  record CompanyRole(UUID id, String registryCode, String name) {}
}
