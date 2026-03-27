# Migrate SavingFundPayment.userId to Party (partyType + partyCode)

## Context

`saving_fund_payment.user_id` (FK to `users`) couples payments to internal user IDs. We're replacing it with `(party_type, party_code)` to support both persons and legal entities. V1_171 migration is already done — it adds columns, backfills, and installs a trigger that keeps party fields in sync whenever `user_id` is set. This plan covers the remaining Java migration, controller switch, and DB contraction.

**Key design:** introduce a `Party` record (`PartyType type, String code`) so method signatures stay clean — one `Party` parameter replaces `Long userId`. Use `Party.Type` enum (nested inside `Party` record, replaces standalone `PartyType`). Currently all callers construct `new Party(PERSON, personalCode)`. Later, `Party` will be derived from `authenticatedPerson.getRole()`.

**Tests:** run with `SPRING_PROFILES_ACTIVE=ci,test ./gradlew test` (PostgreSQL via Testcontainers).

---

## Task 1a — Create Party record + add to SavingFundPayment ✅ DONE

**Delete** `src/main/java/ee/tuleva/onboarding/company/PartyType.java` and update all imports.

**Create:** `src/main/java/ee/tuleva/onboarding/party/Party.java`
```java
public record Party(Type type, String code) {
  public enum Type { PERSON, LEGAL_ENTITY }

  public Party {
    requireNonNull(type);
    requireNonNull(code);
  }
}
```
`PartyType` becomes `Party.Type` — nested inside `Party`. Update all references: `PartyType.PERSON` → `Party.Type.PERSON` (or static import `Party.Type.*`). Update `CompanyParty.java` and other usages.

**File:** `src/main/java/ee/tuleva/onboarding/savings/fund/SavingFundPayment.java`

- Add `@Nullable Party party` field

Purely additive — no existing code affected. **Green** ✅

---

## Task 1b — Repository: rowMapper + attachParty ✅ DONE

**Files:**
- `src/main/java/ee/tuleva/onboarding/savings/fund/SavingFundPaymentRepository.java`
- `src/main/java/ee/tuleva/onboarding/savings/fund/PaymentVerificationService.java`
- `src/main/java/ee/tuleva/onboarding/payment/savings/SavingsCallbackService.java`

**Changes:**
1. **rowMapper** (line ~228): add `.party(mapParty(rs))` — null-safe helper that reads `party_type` + `party_code`, returns `Party` or null
2. **Rename** `attachUser(UUID, Long)` → `attachParty(UUID, Party)`, SQL: `SET party_type=:party_type, party_code=:party_code`
3. **PaymentVerificationService** (line ~68): `attachParty(payment.getId(), new Party(PERSON, user.get().getPersonalCode()))`
4. **SavingsCallbackService** (line ~65): `attachParty(paymentId, new Party(PERSON, token.getMerchantReference().getPersonalCode()))`

**Tests to update:** `SavingFundPaymentRepositoryTest`, `PaymentVerificationServiceTest`, `SavingsFundReservationJobIntegrationTest`, `SebRedemptionIntegrationTest`, `RedemptionIntegrationTest`

`attachUser` callers all updated together. rowMapper is additive. **Green** ✅

---

## Task 1c — All signatures: userId → Party (repo + services + controllers) ✅ DONE

Repo query methods, UpsertionService, and their controller/ApplicationService callers form a cascading signature chain — changing one forces all to change. Done as a single step.

### Repository (`SavingFundPaymentRepository.java`)

| Before | After |
|---|---|
| `findUserPayments(Long userId)` | `findUserPayments(Party party)` |
| `findUserPaymentsWithStatus(Long userId, Status...)` | `findUserPaymentsWithStatus(Party party, Status...)` |
| `findUserDepositBankAccountIbans(Long userId)` | `findUserDepositBankAccountIbans(Party party)` |
| `findRemitterNameByIban(Long userId, String iban)` | `findRemitterNameByIban(Party party, String iban)` |

SQL predicates: `user_id=:user_id` → `party_type=:party_type AND party_code=:party_code`

### SavingFundPaymentUpsertionService

- `getPendingPaymentsForUser(Long userId)` → `getPendingPaymentsForUser(Party party)`
- `cancelUserPayment(Long userId, UUID)` → `cancelUserPayment(Party party, UUID)`
- Comparison: `!userId.equals(payment.getUserId())` → `!party.equals(payment.getParty())`

### Bridge callers

