const {
  SOURCES,
  getSourceForSender,
  getFileConfigAndDate,
  isMessageWithinWindow,
  shouldProcessMessage,
  parseUploadCountFromLabelNames,
  formatUploadLabelName,
  partitionFilenamesByMatch,
  shouldAlertOnUnmatched,
  shouldNotifyAboutSkipped,
  dedupKeyFor,
  formatUnmatchedAttachmentAlert,
  isIgnoredFilename,
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

describe("shouldProcessMessage", () => {
  test("processes the first matching message in a fresh thread", () => {
    expect(shouldProcessMessage(1, 0)).toBe(true);
  });
  test("skips an already-processed matching message", () => {
    expect(shouldProcessMessage(1, 1)).toBe(false);
  });
  test("processes a newly-arrived 2nd matching message in a thread previously processed once", () => {
    expect(shouldProcessMessage(2, 1)).toBe(true);
  });
  test("processes a 3rd matching message even if the first two have been processed in earlier runs", () => {
    expect(shouldProcessMessage(3, 1)).toBe(true);
  });
});

describe("parseUploadCountFromLabelNames", () => {
  test("returns 0 when no upload label is present", () => {
    expect(parseUploadCountFromLabelNames(["Inbox", "Some-Other-Label"])).toBe(0);
  });
  test("returns the integer count from a single upload label", () => {
    expect(parseUploadCountFromLabelNames(["S3-Uploaded-3x"])).toBe(3);
  });
  test("returns the count when the upload label is mixed with unrelated labels", () => {
    expect(parseUploadCountFromLabelNames(["Inbox", "S3-Uploaded-7x", "Important"])).toBe(7);
  });
  test("returns the FIRST upload-style label encountered (defensive — should normally only be one)", () => {
    expect(parseUploadCountFromLabelNames(["S3-Uploaded-2x", "S3-Uploaded-9x"])).toBe(2);
  });
  test("returns 0 when the label name is malformed", () => {
    expect(parseUploadCountFromLabelNames(["S3-Uploaded-abcx"])).toBe(0);
  });
});

describe("formatUploadLabelName", () => {
  test("formats the label name from a count", () => {
    expect(formatUploadLabelName(1)).toBe("S3-Uploaded-1x");
    expect(formatUploadLabelName(9)).toBe("S3-Uploaded-9x");
  });
  test("round-trips with parseUploadCountFromLabelNames", () => {
    for (var n = 0; n <= 10; n++) {
      expect(parseUploadCountFromLabelNames([formatUploadLabelName(n)])).toBe(n);
    }
  });
});

describe("partitionFilenamesByMatch", () => {
  test("splits filenames into matched and unmatched lists", () => {
    const result = partitionFilenamesByMatch(SOURCES.SEB, [
      "TULEVA_pos_raport_20260409.csv",
      "junk.csv",
      "TULEVA_ootel_tehingud_20260409_uuendatud.csv",
    ]);
    expect(result.matched).toEqual([
      "TULEVA_pos_raport_20260409.csv",
      "TULEVA_ootel_tehingud_20260409_uuendatud.csv",
    ]);
    expect(result.unmatched).toEqual(["junk.csv"]);
  });
  test("returns empty unmatched when everything matches", () => {
    const result = partitionFilenamesByMatch(SOURCES.SEB, [
      "TULEVA_pos_raport_20260409.csv",
    ]);
    expect(result.unmatched).toEqual([]);
  });
  test("returns empty matched when nothing matches", () => {
    const result = partitionFilenamesByMatch(SOURCES.SEB, ["random.txt"]);
    expect(result.matched).toEqual([]);
    expect(result.unmatched).toEqual(["random.txt"]);
  });
  test("drops ignored filenames from both matched and unmatched (silences NAV-calc noise)", () => {
    const result = partitionFilenamesByMatch(SOURCES.SEB, [
      "TULEVA_pos_raport_20260415.csv",
      "TKF100 NAV arvutamine 15042026.csv",
      "TUV100 NAV arvutamine 15042026.csv",
    ]);
    expect(result.matched).toEqual(["TULEVA_pos_raport_20260415.csv"]);
    expect(result.unmatched).toEqual([]);
  });
  test("reported Slack false-positive no longer triggers an alert", () => {
    const result = partitionFilenamesByMatch(SOURCES.SEB, [
      "TKF100 NAV arvutamine 15042026.csv",
      "TUV100 NAV arvutamine 15042026.csv",
    ]);
    expect(shouldAlertOnUnmatched(result.matched.length, result.unmatched.length)).toBe(false);
  });
});

describe("isIgnoredFilename", () => {
  test.each([
    "TKF100 NAV arvutamine 15042026.csv",
    "TUV100 NAV arvutamine 15042026.csv",
    "TKF100 NAV arvutamine 01012027.csv",
  ])("ignores internal NAV-calc sheet %s", (filename) => {
    expect(isIgnoredFilename(SOURCES.SEB, filename)).toBe(true);
  });

  test.each([
    "TULEVA_pos_raport_20260409.csv",
    "NAV arvutamine 15042026.csv",
    "TKF100_NAV_arvutamine_15042026.csv",
    "random.csv",
  ])("does not ignore %s", (filename) => {
    expect(isIgnoredFilename(SOURCES.SEB, filename)).toBe(false);
  });

  test("returns false for a source with no ignorePatterns (Swedbank)", () => {
    expect(isIgnoredFilename(SOURCES.SWEDBANK, "anything.csv")).toBe(false);
  });
});

describe("shouldAlertOnUnmatched", () => {
  test("alerts when a known sender has any unmatched attachment", () => {
    expect(shouldAlertOnUnmatched(0, 1)).toBe(true);
  });
  test("alerts even when SOME attachments matched (mixed case)", () => {
    expect(shouldAlertOnUnmatched(2, 1)).toBe(true);
  });
  test("does not alert when everything matched", () => {
    expect(shouldAlertOnUnmatched(2, 0)).toBe(false);
  });
  test("does not alert when the message has no attachments at all", () => {
    expect(shouldAlertOnUnmatched(0, 0)).toBe(false);
  });
});

describe("dedupKeyFor", () => {
  test("is sort-invariant on the filename list", () => {
    expect(dedupKeyFor("msgA", ["b.csv", "a.csv"])).toBe(dedupKeyFor("msgA", ["a.csv", "b.csv"]));
  });
  test("is message-scoped", () => {
    expect(dedupKeyFor("msgA", ["a.csv"])).not.toBe(dedupKeyFor("msgB", ["a.csv"]));
  });
  test("is filename-scoped", () => {
    expect(dedupKeyFor("msgA", ["a.csv"])).not.toBe(dedupKeyFor("msgA", ["b.csv"]));
  });
});

describe("shouldNotifyAboutSkipped", () => {
  test("notifies when nothing has been recorded for this message", () => {
    expect(shouldNotifyAboutSkipped("msgA", ["foo.csv"], {})).toBe(true);
  });
  test("does NOT notify when the same key is already present", () => {
    const key = dedupKeyFor("msgA", ["foo.csv"]);
    expect(shouldNotifyAboutSkipped("msgA", ["foo.csv"], { [key]: "anytime" })).toBe(false);
  });
  test("notifies when a NEW filename set arrives in the same message", () => {
    const oldKey = dedupKeyFor("msgA", ["foo.csv"]);
    expect(shouldNotifyAboutSkipped("msgA", ["foo.csv", "bar.csv"], { [oldKey]: "anytime" })).toBe(true);
  });
  test("notifies for a different message even if same filename", () => {
    const otherKey = dedupKeyFor("msgA", ["foo.csv"]);
    expect(shouldNotifyAboutSkipped("msgB", ["foo.csv"], { [otherKey]: "anytime" })).toBe(true);
  });
});

describe("formatUnmatchedAttachmentAlert", () => {
  test("pure-skip wording when zero attachments matched", () => {
    const msg = formatUnmatchedAttachmentAlert("trustee@example", ["weird.csv"], 0, "k");
    expect(msg).toContain("skipped — no attachments matched");
    expect(msg).toContain("weird.csv");
    expect(msg).toContain("trustee@example");
  });
  test("mixed wording when SOME attachments matched", () => {
    const msg = formatUnmatchedAttachmentAlert("trustee@example", ["weird.csv"], 2, "k");
    expect(msg).toContain("UNMATCHED attachments (alongside 2 matched)");
    expect(msg).not.toContain("skipped — no attachments matched");
  });
  test("includes the dedup key so operators know how to re-trigger", () => {
    const msg = formatUnmatchedAttachmentAlert("trustee@example", ["weird.csv"], 0, "skipped_notified:msgA:abcd");
    expect(msg).toContain("skipped_notified:msgA:abcd");
  });
});
