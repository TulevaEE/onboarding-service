package ee.tuleva.onboarding.savings.fund.redemption.email;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_REDEMPTION_CHILD;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_REDEMPTION_COMPANY;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_REDEMPTION_PERSON;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.auth.principal.Names;
import ee.tuleva.onboarding.auth.role.CompanyRoles;
import ee.tuleva.onboarding.auth.role.CompanyRoles.CompanyRole;
import ee.tuleva.onboarding.notification.email.EmailType;
import ee.tuleva.onboarding.party.Party;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyResolver;
import ee.tuleva.onboarding.savings.fund.notification.RedemptionRequestedEvent;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class RedemptionConfirmationMapper {

  private static final int AMOUNT_DECIMALS = 2;
  private static final int FUND_UNIT_DECIMALS = 5;
  private static final DateTimeFormatter ESTONIAN_DEADLINE_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Tallinn"));

  private final PartyResolver partyResolver;
  private final CompanyRoles companyRoles;

  @Transactional(readOnly = true)
  RedemptionConfirmation map(User requester, RedemptionRequestedEvent event, Locale locale) {
    PartyId party = event.party();
    if (party.type() == LEGAL_ENTITY) {
      return confirmation(
          SAVINGS_FUND_REDEMPTION_COMPANY, requester, event, locale, company(party));
    }
    if (!party.code().equals(requester.getPersonalCode())) {
      return confirmation(SAVINGS_FUND_REDEMPTION_CHILD, requester, event, locale, child(party));
    }
    return confirmation(SAVINGS_FUND_REDEMPTION_PERSON, requester, event, locale, Map.of());
  }

  private Map<String, Object> company(PartyId party) {
    return companyRoles
        .findCompany(party.code())
        .map(CompanyRole::name)
        .map(RedemptionConfirmationMapper::recipientNamed)
        .orElseGet(Map::of);
  }

  private Map<String, Object> child(PartyId party) {
    return partyResolver
        .resolve(party)
        .map(Party::name)
        .map(Names::formatted)
        .map(RedemptionConfirmationMapper::recipientNamed)
        .orElseGet(Map::of);
  }

  private static Map<String, Object> recipientNamed(String name) {
    return Map.of("recipientName", name);
  }

  private static RedemptionConfirmation confirmation(
      EmailType emailType,
      User requester,
      RedemptionRequestedEvent event,
      Locale locale,
      Map<String, Object> recipient) {
    var mergeVars = new HashMap<String, Object>(recipient);
    mergeVars.put("fname", Names.formatted(requester.getFirstName()));
    mergeVars.put("amount", inLocale(event.requestedAmount(), AMOUNT_DECIMALS, locale));
    mergeVars.put("fundUnits", inLocale(event.fundUnits(), FUND_UNIT_DECIMALS, locale));
    mergeVars.put("iban", MaskedIban.of(event.customerIban()));
    mergeVars.put(
        "cancellationDeadline", ESTONIAN_DEADLINE_FORMAT.format(event.cancellationDeadline()));
    mergeVars.put("payoutDeadline", ESTONIAN_DEADLINE_FORMAT.format(event.payoutDeadline()));
    return new RedemptionConfirmation(emailType, mergeVars);
  }

  private static String inLocale(BigDecimal number, int decimals, Locale locale) {
    NumberFormat format = NumberFormat.getNumberInstance(locale);
    format.setMinimumFractionDigits(decimals);
    format.setMaximumFractionDigits(decimals);
    format.setRoundingMode(HALF_UP);
    return format.format(number);
  }
}