- **SavingFundPaymentController**: `cancelUserPayment(user.getId(), paymentId)` → `cancelUserPayment(new Party(PERSON, user.getPersonalCode()), paymentId)`; `findUserDepositBankAccountIbans(user.getId())` → `findUserDepositBankAccountIbans(new Party(PERSON, user.getPersonalCode()))`
- **ApplicationService** (line ~148): `getPendingPaymentsForUser(person.getUserId())` → `getPendingPaymentsForUser(new Party(PERSON, person.getPersonalCode()))`

**Tests to update:** `SavingFundPaymentRepositoryTest`, `SavingFundPaymentUpsertionServiceTest`, `SavingFundPaymentControllerTest`, `ApplicationServiceSpec.groovy`

All signatures change together, all callers updated. **Green** ✅

---

## Task 1d — Services: getUserId() → getParty() ✅ DONE

These services read `payment.getParty()` (populated by rowMapper since 1b) to look up the `User`.

**Files:**
- `src/main/java/ee/tuleva/onboarding/savings/fund/PaymentReturningService.java` (injects `UserRepository`)
  - `isUserCancelledPayment` (line 51): `.getUserId() != null` → `.getParty() != null`
  - `reserveUserBalanceForReturn` (line 56): `userRepository.findById(payment.getUserId())` → `userRepository.findByPersonalCode(payment.getParty().code())`
- `src/main/java/ee/tuleva/onboarding/savings/fund/issuing/IssuerService.java` (injects `UserService`)
  - line 30: `userService.getByIdOrThrow(payment.getUserId())` → `userService.findByPersonalCode(payment.getParty().code()).orElseThrow()`
- `src/main/java/ee/tuleva/onboarding/savings/fund/PaymentReservationService.java` (injects `UserService`)
  - line 25: same pattern as IssuerService
- `src/main/java/ee/tuleva/onboarding/banking/processor/DeferredReturnMatcher.java` (injects `UserService`)
  - line 118: `originalPayment.getUserId() != null` → `originalPayment.getParty() != null`
  - line 119: `userService.getByIdOrThrow(originalPayment.getUserId())` → `userService.findByPersonalCode(originalPayment.getParty().code()).orElseThrow()`

Both `userId` and `party` exist on domain during this step. **Green** ✅

---

## Task 1e — PaymentVerificationService: identityCheckFailure ✅ DONE

Independent of 1c/1d — reads `payment.getParty()` (populated since 1b).

**File:** `src/main/java/ee/tuleva/onboarding/savings/fund/PaymentVerificationService.java` (line ~90)

```java
Optional.ofNullable(payment.getParty())
    .map(Party::code)
    .flatMap(userRepository::findByPersonalCode)
    .ifPresent(user -> applicationEventPublisher.publishEvent(
        new SavingsPaymentFailedEvent(this, user, Locale.of("et"))));
```

**Green** ✅

---

## Task 1f — Remove userId from SavingFundPayment ✅ DONE

All callers now use `getParty()`. Must be last in Task 1.

**Changes:**
- `SavingFundPayment.java`: remove `Long userId` field
- `SavingFundPaymentRepository.java` rowMapper: remove `.userId(getLong(rs, "user_id"))`, remove `getLong` helper if unused
- `SavingFundPaymentRepository.java` `attachParty`: remove `user_id=(SELECT id FROM users WHERE personal_code=:party_code)` reverse-lookup (was added in 1b for backward compat with userId-based queries, no longer needed since 1c switched all queries to party columns)

**Green** ✅

---

## Task 1g — ⚠️ TODO: replace `new Party(PERSON, getPersonalCode())` with role-based Party construction

Review all places where `new Party()` is hardcoded with `PERSON` and `getPersonalCode()` when the caller has access to `AuthenticatedPerson` (or its role). These should derive Party from role info instead, so legal entities work correctly.

**Places that need changing (controller/application layer — have AuthenticatedPerson):**
- ✅ DONE `SavingFundPaymentController.cancelSavingsFundPayment` — uses `PartyId.from(authenticatedPerson.getRole())`
- ✅ DONE `SavingFundPaymentController.getBankAccounts` — same
- ✅ DONE `ApplicationService.getSavingsFundApplications` — uses `PartyId.from(person.getRole())`

**Places that hardcode PERSON (backend services — need LEGAL_ENTITY support in next subproject):**
- ❌ NOT DONE `PaymentVerificationService.process` — still hardcodes `new PartyId(PERSON, code)` from bank statement identity check
- ✅ DONE `SavingsCallbackService.processToken` — derives type from `ref.getRecipientPartyType()` with fallback to PERSON
- ✅ DONE `RedemptionService.createRedemptionRequest` — takes `PartyId` directly, no hardcoded PERSON
- ✅ DONE `RedemptionBatchJob` — uses `request.getPartyId()`, no hardcoded PERSON

