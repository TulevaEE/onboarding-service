package ee.tuleva.onboarding.savings.fund.redemption.email;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_REDEMPTION_CHILD;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_REDEMPTION_COMPANY;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_REDEMPTION_PERSON;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static java.util.Locale.ENGLISH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.auth.role.CompanyRoles;
import ee.tuleva.onboarding.auth.role.CompanyRoles.CompanyRole;
import ee.tuleva.onboarding.party.Party;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyResolver;
import ee.tuleva.onboarding.savings.fund.notification.RedemptionRequestedEvent;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedemptionConfirmationMapperTest {

  private static final Locale ESTONIAN = Locale.of("et");
  private static final String CHILD_CODE = "51111111111";
  private static final String REGISTRY_CODE = "16001234";
  private static final Map<String, Object> REQUEST_FACTS =
      Map.of(
          "fname", "Jordan",
          "amount", "250.00",
          "fundUnits", "231.48148",
          "iban", "EE47 **** **** **** 5685",
          "cancellationDeadline", "30.09.2025 16:00",
          "payoutDeadline", "01.10.2025 16:00");

  @Mock private PartyResolver partyResolver;
  @Mock private CompanyRoles companyRoles;

  @InjectMocks private RedemptionConfirmationMapper mapper;

  private final User requester =
      User.builder().personalCode("38888888888").firstName("JORDAN").lastName("TESTER").build();

  @Test
  void aPersonRedeemingTheirOwnUnitsGetsThePersonConfirmationWithTheRequestFactsAndNoLastName() {
    var confirmation =
        mapper.map(requester, requested(new PartyId(PERSON, requester.getPersonalCode())), ENGLISH);

    assertThat(confirmation)
        .isEqualTo(new RedemptionConfirmation(SAVINGS_FUND_REDEMPTION_PERSON, REQUEST_FACTS));
    verifyNoInteractions(partyResolver, companyRoles);
  }

  @Test
  void aGuardianRedeemingAChildsUnitsGetsTheChildConfirmationNamingTheChild() {
    var child = new PartyId(PERSON, CHILD_CODE);
    given(partyResolver.resolve(child))
        .willReturn(Optional.of(namedParty(CHILD_CODE, "MARI MAASIKAS")));

    var confirmation = mapper.map(requester, requested(child), ENGLISH);

    assertThat(confirmation)
        .isEqualTo(
            new RedemptionConfirmation(
                SAVINGS_FUND_REDEMPTION_CHILD, requestFactsAnd("recipientName", "Mari Maasikas")));
  }

  @Test
  void aChildWhoseNameCannotBeResolvedGetsTheChildConfirmationWithoutARecipientName() {
    var child = new PartyId(PERSON, CHILD_CODE);
    given(partyResolver.resolve(child)).willReturn(Optional.empty());

    var confirmation = mapper.map(requester, requested(child), ENGLISH);

    assertThat(confirmation)
        .isEqualTo(new RedemptionConfirmation(SAVINGS_FUND_REDEMPTION_CHILD, REQUEST_FACTS));
  }

  @Test
  void aBoardMemberRedeemingACompanysUnitsGetsTheCompanyConfirmationNamingItAsRegistered() {
    given(companyRoles.findCompany(REGISTRY_CODE))
        .willReturn(Optional.of(new CompanyRole(UUID.randomUUID(), REGISTRY_CODE, "Acme OÜ")));

    var confirmation =
        mapper.map(requester, requested(new PartyId(LEGAL_ENTITY, REGISTRY_CODE)), ENGLISH);

    assertThat(confirmation)
        .isEqualTo(
            new RedemptionConfirmation(
                SAVINGS_FUND_REDEMPTION_COMPANY, requestFactsAnd("recipientName", "Acme OÜ")));
  }

  @Test
  void aCompanyNotOnFileGetsTheCompanyConfirmationWithoutARecipientName() {
    given(companyRoles.findCompany(REGISTRY_CODE)).willReturn(Optional.empty());

    var confirmation =
        mapper.map(requester, requested(new PartyId(LEGAL_ENTITY, REGISTRY_CODE)), ENGLISH);

    assertThat(confirmation)
        .isEqualTo(new RedemptionConfirmation(SAVINGS_FUND_REDEMPTION_COMPANY, REQUEST_FACTS));
  }

  @Test
  void deadlinesAreShownInEstonianTimeAlsoInWinter() {
    var event =
        new RedemptionRequestedEvent(
            UUID.randomUUID(),
            7L,
            new PartyId(PERSON, requester.getPersonalCode()),
            new BigDecimal("250.00"),
            new BigDecimal("231.48148"),
            "EE471000001020145685",
            Instant.parse("2026-01-12T15:00:00Z"),
            Instant.parse("2026-01-13T14:00:00Z"),
            Instant.parse("2026-01-14T14:00:00Z"));

    var confirmation = mapper.map(requester, event, ENGLISH);

    assertThat(confirmation.mergeVars())
        .containsEntry("cancellationDeadline", "13.01.2026 16:00")
        .containsEntry("payoutDeadline", "14.01.2026 16:00");
  }

  @Test
  void amountsAreWrittenTheEstonianWayInAnEstonianEmail() {
    var confirmation =
        mapper.map(
            requester,
            requestedOf(new BigDecimal("1234.56"), new BigDecimal("1234.56789")),
            ESTONIAN);

    assertThat(confirmation.mergeVars())
        .containsEntry("amount", "1\u00A0234,56")
        .containsEntry("fundUnits", "1\u00A0234,56789");
  }

  @Test
  void amountsAreWrittenTheEnglishWayInAnEnglishEmail() {
    var confirmation =
        mapper.map(
            requester,
            requestedOf(new BigDecimal("1234.56"), new BigDecimal("1234.56789")),
            ENGLISH);

    assertThat(confirmation.mergeVars())
        .containsEntry("amount", "1,234.56")
        .containsEntry("fundUnits", "1,234.56789");
  }

  @Test
  void theAmountAlwaysHasTwoDecimalsAndTheUnitsFive() {
    var confirmation =
        mapper.map(requester, requestedOf(new BigDecimal("250"), new BigDecimal("10")), ESTONIAN);

    assertThat(confirmation.mergeVars())
        .containsEntry("amount", "250,00")
        .containsEntry("fundUnits", "10,00000");
  }

  @Test
  void anAmountOrUnitCountWithMoreDecimalsIsRoundedHalfUp() {
    var confirmation =
        mapper.map(
            requester, requestedOf(new BigDecimal("0.125"), new BigDecimal("1.234565")), ENGLISH);

    assertThat(confirmation.mergeVars())
        .containsEntry("amount", "0.13")
        .containsEntry("fundUnits", "1.23457");
  }

  private RedemptionRequestedEvent requestedOf(BigDecimal amount, BigDecimal fundUnits) {
    return new RedemptionRequestedEvent(
        UUID.randomUUID(),
        7L,
        new PartyId(PERSON, requester.getPersonalCode()),
        amount,
        fundUnits,
        "EE471000001020145685",
        Instant.parse("2025-09-29T15:00:00Z"),
        Instant.parse("2025-09-30T13:00:00Z"),
        Instant.parse("2025-10-01T13:00:00Z"));
  }

  private static RedemptionRequestedEvent requested(PartyId party) {
    return new RedemptionRequestedEvent(
        UUID.randomUUID(),
        7L,
        party,
        new BigDecimal("250.00"),
        new BigDecimal("231.48148"),
        "EE471000001020145685",
        Instant.parse("2025-09-29T15:00:00Z"),
        Instant.parse("2025-09-30T13:00:00Z"),
        Instant.parse("2025-10-01T13:00:00Z"));
  }

  private static Map<String, Object> requestFactsAnd(String key, Object value) {
    var mergeVars = new HashMap<>(REQUEST_FACTS);
    mergeVars.put(key, value);
    return mergeVars;
  }

  private static Party namedParty(String code, String name) {
    record NamedParty(String code, String name) implements Party {}
    return new NamedParty(code, name);
  }
}
