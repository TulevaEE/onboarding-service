package ee.tuleva.onboarding.kyb;

import java.util.Set;

final class KybRelationshipRoles {

  static final String BOARD_MEMBER_ROLE = "JUHL"; // juhatuse liige (board member)
  static final String NASDAQ_CSD_SHAREHOLDER_ROLE = "O"; // osanik (shares held in Nasdaq CSD)
  static final String BUSINESS_REGISTRY_SHAREHOLDER_ROLE =
      "OSAN"; // osanik (listed in the registry)
  static final String BENEFICIAL_OWNER_ROLE = "W"; // tegelik kasusaaja (beneficial owner)

  // Both codes mean osanik (shareholder): "O" when shares are in Nasdaq CSD, "OSAN" when they are
  // listed directly in the business registry.
  static final Set<String> SHAREHOLDER_ROLES =
      Set.of(NASDAQ_CSD_SHAREHOLDER_ROLE, BUSINESS_REGISTRY_SHAREHOLDER_ROLE);

  // The only roles an ownership rule models. Everything else (founders, the Nasdaq CSD share
  // registrar ORP/ARP, prokurist, contact persons, liquidators, auditors, ...) is not a related
  // person for screening.
  static final Set<String> RELATED_PERSON_ROLES =
      Set.of(
          BOARD_MEMBER_ROLE,
          NASDAQ_CSD_SHAREHOLDER_ROLE,
          BUSINESS_REGISTRY_SHAREHOLDER_ROLE,
          BENEFICIAL_OWNER_ROLE);

  private KybRelationshipRoles() {}
}