---

## Task 1h — ⚠️ TODO: `findByPersonalCode(party.code())` assumes PERSON

Several services look up a `User` via `findByPersonalCode(payment.getParty().code())`. This works for `PERSON` parties (where `party.code()` is a personal code) but will **not work for `LEGAL_ENTITY`** parties (where `party.code()` is a registry code, not a personal code). No code changes now — this is a documented limitation to address when legal entity payment support is added.

**Affected places:**
- ✅ DONE `IssuerService.processPayment` — no longer looks up User, uses `payment.getPartyId()` directly with ledger
- ✅ DONE `PaymentReservationService.process` — same, no longer uses `findByPersonalCode`
- ✅ DONE `PaymentReturningService.reserveUserBalanceForReturn` — same, no longer uses `findByPersonalCode`
- ✅ DONE `DeferredReturnMatcher.completePaymentReturn` — same, no longer uses `findByPersonalCode`
- ✅ DONE `PaymentVerificationService.identityCheckFailure` — still uses `findByPersonalCode` but always in PERSON context (correct)
- ❌ NOT DONE `RedemptionVerificationService.process()` — new, uses `findByPersonalCode(partyId.code())` without type check (PERSON-only)

---

## ⚠️ TODO: Review User/Person usage for email alerts in payment flows

Several services look up a `User` to publish events that trigger email notifications (e.g., `SavingsPaymentFailedEvent`, `SavingsPaymentCancelledEvent`, `SavingsPaymentCreatedEvent`, `UnattributedPaymentEvent`). These events carry a `User` object used for sending email alerts. When legal entity support is added, the notification recipient may not be a `User` — it could be a company representative or a different contact. Review and update:

- ❌ NOT DONE `PaymentVerificationService.process` — publishes `SavingsPaymentFailedEvent(user)` on identity check failure
- ❌ NOT DONE `PaymentVerificationService.identityCheckFailure` — publishes `SavingsPaymentFailedEvent(user)` if payment has party
- ❌ NOT DONE `SavingsCallbackService.processToken` — publishes `SavingsPaymentCreatedEvent(user)`
- ❌ NOT DONE `SavingFundPaymentController.cancelSavingsFundPayment` — publishes `SavingsPaymentCancelledEvent(user)`

---

## Task 2 — Controllers switch to Role ✅ DONE

`AuthenticatedPerson` already has `getRoleCode()` and `getRoleType()` convenience methods (added today in cc235b11). The controller already uses `getRoleCode()` for the onboarding status endpoint.

**File:** `src/main/java/ee/tuleva/onboarding/savings/fund/SavingFundPaymentController.java`

- `cancelSavingsFundPayment`: resolve user via `userService.findByPersonalCode(authenticatedPerson.getRoleCode()).orElseThrow()` instead of `getByIdOrThrow(authenticatedPerson.getUserId())`
- `getBankAccounts`: `findDepositBankAccountIbans(Party.from(authenticatedPerson.getRole()))` — no User lookup needed

**File:** `src/main/java/ee/tuleva/onboarding/mandate/application/ApplicationService.java`

- `getPendingPayments(Party.from(person.getRole()))`

Added `Party.from(Role)` factory: `new Party(Type.valueOf(role.type().name()), role.code())`. Also added role claim to `JwtTokenGenerator` for integration tests.

**Tests:** Update `SavingFundPaymentControllerTest` auth setup to use `Role`; update `ApplicationServiceSpec.groovy` stubs.

Note: `cancelSavingsFundPayment` still needs a `User` for the `SavingsPaymentCancelledEvent`. Use `userService.findByPersonalCode(authenticatedPerson.getRoleCode()).orElseThrow()` instead of `getByIdOrThrow(authenticatedPerson.getUserId())`.

---

## Task 3 — DB contract ✅ DONE

**Create** `src/main/resources/db/migration/V1_172__saving_fund_payment_drop_user_id.sql`:
```sql
ALTER TABLE saving_fund_payment DROP COLUMN user_id;
```

No trigger to drop — V1_171 only added columns, backfill, and index.

---

## Verification

After each task:
1. `SPRING_PROFILES_ACTIVE=ci,test ./gradlew test` — all tests green on PostgreSQL
2. `./gradlew spotlessCheck` — code style
