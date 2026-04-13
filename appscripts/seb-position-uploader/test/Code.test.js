const {
  SOURCES,
  getSourceForSender,
  getFileConfigAndDate,
  isMessageWithinWindow,
} = require("../src/Code");

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

describe("getFileConfigAndDate — SEB filenames (positive)", () => {
  test.each([
    ["TULEVA_pos_raport_20260409.csv", "_positions.csv", "2026-04-09"],
    ["TULEVA_pos_raport_20260409_uuendatud.csv", "_positions.csv", "2026-04-09"],
    ["TULEVA_pos_raport_20260408_uus.csv", "_positions.csv", "2026-04-08"],
    ["TULEVA_ootel_tehingud_20260409.csv", "_pending_transactions.csv", "2026-04-09"],
    ["TULEVA_ootel_tehingud_20260409_uuendatud.csv", "_pending_transactions.csv", "2026-04-09"],
    ["TULEVA_ootel_tehingud_20260408_uus.csv", "_pending_transactions.csv", "2026-04-08"],
    ["TULEVA_pos_raport_20260409_v2.csv", "_positions.csv", "2026-04-09"],
    ["TULEVA_pos_raport_20260409 (1).csv", "_positions.csv", "2026-04-09"],
    ["TULEVA_pos_raport_20260409-korrigeeritud.csv", "_positions.csv", "2026-04-09"],
  ])("matches %s → suffix %s, date %s", (filename, expectedSuffix, expectedDate) => {
    const result = getFileConfigAndDate(SOURCES.SEB, filename);
    expect(result).not.toBeNull();
    expect(result.fileConfig.s3Suffix).toBe(expectedSuffix);
    expect(result.reportDate).toBe(expectedDate);
  });
});

describe("getFileConfigAndDate — SEB filenames (false-positive defenses)", () => {
  test.each([
    "random.csv",
    "TULEVA_pos_raport.csv",                  // no date at all
    "TULEVA_pos_raport_backup.csv",           // word instead of date
    "TULEVA_pos_raport_2026.csv",             // date too short
    "TULEVA_pos_raport_20260409.txt",         // wrong extension
    "NOT_TULEVA_pos_raport_20260409.csv",     // wrong prefix
    "TULEVA_portfolio_20260409.csv",          // Swedbank pattern, not SEB
  ])("rejects %s", (filename) => {
    expect(getFileConfigAndDate(SOURCES.SEB, filename)).toBeNull();
  });
});

describe("getFileConfigAndDate — Swedbank filenames", () => {
  test("matches TULEVA_PORTFOLIO_20260409.csv", () => {
    const result = getFileConfigAndDate(SOURCES.SWEDBANK, "TULEVA_PORTFOLIO_20260409.csv");
    expect(result).not.toBeNull();
    expect(result.fileConfig.s3Suffix).toBe(".csv");
    expect(result.reportDate).toBe("2026-04-09");
  });
  test("rejects SEB filename", () => {
    expect(getFileConfigAndDate(SOURCES.SWEDBANK, "TULEVA_pos_raport_20260409.csv")).toBeNull();
  });
});

describe("isMessageWithinWindow", () => {
  test("accepts a message strictly newer than the cutoff", () => {
    expect(isMessageWithinWindow(new Date("2026-04-10T00:00:00Z"), new Date("2026-04-09T00:00:00Z"))).toBe(true);
  });
  test("rejects a message strictly older than the cutoff", () => {
    expect(isMessageWithinWindow(new Date("2026-04-08T00:00:00Z"), new Date("2026-04-09T00:00:00Z"))).toBe(false);
  });
  test("accepts a message exactly at the cutoff (boundary inclusive)", () => {
    expect(isMessageWithinWindow(new Date("2026-04-09T00:00:00Z"), new Date("2026-04-09T00:00:00Z"))).toBe(true);
  });
  test("accepts everything when no cutoff is configured", () => {
    expect(isMessageWithinWindow(new Date("2026-04-08T00:00:00Z"), null)).toBe(true);
  });
});
