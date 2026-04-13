const {
  SOURCES,
  getSourceForSender,
  getFileConfig,
  extractDateFromFilename,
} = require("../src/Code");

// Characterization tests — pin the CURRENT (broken) behavior of the script
// before we change anything. The suffixed-filename cases assert `toBeNull()`
// here because the existing regex doesn't match them. The follow-up commit
// will FLIP these assertions to `not.toBeNull()` as the TDD red→green step.

describe("getSourceForSender", () => {
  // Pulls allowed senders from SOURCES rather than hardcoding addresses in
  // test prose — same coverage, no literal addresses in this file.
  test("matches each configured SEB sender", () => {
    SOURCES.SEB.senders.forEach((s) => {
      expect(getSourceForSender("Some Person <" + s + ">")).toBe(SOURCES.SEB);
    });
  });
  test("matches each configured Swedbank sender", () => {
    SOURCES.SWEDBANK.senders.forEach((s) => {
      expect(getSourceForSender("Some Person <" + s + ">")).toBe(SOURCES.SWEDBANK);
    });
  });
  test("rejects an arbitrary @tuleva.ee address that is not on the allowlist", () => {
    expect(getSourceForSender("Random Internal <random.person@tuleva.ee>")).toBeNull();
  });
  test("rejects unknown sender", () => {
    expect(getSourceForSender("attacker@example.com")).toBeNull();
  });
});

describe("getFileConfig — SEB filenames (current behavior, will be expanded in the follow-up commit)", () => {
  test.each([
    ["TULEVA_pos_raport_20260409.csv", "_positions.csv"],
    ["TULEVA_ootel_tehingud_20260409.csv", "_pending_transactions.csv"],
  ])("matches routine filename %s → suffix %s", (filename, expectedSuffix) => {
    const cfg = getFileConfig(SOURCES.SEB, filename);
    expect(cfg).not.toBeNull();
    expect(cfg.s3Suffix).toBe(expectedSuffix);
  });

  // The bug: existing regex /^TULEVA_pos_raport_\d+\.csv$/ rejects suffixed filenames.
  // These tests pin the BROKEN behavior. The follow-up commit flips them to `not.toBeNull()`.
  test.each([
    "TULEVA_pos_raport_20260409_uuendatud.csv",
    "TULEVA_ootel_tehingud_20260409_uuendatud.csv",
    "TULEVA_pos_raport_20260408_uus.csv",
    "TULEVA_ootel_tehingud_20260408_uus.csv",
  ])("BUG: rejects suffixed filename %s — pinned, will flip in follow-up commit", (filename) => {
    expect(getFileConfig(SOURCES.SEB, filename)).toBeNull();
  });

  test.each([
    "random.csv",
    "TULEVA_pos_raport.csv",                  // no date at all
    "TULEVA_pos_raport_20260409.txt",         // wrong extension
    "NOT_TULEVA_pos_raport_20260409.csv",     // wrong prefix
    "TULEVA_portfolio_20260409.csv",          // Swedbank pattern, not SEB
  ])("rejects %s", (filename) => {
    expect(getFileConfig(SOURCES.SEB, filename)).toBeNull();
  });
});

describe("getFileConfig — Swedbank filenames", () => {
  test("matches TULEVA_PORTFOLIO_20260409.csv", () => {
    const cfg = getFileConfig(SOURCES.SWEDBANK, "TULEVA_PORTFOLIO_20260409.csv");
    expect(cfg).not.toBeNull();
    expect(cfg.s3Suffix).toBe(".csv");
  });
  test("rejects SEB filename", () => {
    expect(getFileConfig(SOURCES.SWEDBANK, "TULEVA_pos_raport_20260409.csv")).toBeNull();
  });
});

describe("extractDateFromFilename", () => {
  const datePattern = /(\d{4})(\d{2})(\d{2})/;
  test("extracts date from routine filename", () => {
    expect(extractDateFromFilename("TULEVA_pos_raport_20260409.csv", datePattern)).toBe("2026-04-09");
  });
  test("extracts date even from suffixed filename (regex matches the digits, but getFileConfig rejects it)", () => {
    expect(extractDateFromFilename("TULEVA_pos_raport_20260409_uuendatud.csv", datePattern)).toBe("2026-04-09");
  });
  test("returns null when no date present", () => {
    expect(extractDateFromFilename("random.csv", datePattern)).toBeNull();
  });
});
