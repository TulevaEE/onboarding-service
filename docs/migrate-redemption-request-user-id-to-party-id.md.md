# Migrate RedemptionRequest.userId to PartyId (partyType + partyCode)

## Context

`redemption_request.user_id` (FK to `users`) coupled redemptions to internal user IDs. Replaced with `(party_type, party_code)` to support both persons and legal entities — the same pattern already completed for `saving_fund_payment`. Reuses the existing `PartyId` record (`ee.tuleva.onboarding.party.PartyId`).

---

## Task 1 — DB migration: add party columns + backfill ✅ DONE

`V1_174__add_party_to_redemption_request.sql` — adds `party_type` + `party_code`, backfills from `users`, sets NOT NULL, creates index.

## Task 2 — Add PartyId fields to RedemptionRequest entity ✅ DONE

Added `partyType`, `partyCode` fields + `getPartyId()` convenience getter. No `@Embeddable` — `PartyId` stays a pure domain record.

## Task 3 — Switch service + repository + controller signatures to PartyId ✅ DONE

- Repository: `findByUserId*` → `findByPartyTypeAndPartyCode*`, removed `TEST_backdateVerifiedRequests`
- RedemptionService: all methods take `PartyId` instead of `Long userId`, removed `UserService` dependency
- RedemptionController: uses `PartyId.from(authenticatedPerson.getRole())`
- ApplicationService: uses `PartyId.from(person.getRole())`
- RedemptionRequestedEvent: `long userId` → `PartyId partyId`
- `V1_175__redemption_request_user_id_nullable.sql` — made `user_id` nullable as transition step

## Task 4 — Backend services: resolve party from partyId instead of userId ✅ DONE

- RedemptionBatchJob: uses `request.getPartyId()` directly, removed `UserService` dependency
- RedemptionVerificationService: uses `userService.findByPersonalCode(partyId.code())` instead of `getByIdOrThrow(userId)`
- SebBankStatementProcessor: uses `request.getPartyId()` directly

## Task 5 — Remove userId from RedemptionRequest + drop column ✅ DONE

- Removed `Long userId` field from entity
- `V1_176__redemption_request_drop_user_id.sql` — drops `user_id` column
- Removed `withdrawalOutgoing_throwsWhenUserNotFound` test (no longer applicable — processor reads party from request)

---

## ⚠️ PERSON-only after migration (needs work for LEGAL_ENTITY)

These places work correctly for `PERSON` parties but **will not support `LEGAL_ENTITY`**:

1. ❌ NOT DONE **`RedemptionVerificationService.process()`** — resolves `User` via `userService.findByPersonalCode(partyId.code())` for AML/KYC checks. For LEGAL_ENTITY, `partyId.code()` is a registry code, not a personal code — this lookup would fail. AML approach for companies needs defining.

2. ❌ NOT DONE **`KycSurveyService.getCountry(userId)`** — takes a user ID (Long). Called by `RedemptionVerificationService`. No party-aware variant exists for companies.

3. ❌ NOT DONE **`AmlService.addSanctionAndPepCheckIfMissing(user, country)`** — takes a `User` entity. No Company-aware variant exists.
