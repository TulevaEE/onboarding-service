const {
  SOURCES,
  notifySlack,
  processNewEmails,
  processHistoricalEmails,
  saveReportsToDrive,
  saveThreadToDrive,
  saveReportToDrive,
  isOlderThanCurrentSource,
  forgetOldReportSources,
  driveReportFamily,
  isSameDriveReport,
  isVersionedDriveName,
  nextDriveVersion,
  versionedDriveName,
  usCopyName,
  isUsCopyName,
  convertSebCsvToUs,
  notifyDriveProblemOncePerDay,
} = require("../src/Code");

const POSITIONS = SOURCES.SEB.files[0];
const PENDING = SOURCES.SEB.files[1];

function fakeDrive() {
  const events = [];
  const failures = {};
  function fail(action, name) {
    const key = action + " " + name;
    if (failures[key]) throw new Error("Drive failed: " + key);
  }
  function file(name, content) {
    let currentName = name;
    let currentContent = content;
    const f = {
      getName: () => currentName,
      content: () => currentContent,
      getBlob: () => ({ getDataAsString: () => currentContent }),
      setName(newName) {
        fail("setName", currentName);
        events.push("rename " + currentName + " -> " + newName);
        currentName = newName;
        return f;
      },
      setContent(newContent) {
        fail("setContent", currentName);
        events.push("setContent " + currentName);
        currentContent = newContent;
        return f;
      },
      makeCopy(copyName, destination) {
        fail("makeCopy", currentName);
        return destination.createFile(copyName, currentContent);
      },
    };
    return f;
  }
  const files = [];
  const folder = {
    getFiles() {
      const snapshot = files.slice();
      let i = 0;
      return { hasNext: () => i < snapshot.length, next: () => snapshot[i++] };
    },
    createFile(blobOrName, content) {
      const isBlob = typeof blobOrName !== "string";
      const name = isBlob ? blobOrName.getName() : blobOrName;
      fail("createFile", name);
      const f = file(name, isBlob ? blobOrName.getDataAsString() : content);
      files.push(f);
      events.push("create " + name);
      return f;
    },
  };
  return {
    folder,
    events,
    failures,
    seed: (names) => names.forEach((n) => files.push(file(n, "old " + n))),
    seedWithContent: (contents) => Object.keys(contents).forEach((n) => files.push(file(n, contents[n]))),
    names: () => files.map((f) => f.getName()),
    byName: (name) => files.filter((f) => f.getName() === name),
    currentUsCopiesFor: (keyword, date) =>
      files.filter((f) => {
        const n = f.getName().toLowerCase();
        return n.includes(keyword) && n.match(/(\d{8})/)[1] === date && n.endsWith("_us.csv");
      }),
  };
}

function fakeAttachment(name, content) {
  return {
    getName: () => name,
    getDataAsString: () => content,
    copyBlob: () => ({ getName: () => name, getDataAsString: () => content }),
  };
}

function installAppsScriptFakes({ properties = {}, folders = {}, threads = [], lockAvailable = true, s3Status = 200 } = {}) {
  const slack = [];
  const calls = { search: 0, lock: 0, folders: [] };
  const props = Object.assign({}, properties);
  global.Logger = { log: () => {} };
  global.MimeType = { CSV: "text/csv" };
  global.Utilities = {
    formatDate: () => "2026-11-02",
    newBlob: (content) => ({ getBytes: () => Array.from(String(content), (ch) => ch.charCodeAt(0) & 0xff) }),
    computeDigest: () => [1, 2, 3],
    computeHmacSignature: () => [4, 5, 6],
    DigestAlgorithm: { SHA_256: "SHA_256" },
    MacAlgorithm: { HMAC_SHA_256: "HMAC_SHA_256" },
  };
  global.PropertiesService = {
    getScriptProperties: () => ({
      getProperty: (key) => (key in props ? props[key] : null),
      setProperty: (key, value) => {
        props[key] = value;
      },
      getProperties: () => Object.assign({}, props),
      deleteProperty: (key) => {
        delete props[key];
      },
    }),
  };
  global.LockService = {
    getScriptLock: () => ({
      tryLock: () => {
        calls.lock++;
        return lockAvailable;
      },
      releaseLock: () => {},
    }),
  };
  global.DriveApp = {
    getFolderById(id) {
      calls.folders.push(id);
      if (!folders[id]) throw new Error("No item with the given ID could be found");
      return folders[id];
    },
  };
  global.GmailApp = {
    search: () => {
      calls.search++;
      return threads;
    },
    getUserLabelByName: (name) => ({ getName: () => name }),
  };
  const s3 = { status: s3Status };
  const s3Keys = [];
  const s3Bodies = {};
  const slackResponses = [];
  global.UrlFetchApp = {
    fetch(url, options) {
      if (url.includes(".amazonaws.com/")) {
        const key = url.split(".amazonaws.com/")[1];
        if (s3.status !== 200) return { getResponseCode: () => s3.status, getContentText: () => "AccessDenied" };
        s3Keys.push(key);
        s3Bodies[key] = String.fromCharCode(...options.payload);
        return { getResponseCode: () => 200, getContentText: () => "ok" };
      }
      const response = slackResponses.shift() || 200;
      if (response instanceof Error) throw response;
      if (response === 200) slack.push(JSON.parse(options.payload).text);
      return { getResponseCode: () => response, getContentText: () => "slack says " + response };
    },
  };
  return { slack, calls, props, s3, s3Keys, s3Bodies, slackResponses };
}

const noRecipients = { getTo: () => "", getCc: () => "", getBcc: () => "" };

const MORNING = new Date("2026-11-02T08:45:00Z");
const SOURCE = { date: MORNING.toISOString(), id: "18f0a00000000001" };
const LATER = { date: "2026-11-02T10:15:00.000Z", id: "18f0a00000000002" };

function sebMessage(id, attachments, date = MORNING) {
  return {
    ...noRecipients,
    getId: () => id,
    getDate: () => date,
    getFrom: () => "SEB <" + SOURCES.SEB.senders[0] + ">",
    getAttachments: () => attachments,
  };
}

function swedbankMessage(id) {
  return {
    ...noRecipients,
    getId: () => id,
    getDate: () => MORNING,
    getFrom: () => "Swedbank <" + SOURCES.SWEDBANK.senders[0] + ">",
    getAttachments: () => [fakeAttachment("TULEVA_PORTFOLIO_20261020.csv", "x")],
  };
}

function fakeThread(messages, labelNames) {
  const labels = labelNames.slice();
  return {
    labels,
    getMessages: () => messages,
    getFirstMessageSubject: () => "SEB reports",
    getLabels: () => labels.map((name) => ({ getName: () => name })),
    addLabel: (label) => labels.push(label.getName()),
    removeLabel: (label) => labels.splice(labels.indexOf(label.getName()), 1),
  };
}

beforeEach(() => {
  jest.useFakeTimers({ now: new Date("2026-11-02T11:00:00Z") });
});

afterEach(() => {
  jest.useRealTimers();
  ["Logger", "MimeType", "Utilities", "PropertiesService", "LockService", "DriveApp", "GmailApp", "UrlFetchApp"].forEach(
    (name) => delete global[name],
  );
});

describe("which reports are copied to Drive", () => {
  test("both SEB report types carry the keyword the Sheets scripts look for", () => {
    expect(SOURCES.SEB.files.map((f) => f.driveKeyword)).toEqual(["tuleva_pos", "tuleva_ootel"]);
  });
  test("Swedbank and Flow Traders files are not copied", () => {
    Object.keys(SOURCES)
      .filter((key) => key !== "SEB")
      .forEach((key) => SOURCES[key].files.forEach((f) => expect(f.driveKeyword).toBeUndefined()));
  });
});

describe("Drive file names", () => {
  test("the US copy turns decimal commas into points and semicolon separators into commas", () => {
    expect(convertSebCsvToUs("Fund;Quantity;Price\nTUK75;1234,5;10,25\n")).toBe("Fund,Quantity,Price\nTUK75,1234.5,10.25\n");
  });
  test("the US copy ends in _US.csv, whatever the extension's letter case", () => {
    expect(usCopyName("TULEVA_pos_raport_20261020.csv")).toBe("TULEVA_pos_raport_20261020_US.csv");
    expect(usCopyName("TULEVA_pos_raport_20261020_korrigeeritud.CSV")).toBe("TULEVA_pos_raport_20261020_korrigeeritud_US.csv");
    expect(isUsCopyName("TULEVA_pos_raport_20261020_korrigeeritud_US.CSV")).toBe(true);
    expect(isUsCopyName("TULEVA_pos_raport_20261020_US_v1.csv")).toBe(false);
  });
  test("an older version gets _vN before the extension", () => {
    expect(versionedDriveName("TULEVA_pos_raport_20261020_US.csv", 2)).toBe("TULEVA_pos_raport_20261020_US_v2.csv");
    expect(isVersionedDriveName("TULEVA_pos_raport_20261020_US_v2.csv")).toBe(true);
    expect(isVersionedDriveName("TULEVA_pos_raport_20261020_US.csv")).toBe(false);
  });
  test("the next version follows the highest one", () => {
    expect(nextDriveVersion(["TULEVA_pos_raport_20261020.csv"])).toBe(1);
    expect(nextDriveVersion(["TULEVA_pos_raport_20261020_v1.csv", "TULEVA_pos_raport_20261020_US_v2.csv"])).toBe(3);
  });
});

describe("isSameDriveReport", () => {
  test("matches the original, the US copy, a corrected file and old versions of that type and date", () => {
    [
      "TULEVA_pos_raport_20261020.csv",
      "TULEVA_pos_raport_20261020_US.csv",
      "TULEVA_pos_raport_20261020_korrigeeritud_US.csv",
      "TULEVA_pos_raport_20261020_US_v3.csv",
    ].forEach((name) => expect(isSameDriveReport(name, POSITIONS, "20261020")).toBe(true));
  });
  test("reads the date from the report name, not from digits later in it", () => {
    expect(isSameDriveReport("TULEVA_pos_raport_20261021_parandus_20261020_US.csv", POSITIONS, "20261020")).toBe(false);
  });
  test("does not match another report type or another file that only mentions the type", () => {
    expect(isSameDriveReport("TULEVA_ootel_tehingud_20261020_US.csv", POSITIONS, "20261020")).toBe(false);
    expect(isSameDriveReport("koopia_TULEVA_pos_raport_20261020_US.csv", POSITIONS, "20261020")).toBe(false);
  });
});

describe("saveReportToDrive", () => {
  const seeded = [
    "TULEVA_pos_raport_20261020.csv",
    "TULEVA_pos_raport_20261020_US.csv",
    "TULEVA_pos_raport_20261019_US.csv",
    "TULEVA_pos_raport_20261021_parandus_20261020_US.csv",
    "TULEVA_ootel_tehingud_20261020_US.csv",
  ];

  test("the first report of a date gets an unchanged original and a US copy", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();

    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1,5\n"), POSITIONS, "20261020", SOURCE);

    expect(drive.names()).toEqual(["TULEVA_pos_raport_20261020_US.csv", "TULEVA_pos_raport_20261020.csv"]);
    expect(drive.byName("TULEVA_pos_raport_20261020_US.csv")[0].content()).toBe("A,1.5\n");
    expect(drive.byName("TULEVA_pos_raport_20261020.csv")[0].content()).toBe("A;1,5\n");
  });

  test("a corrected report updates the current US copy in place and keeps the old content as _v1", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    drive.seed(seeded);
    const currentUs = drive.byName("TULEVA_pos_raport_20261020_US.csv")[0];

    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020_korrigeeritud.csv", "B;2,5\n"), POSITIONS, "20261020", SOURCE);

    expect(drive.names()).toEqual([
      "TULEVA_pos_raport_20261020_v1.csv",
      "TULEVA_pos_raport_20261020_korrigeeritud_US.csv",
      "TULEVA_pos_raport_20261019_US.csv",
      "TULEVA_pos_raport_20261021_parandus_20261020_US.csv",
      "TULEVA_ootel_tehingud_20261020_US.csv",
      "TULEVA_pos_raport_20261020_US_v1.csv",
      "TULEVA_pos_raport_20261020_korrigeeritud.csv",
    ]);
    expect(currentUs.content()).toBe("B,2.5\n");
    expect(drive.byName("TULEVA_pos_raport_20261020_US_v1.csv")[0].content()).toBe("old TULEVA_pos_raport_20261020_US.csv");
  });

  test("the folder's file names change after the US copy's content does, so a reader watching the names sees the new content", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    drive.seed(seeded);

    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020.csv", "B;2\n"), POSITIONS, "20261020", SOURCE);

    const contentUpdated = drive.events.indexOf("setContent TULEVA_pos_raport_20261020_US.csv");
    expect(contentUpdated).toBeGreaterThan(-1);
    expect(drive.events.slice(contentUpdated + 1).some((e) => e.startsWith("create "))).toBe(true);
  });

  test("the new original is the last file written, so it is the last updated report of that date", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    drive.seed(seeded);

    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020.csv", "B;2\n"), POSITIONS, "20261020", SOURCE);

    expect(drive.events.slice(-2)).toEqual([
      "rename TULEVA_pos_raport_20261020.csv -> TULEVA_pos_raport_20261020_v1.csv",
      "create TULEVA_pos_raport_20261020.csv",
    ]);
  });

  test("a resent report takes the next free version number", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    drive.seed([
      "TULEVA_ootel_tehingud_20261020_v1.csv",
      "TULEVA_ootel_tehingud_20261020_US_v1.csv",
      "TULEVA_ootel_tehingud_20261020.csv",
      "TULEVA_ootel_tehingud_20261020_US.csv",
    ]);

    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_ootel_tehingud_20261020.csv", "C;3\n"), PENDING, "20261020", SOURCE);

    expect(drive.names().sort()).toEqual(
      [
        "TULEVA_ootel_tehingud_20261020.csv",
        "TULEVA_ootel_tehingud_20261020_US.csv",
        "TULEVA_ootel_tehingud_20261020_US_v1.csv",
        "TULEVA_ootel_tehingud_20261020_US_v2.csv",
        "TULEVA_ootel_tehingud_20261020_v1.csv",
        "TULEVA_ootel_tehingud_20261020_v2.csv",
      ].sort(),
    );
  });

  test.each([
    ["makeCopy", "TULEVA_pos_raport_20261020_US.csv", "old TULEVA_pos_raport_20261020_US.csv"],
    ["setContent", "TULEVA_pos_raport_20261020_US.csv", "old TULEVA_pos_raport_20261020_US.csv"],
    ["setName", "TULEVA_pos_raport_20261020_US.csv", "B,2\n"],
    ["createFile", "TULEVA_pos_raport_20261020_korrigeeritud.csv", "B,2\n"],
    ["setName", "TULEVA_pos_raport_20261020.csv", "B,2\n"],
  ])("when %s fails on %s the date still has exactly one current US copy", (action, name, expectedContent) => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    drive.seed(seeded);
    drive.failures[action + " " + name] = true;

    expect(() =>
      saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020_korrigeeritud.csv", "B;2\n"), POSITIONS, "20261020", SOURCE),
    ).toThrow("Drive failed");

    const current = drive.currentUsCopiesFor("tuleva_pos", "20261020");
    expect(current).toHaveLength(1);
    expect(current[0].content()).toBe(expectedContent);
  });

  test("an older message never replaces the content of a newer correction", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020_korrigeeritud.csv", "B;2\n"), POSITIONS, "20261020", LATER);
    const before = drive.names();

    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n"), POSITIONS, "20261020", SOURCE);

    expect(drive.names()).toEqual(before);
    expect(drive.currentUsCopiesFor("tuleva_pos", "20261020").map((f) => f.content())).toEqual(["B,2\n"]);
  });

  test("the same e-mail resumes a copy that failed half way", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    drive.failures["createFile TULEVA_pos_raport_20261020.csv"] = true;
    const attachment = fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n");
    expect(() => saveReportToDrive(drive.folder, attachment, POSITIONS, "20261020", SOURCE)).toThrow("Drive failed");

    delete drive.failures["createFile TULEVA_pos_raport_20261020.csv"];
    saveReportToDrive(drive.folder, attachment, POSITIONS, "20261020", SOURCE);

    expect(drive.byName("TULEVA_pos_raport_20261020.csv").map((f) => f.content())).toEqual(["A;1\n"]);
    expect(drive.currentUsCopiesFor("tuleva_pos", "20261020").map((f) => f.content())).toEqual(["A,1\n"]);
  });

  test("a report the folder already holds with the same content, as the old Gmail saver wrote it, gets no new version", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    drive.seedWithContent({
      "TULEVA_pos_raport_20261020.csv": "A;1,5\n",
      "TULEVA_pos_raport_20261020_US.csv": "A,1.5\n",
    });

    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1,5\n"), POSITIONS, "20261020", SOURCE);

    expect(drive.events).toEqual([]);
    expect(drive.names()).toEqual(["TULEVA_pos_raport_20261020.csv", "TULEVA_pos_raport_20261020_US.csv"]);
  });

  test("a correction the old Gmail saver has already versioned in gets no new version", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    drive.seedWithContent({
      "TULEVA_pos_raport_20261020_v1.csv": "A;1\n",
      "TULEVA_pos_raport_20261020_US_v1.csv": "A,1\n",
      "TULEVA_pos_raport_20261020_korrigeeritud.csv": "B;2\n",
      "TULEVA_pos_raport_20261020_korrigeeritud_US.csv": "B,2\n",
    });

    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020_korrigeeritud.csv", "B;2\n"), POSITIONS, "20261020", LATER);

    expect(drive.events).toEqual([]);
  });

  test("a copy resumed after its new original failed keeps the US copy it already updated, without another version", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    drive.seedWithContent({
      "TULEVA_pos_raport_20261020_v1.csv": "A;1\n",
      "TULEVA_pos_raport_20261020_US_v1.csv": "A,1\n",
      "TULEVA_pos_raport_20261020_korrigeeritud_US.csv": "B,2\n",
    });

    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020_korrigeeritud.csv", "B;2\n"), POSITIONS, "20261020", LATER);

    expect(drive.events).toEqual(["create TULEVA_pos_raport_20261020_korrigeeritud.csv"]);
  });

  test("a report whose original matches but whose US copy does not is written again, ending with a new original", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    drive.seedWithContent({
      "TULEVA_pos_raport_20261020.csv": "B;2\n",
      "TULEVA_pos_raport_20261020_US.csv": "A,1\n",
    });

    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020.csv", "B;2\n"), POSITIONS, "20261020", SOURCE);

    expect(drive.currentUsCopiesFor("tuleva_pos", "20261020").map((f) => f.content())).toEqual(["B,2\n"]);
    expect(drive.events.slice(-1)).toEqual(["create TULEVA_pos_raport_20261020.csv"]);
  });

  test("a report whose current Drive copy already came from this e-mail is not saved again", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    const attachment = fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n");
    saveReportToDrive(drive.folder, attachment, POSITIONS, "20261020", SOURCE);
    const eventsAfterTheCopy = drive.events.length;

    saveReportToDrive(drive.folder, attachment, POSITIONS, "20261020", SOURCE);

    expect(drive.events.slice(eventsAfterTheCopy)).toEqual([]);
    expect(drive.names()).toEqual(["TULEVA_pos_raport_20261020_US.csv", "TULEVA_pos_raport_20261020.csv"]);
  });

  test("a newer e-mail replaces a report whose current Drive copy came from an earlier one", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n"), POSITIONS, "20261020", SOURCE);

    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020_korrigeeritud.csv", "B;2\n"), POSITIONS, "20261020", LATER);

    expect(drive.currentUsCopiesFor("tuleva_pos", "20261020").map((f) => f.content())).toEqual(["B,2\n"]);
  });

  test("a Drive record that cannot be saved does not fail the copy", () => {
    installAppsScriptFakes();
    const store = PropertiesService.getScriptProperties();
    global.PropertiesService = {
      getScriptProperties: () => ({
        ...store,
        setProperty: (key, value) => {
          if (key.startsWith("drive_source:")) throw new Error("properties quota");
          store.setProperty(key, value);
        },
      }),
    };
    const drive = fakeDrive();

    expect(() =>
      saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n"), POSITIONS, "20261020", SOURCE),
    ).not.toThrow();
    expect(drive.names()).toEqual(["TULEVA_pos_raport_20261020_US.csv", "TULEVA_pos_raport_20261020.csv"]);
  });

  test("of two e-mails with the same time the one with the higher Gmail id wins, in either order", () => {
    const tie = (id) => ({ date: MORNING.toISOString(), id });
    [["18f0a00000000001", "18f0a00000000009"], ["18f0a00000000009", "18f0a00000000001"]].forEach((order) => {
      installAppsScriptFakes();
      const drive = fakeDrive();
      order.forEach((id) =>
        saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020_" + id + ".csv", id + "\n"), POSITIONS, "20261020", tie(id)),
      );
      expect(drive.currentUsCopiesFor("tuleva_pos", "20261020").map((f) => f.content())).toEqual(["18f0a00000000009\n"]);
    });
  });

  test("the reports of each type and date are tracked apart", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261021.csv", "D;4\n"), POSITIONS, "20261021", LATER);

    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n"), POSITIONS, "20261020", SOURCE);
    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_ootel_tehingud_20261021.csv", "E;5\n"), PENDING, "20261021", SOURCE);

    expect(drive.currentUsCopiesFor("tuleva_pos", "20261020")).toHaveLength(1);
    expect(drive.currentUsCopiesFor("tuleva_ootel", "20261021")).toHaveLength(1);
  });

  test("an attachment already named _US.csv is not saved, and Slack says why", () => {
    const { slack } = installAppsScriptFakes({ properties: { SLACK_WEBHOOK_URL: "https://hooks.example/x" } });
    const drive = fakeDrive();

    saveReportToDrive(drive.folder, fakeAttachment("TULEVA_pos_raport_20261020_US.CSV", "A,1\n"), POSITIONS, "20261020", SOURCE);

    expect(drive.names()).toEqual([]);
    expect(slack).toHaveLength(1);
    expect(slack[0]).toContain("TULEVA_pos_raport_20261020_US.CSV");
  });
});

describe("the e-mail record per report type and date", () => {
  test("an older e-mail is older; the same e-mail and a newer one are not", () => {
    expect(isOlderThanCurrentSource(SOURCE, LATER)).toBe(true);
    expect(isOlderThanCurrentSource(LATER, SOURCE)).toBe(false);
    expect(isOlderThanCurrentSource(SOURCE, SOURCE)).toBe(false);
    expect(isOlderThanCurrentSource(SOURCE, null)).toBe(false);
  });
  test("records whose e-mail is more than 45 days old are dropped, whatever the report date", () => {
    const record = (date) => JSON.stringify({ date, id: "18f0a00000000001" });
    const { props } = installAppsScriptFakes({
      properties: {
        "report_source:seb/2026-10-20_positions.csv": record("2026-09-16T08:45:00.000Z"),
        "report_source:seb/2026-08-01_pending_transactions.csv": record("2026-11-01T10:15:00.000Z"),
        "report_source:seb/2026-10-31_positions.csv": record("2026-11-01T08:45:00.000Z"),
        "report_source:portfolio/2026-10-19.csv": "not json",
        "drive_source:seb/2026-10-20_positions.csv": record("2026-09-16T08:45:00.000Z"),
        "drive_source:seb/2026-10-31_positions.csv": record("2026-11-01T08:45:00.000Z"),
        SLACK_WEBHOOK_URL: "https://hooks.example/x",
      },
    });

    forgetOldReportSources(PropertiesService.getScriptProperties(), new Date("2026-11-02T09:00:00Z"));

    expect(Object.keys(props).sort()).toEqual([
      "SLACK_WEBHOOK_URL",
      "drive_source:seb/2026-10-31_positions.csv",
      "report_source:seb/2026-08-01_pending_transactions.csv",
      "report_source:seb/2026-10-31_positions.csv",
    ]);
  });
});

describe("driveReportFamily", () => {
  test("returns the current files of that type and date and the next version", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    drive.seed([
      "TULEVA_pos_raport_20261020.csv",
      "TULEVA_pos_raport_20261020_US.csv",
      "TULEVA_pos_raport_20261020_US_v4.csv",
      "TULEVA_pos_raport_20261019_US_v9.csv",
    ]);

    const family = driveReportFamily(drive.folder, POSITIONS, "20261020");

    expect(family.current.map((f) => f.getName())).toEqual(["TULEVA_pos_raport_20261020.csv", "TULEVA_pos_raport_20261020_US.csv"]);
    expect(family.nextVersion).toBe(5);
  });
});

describe("saveThreadToDrive", () => {
  const report = (date) => [fakeAttachment("TULEVA_pos_raport_" + date + ".csv", "A;1\n")];

  test("also copies the messages the S3 pass has not uploaded, and leaves the thread's labels to S3", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    const thread = fakeThread([sebMessage("m1", report("20261019")), sebMessage("m2", report("20261020"))], ["S3-Uploaded-1x"]);

    saveThreadToDrive(thread, drive.folder);

    expect(drive.names()).toEqual([
      "TULEVA_pos_raport_20261019_US.csv",
      "TULEVA_pos_raport_20261019.csv",
      "TULEVA_pos_raport_20261020_US.csv",
      "TULEVA_pos_raport_20261020.csv",
    ]);
    expect(thread.labels).toEqual(["S3-Uploaded-1x"]);
  });

  test("the next run writes nothing for the reports already in Drive", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    const thread = fakeThread([sebMessage("m1", report("20261019")), sebMessage("m2", report("20261020"))], ["S3-Uploaded-2x"]);
    saveThreadToDrive(thread, drive.folder);
    const eventsAfterTheFirstRun = drive.events.length;

    saveThreadToDrive(thread, drive.folder);

    expect(drive.events.slice(eventsAfterTheFirstRun)).toEqual([]);
  });

  test("a failed copy is retried by the next run", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    drive.failures["createFile TULEVA_pos_raport_20261020_US.csv"] = true;
    const thread = fakeThread([sebMessage("m1", report("20261020"))], ["S3-Uploaded-1x"]);
    saveThreadToDrive(thread, drive.folder);
    expect(drive.names()).toEqual([]);

    delete drive.failures["createFile TULEVA_pos_raport_20261020_US.csv"];
    saveThreadToDrive(thread, drive.folder);

    expect(drive.names()).toEqual(["TULEVA_pos_raport_20261020_US.csv", "TULEVA_pos_raport_20261020.csv"]);
  });

  test("a failed copy does not hold up the later e-mails of its thread, and Slack hears about it", () => {
    const { slack } = installAppsScriptFakes({ properties: { SLACK_WEBHOOK_URL: "https://hooks.example/x" } });
    const drive = fakeDrive();
    drive.failures["createFile TULEVA_pos_raport_20261019_US.csv"] = true;
    const thread = fakeThread([sebMessage("m1", report("20261019")), sebMessage("m2", report("20261020"))], ["S3-Uploaded-2x"]);

    saveThreadToDrive(thread, drive.folder);

    expect(drive.names()).toEqual(["TULEVA_pos_raport_20261020_US.csv", "TULEVA_pos_raport_20261020.csv"]);
    expect(slack).toEqual([expect.stringContaining("not in Drive yet")]);
  });

  test("retrying a message whose copy failed half way leaves its reports already in Drive as they are", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    drive.failures["createFile TULEVA_ootel_tehingud_20261020_US.csv"] = true;
    const thread = fakeThread(
      [sebMessage("m1", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n"), fakeAttachment("TULEVA_ootel_tehingud_20261020.csv", "P;1\n")])],
      ["S3-Uploaded-1x"],
    );
    saveThreadToDrive(thread, drive.folder);
    const eventsBeforeTheRetry = drive.events.length;

    delete drive.failures["createFile TULEVA_ootel_tehingud_20261020_US.csv"];
    saveThreadToDrive(thread, drive.folder);

    expect(drive.events.slice(eventsBeforeTheRetry)).toEqual([
      "create TULEVA_ootel_tehingud_20261020_US.csv",
      "create TULEVA_ootel_tehingud_20261020.csv",
    ]);
    expect(drive.names()).toEqual([
      "TULEVA_pos_raport_20261020_US.csv",
      "TULEVA_pos_raport_20261020.csv",
      "TULEVA_ootel_tehingud_20261020_US.csv",
      "TULEVA_ootel_tehingud_20261020.csv",
    ]);
  });

  test.each([
    ["Slack does not take the alert", { SLACK_WEBHOOK_URL: "https://hooks.example/x" }, [503]],
    ["the Slack webhook call fails", { SLACK_WEBHOOK_URL: "https://hooks.example/x" }, [new Error("timeout")]],
    ["no Slack webhook is configured", {}, []],
  ])("a report rejected for its _US name is still not saved, and does not hold up the thread, when %s", (name, properties, slackFailures) => {
    const { slackResponses } = installAppsScriptFakes({ properties });
    slackResponses.push(...slackFailures);
    const drive = fakeDrive();
    const thread = fakeThread(
      [
        sebMessage("m1", [fakeAttachment("TULEVA_pos_raport_20261020_US.csv", "A,1\n")]),
        sebMessage("m2", [fakeAttachment("TULEVA_pos_raport_20261021.csv", "B;2\n")]),
      ],
      ["S3-Uploaded-2x"],
    );

    expect(() => saveThreadToDrive(thread, drive.folder)).not.toThrow();

    expect(drive.names()).toEqual(["TULEVA_pos_raport_20261021_US.csv", "TULEVA_pos_raport_20261021.csv"]);
  });

  test("an alert Slack did not take about a _US.csv attachment is sent by the next run", () => {
    const { slack, slackResponses } = installAppsScriptFakes({ properties: { SLACK_WEBHOOK_URL: "https://hooks.example/x" } });
    slackResponses.push(503);
    const drive = fakeDrive();
    const thread = fakeThread([sebMessage("m1", [fakeAttachment("TULEVA_pos_raport_20261020_US.csv", "A,1\n")])], ["S3-Uploaded-1x"]);
    saveThreadToDrive(thread, drive.folder);
    expect(slack).toEqual([]);

    saveThreadToDrive(thread, drive.folder);
    saveThreadToDrive(thread, drive.folder);

    expect(slack).toEqual([expect.stringContaining("TULEVA_pos_raport_20261020_US.csv")]);
    expect(drive.names()).toEqual([]);
  });

  test("a thread without SEB reports leaves Drive as it is", () => {
    installAppsScriptFakes();
    const drive = fakeDrive();
    const thread = fakeThread([swedbankMessage("m1")], ["S3-Uploaded-1x"]);

    saveThreadToDrive(thread, drive.folder);

    expect(drive.names()).toEqual([]);
    expect(thread.labels).toEqual(["S3-Uploaded-1x"]);
  });
});

describe("saveReportsToDrive", () => {
  const report = (date) => [fakeAttachment("TULEVA_pos_raport_" + date + ".csv", "A;1\n")];

  test("only alerts, once a day, when the folder Script Property is not set", () => {
    const { calls, slack } = installAppsScriptFakes({ properties: { SLACK_WEBHOOK_URL: "https://hooks.example/x" } });

    saveReportsToDrive(2);
    saveReportsToDrive(2);

    expect(calls).toEqual({ search: 0, lock: 0, folders: [] });
    expect(slack).toHaveLength(1);
    expect(slack[0]).toContain("DRIVE_SEB_REPORT_FOLDER_ID");
  });

  test("a run does nothing while another run holds the script lock, and the next trigger retries", () => {
    const drive = fakeDrive();
    const thread = fakeThread([sebMessage("m1", report("20261020"))], []);
    const { calls, s3Keys } = installAppsScriptFakes({
      properties: { AWS_ACCESS_KEY_ID: "test-key", AWS_SECRET_ACCESS_KEY: "test-secret", DRIVE_SEB_REPORT_FOLDER_ID: "folder-1" },
      folders: { "folder-1": drive.folder },
      threads: [thread],
      lockAvailable: false,
    });

    processNewEmails();

    expect(calls.search).toBe(0);
    expect(s3Keys).toEqual([]);
    expect(thread.labels).toEqual([]);
  });

  test("a catch-up run refuses to start while another run holds the script lock", () => {
    const { calls } = installAppsScriptFakes({ lockAvailable: false });

    expect(() => processHistoricalEmails()).toThrow("another run holds the script lock");
    expect(calls.search).toBe(0);
  });

  test("one failing thread does not stop the others, and Slack hears about it once a day", () => {
    const drive = fakeDrive();
    drive.failures["createFile TULEVA_pos_raport_20261019_US.csv"] = true;
    const failing = fakeThread([sebMessage("m1", report("20261019"))], ["S3-Uploaded-1x"]);
    const working = fakeThread([sebMessage("m2", report("20261020"))], ["S3-Uploaded-1x"]);
    const { slack } = installAppsScriptFakes({
      properties: {
        DRIVE_SEB_REPORT_FOLDER_ID: "folder-1",
        SLACK_WEBHOOK_URL: "https://hooks.example/x",
      },
      folders: { "folder-1": drive.folder },
      threads: [working, failing],
    });

    saveReportsToDrive(2);
    saveReportsToDrive(2);

    expect(drive.currentUsCopiesFor("tuleva_pos", "20261020")).toHaveLength(1);
    expect(drive.currentUsCopiesFor("tuleva_pos", "20261019")).toEqual([]);
    expect(slack).toHaveLength(1);
    expect(slack[0]).toContain("not in Drive");
  });

  test("replaying older e-mails after a newer one, as the first run does, keeps the newer content", () => {
    const drive = fakeDrive();
    const newer = fakeThread(
      [sebMessage("18f0a00000000002", [fakeAttachment("TULEVA_pos_raport_20261020_korrigeeritud.csv", "B;2\n")], new Date("2026-11-02T10:15:00Z"))],
      ["S3-Uploaded-1x"],
    );
    const older = fakeThread([sebMessage("18f0a00000000001", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n")])], ["S3-Uploaded-1x"]);
    installAppsScriptFakes({
      properties: { DRIVE_SEB_REPORT_FOLDER_ID: "folder-1" },
      folders: { "folder-1": drive.folder },
      threads: [older, newer],
    });

    saveReportsToDrive(2);

    expect(drive.currentUsCopiesFor("tuleva_pos", "20261020").map((f) => f.content())).toEqual(["B,2\n"]);
  });

  test("a failed original that is retried after a newer correction succeeded does not bring back its content", () => {
    const drive = fakeDrive();
    drive.failures["createFile TULEVA_pos_raport_20261020.csv"] = true;
    const original = fakeThread(
      [sebMessage("18f0a00000000001", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n")], new Date("2026-11-02T08:45:00Z"))],
      ["S3-Uploaded-1x"],
    );
    const correction = fakeThread(
      [sebMessage("18f0a00000000002", [fakeAttachment("TULEVA_pos_raport_20261020_korrigeeritud.csv", "B;2\n")], new Date("2026-11-02T10:15:00Z"))],
      ["S3-Uploaded-1x"],
    );
    installAppsScriptFakes({
      properties: { DRIVE_SEB_REPORT_FOLDER_ID: "folder-1" },
      folders: { "folder-1": drive.folder },
      threads: [correction, original],
    });

    saveReportsToDrive(2);
    delete drive.failures["createFile TULEVA_pos_raport_20261020.csv"];
    saveReportsToDrive(2);

    expect(drive.currentUsCopiesFor("tuleva_pos", "20261020").map((f) => f.content())).toEqual(["B,2\n"]);
  });

  test("a thread whose messages cannot be read does not stop the others", () => {
    const drive = fakeDrive();
    const unreadable = {
      getMessages: () => {
        throw new Error("Gmail unavailable");
      },
      getFirstMessageSubject: () => "SEB reports",
    };
    const working = fakeThread([sebMessage("m2", report("20261020"))], ["S3-Uploaded-1x"]);
    installAppsScriptFakes({ properties: { DRIVE_SEB_REPORT_FOLDER_ID: "folder-1" }, folders: { "folder-1": drive.folder }, threads: [working, unreadable] });

    saveReportsToDrive(2);

    expect(drive.currentUsCopiesFor("tuleva_pos", "20261020")).toHaveLength(1);
  });

  test("the first run copies the e-mails of the last two days, and leaves the reports the old Gmail saver already saved as it is", () => {
    const drive = fakeDrive();
    drive.seedWithContent({
      "TULEVA_pos_raport_20261030.csv": "A;1\n",
      "TULEVA_pos_raport_20261030_US.csv": "A,1\n",
    });
    const dailyReports = fakeThread(
      [
        sebMessage("18f0a00000000001", report("20261027"), new Date("2026-10-28T08:45:00Z")),
        sebMessage("18f0a00000000002", report("20261030"), new Date("2026-11-01T08:45:00Z")),
        sebMessage("18f0a00000000003", report("20261031"), new Date("2026-11-02T08:45:00Z")),
      ],
      ["S3-Uploaded-3x"],
    );
    installAppsScriptFakes({ properties: { DRIVE_SEB_REPORT_FOLDER_ID: "folder-1" }, folders: { "folder-1": drive.folder }, threads: [dailyReports] });

    saveReportsToDrive(2);

    expect(drive.events).toEqual(["create TULEVA_pos_raport_20261031_US.csv", "create TULEVA_pos_raport_20261031.csv"]);
  });

  test("a catch-up run copies the e-mails of its own window", () => {
    const drive = fakeDrive();
    const dailyReports = fakeThread(
      [
        sebMessage("18f0a00000000001", report("20261027"), new Date("2026-10-28T08:45:00Z")),
        sebMessage("18f0a00000000003", report("20261031"), new Date("2026-11-02T08:45:00Z")),
      ],
      ["S3-Uploaded-2x"],
    );
    installAppsScriptFakes({ properties: { DRIVE_SEB_REPORT_FOLDER_ID: "folder-1" }, folders: { "folder-1": drive.folder }, threads: [dailyReports] });
    saveReportsToDrive(2);

    saveReportsToDrive(7);

    expect(drive.currentUsCopiesFor("tuleva_pos", "20261027")).toHaveLength(1);
    expect(drive.currentUsCopiesFor("tuleva_pos", "20261031")).toHaveLength(1);
  });

  test("a folder it cannot open is reported, not thrown", () => {
    const { slack } = installAppsScriptFakes({
      properties: { DRIVE_SEB_REPORT_FOLDER_ID: "missing-folder", SLACK_WEBHOOK_URL: "https://hooks.example/x" },
    });

    expect(() => saveReportsToDrive(2)).not.toThrow();
    expect(slack).toHaveLength(1);
    expect(slack[0]).toContain("could not be saved to Drive");
  });
});

describe("one run: S3 first, then Drive", () => {
  const awsAndDrive = (drive) => ({
    properties: {
      AWS_ACCESS_KEY_ID: "test-key",
      AWS_SECRET_ACCESS_KEY: "test-secret",
      DRIVE_SEB_REPORT_FOLDER_ID: "folder-1",
    },
    folders: { "folder-1": drive.folder },
  });

  test.each([
    ["processNewEmails", () => processNewEmails()],
    ["processHistoricalEmails", () => processHistoricalEmails()],
  ])("%s uploads a new report to S3 and copies it to Drive in the same run", (name, run) => {
    const drive = fakeDrive();
    const thread = fakeThread([sebMessage("m1", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n")])], []);
    const fakes = installAppsScriptFakes(Object.assign(awsAndDrive(drive), { threads: [thread] }));

    run();

    expect(fakes.s3Keys).toEqual(["seb/2026-10-20_positions.csv"]);
    expect(drive.names()).toEqual(["TULEVA_pos_raport_20261020_US.csv", "TULEVA_pos_raport_20261020.csv"]);
    expect(thread.labels).toEqual(["S3-Uploaded-1x"]);
  });

  test.each([
    ["processNewEmails", () => processNewEmails()],
    ["processHistoricalEmails", () => processHistoricalEmails()],
  ])("when S3 rejects the upload, %s still copies the report to Drive and then fails with the S3 error", (name, run) => {
    const drive = fakeDrive();
    const thread = fakeThread([sebMessage("m1", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n")])], []);
    const fakes = installAppsScriptFakes(Object.assign(awsAndDrive(drive), { threads: [thread], s3Status: 403 }));

    expect(run).toThrow("S3 upload failed: 403");

    expect(fakes.s3Keys).toEqual([]);
    expect(drive.names()).toEqual(["TULEVA_pos_raport_20261020_US.csv", "TULEVA_pos_raport_20261020.csv"]);
    expect(thread.labels).toEqual([]);
  });

  test("processNewEmails tells Slack about the S3 failure after the Drive copy", () => {
    const drive = fakeDrive();
    const thread = fakeThread([sebMessage("m1", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n")])], []);
    const setup = awsAndDrive(drive);
    setup.properties.SLACK_WEBHOOK_URL = "https://hooks.example/x";
    const fakes = installAppsScriptFakes(Object.assign(setup, { threads: [thread], s3Status: 403 }));

    expect(() => processNewEmails()).toThrow("S3 upload failed");

    expect(fakes.slack).toEqual([expect.stringContaining("Uncaught error in `processNewEmails`")]);
  });

  test("once S3 accepts uploads again, the next run uploads the report and leaves its Drive copy as it is", () => {
    const drive = fakeDrive();
    const thread = fakeThread([sebMessage("m1", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n")])], []);
    const fakes = installAppsScriptFakes(Object.assign(awsAndDrive(drive), { threads: [thread], s3Status: 403 }));
    expect(() => processNewEmails()).toThrow("S3 upload failed");
    const namesAfterTheFailedRun = drive.names();

    fakes.s3.status = 200;
    processNewEmails();

    expect(fakes.s3Keys).toEqual(["seb/2026-10-20_positions.csv"]);
    expect(drive.names()).toEqual(namesAfterTheFailedRun);
    expect(thread.labels).toEqual(["S3-Uploaded-1x"]);
  });

  test("when S3 rejects the uploads, an older e-mail still does not replace a newer correction in Drive", () => {
    const drive = fakeDrive();
    const correctionUploadedBeforeTheRecord = fakeThread(
      [sebMessage("18f0a00000000002", [fakeAttachment("TULEVA_pos_raport_20261020_korrigeeritud.csv", "B;2\n")], new Date("2026-11-02T10:15:00Z"))],
      ["S3-Uploaded-1x"],
    );
    const olderNeverUploaded = fakeThread(
      [sebMessage("18f0a00000000001", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n")], new Date("2026-11-01T08:45:00Z"))],
      [],
    );
    const anotherDate = fakeThread([sebMessage("18f0a00000000003", [fakeAttachment("TULEVA_pos_raport_20261021.csv", "C;3\n")])], []);
    const fakes = installAppsScriptFakes(
      Object.assign(awsAndDrive(drive), { threads: [anotherDate, correctionUploadedBeforeTheRecord, olderNeverUploaded], s3Status: 403 }),
    );

    expect(() => processNewEmails()).toThrow("S3 upload failed");

    expect(fakes.s3Keys).toEqual([]);
    expect(drive.currentUsCopiesFor("tuleva_pos", "20261020").map((f) => f.content())).toEqual(["B,2\n"]);
    expect(drive.currentUsCopiesFor("tuleva_pos", "20261021").map((f) => f.content())).toEqual(["C,3\n"]);
  });
});

describe("S3 and Drive keep the newest e-mail of each report date", () => {
  const fakesWith = (drive, threads) =>
    installAppsScriptFakes({
      properties: { AWS_ACCESS_KEY_ID: "test-key", AWS_SECRET_ACCESS_KEY: "test-secret", DRIVE_SEB_REPORT_FOLDER_ID: "folder-1" },
      folders: { "folder-1": drive.folder },
      threads,
    });
  const correctionThread = () =>
    fakeThread(
      [sebMessage("18f0a00000000002", [fakeAttachment("TULEVA_pos_raport_20261020_korrigeeritud.csv", "B;2\n")], new Date("2026-11-02T10:15:00Z"))],
      [],
    );

  test("a catch-up run does not put an older report back on S3 or in Drive", () => {
    const drive = fakeDrive();
    const correction = correctionThread();
    const fakes = fakesWith(drive, [correction]);
    processNewEmails();
    const neverUploaded = fakeThread(
      [sebMessage("18f0a00000000001", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n")], new Date("2026-10-31T08:45:00Z"))],
      [],
    );
    GmailApp.search = () => [correction, neverUploaded];

    processHistoricalEmails();

    expect(fakes.s3Keys).toEqual(["seb/2026-10-20_positions.csv"]);
    expect(fakes.s3Bodies["seb/2026-10-20_positions.csv"]).toBe("B;2\n");
    expect(drive.currentUsCopiesFor("tuleva_pos", "20261020").map((f) => f.content())).toEqual(["B,2\n"]);
    expect(neverUploaded.labels).toEqual(["S3-Uploaded-1x"]);
  });

  test("a correction uploaded before the e-mail record existed still beats an older e-mail a catch-up finds", () => {
    const drive = fakeDrive();
    const uploadedBeforeTheRecord = fakeThread(
      [sebMessage("18f0a00000000002", [fakeAttachment("TULEVA_pos_raport_20261020_korrigeeritud.csv", "B;2\n")], new Date("2026-11-02T10:15:00Z"))],
      ["S3-Uploaded-1x"],
    );
    const neverUploaded = fakeThread(
      [sebMessage("18f0a00000000001", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n")], new Date("2026-10-31T08:45:00Z"))],
      [],
    );
    const fakes = fakesWith(drive, [uploadedBeforeTheRecord, neverUploaded]);

    processHistoricalEmails();

    expect(fakes.s3Keys).toEqual([]);
    expect(JSON.parse(fakes.props["report_source:seb/2026-10-20_positions.csv"]).id).toBe("18f0a00000000002");
    expect(neverUploaded.labels).toEqual(["S3-Uploaded-1x"]);
    expect(drive.currentUsCopiesFor("tuleva_pos", "20261020").map((f) => f.content())).toEqual(["B,2\n"]);
  });

  test("when the record of an earlier correction cannot be saved, Drive does not get the older report either", () => {
    const drive = fakeDrive();
    const uploadedBeforeTheRecord = fakeThread(
      [sebMessage("18f0a00000000002", [fakeAttachment("TULEVA_pos_raport_20261020_korrigeeritud.csv", "B;2\n")], new Date("2026-11-02T10:15:00Z"))],
      ["S3-Uploaded-1x"],
    );
    const neverUploaded = fakeThread(
      [sebMessage("18f0a00000000001", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n")], new Date("2026-10-31T08:45:00Z"))],
      [],
    );
    const fakes = fakesWith(drive, [uploadedBeforeTheRecord, neverUploaded]);
    const store = PropertiesService.getScriptProperties();
    let failedOnce = false;
    global.PropertiesService = {
      getScriptProperties: () => ({
        ...store,
        setProperty: (key, value) => {
          if (key.startsWith("report_source:") && !failedOnce) {
            failedOnce = true;
            throw new Error("properties quota");
          }
          store.setProperty(key, value);
        },
      }),
    };

    processHistoricalEmails();

    expect(fakes.s3Keys).toEqual([]);
    expect(drive.events).not.toContain("create TULEVA_pos_raport_20261020.csv");
    expect(drive.currentUsCopiesFor("tuleva_pos", "20261020").map((f) => f.content())).toEqual(["B,2\n"]);
  });

  test("an already uploaded older e-mail never lowers the record of a newer one", () => {
    const drive = fakeDrive();
    const olderUploaded = fakeThread(
      [sebMessage("18f0a00000000001", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n")], new Date("2026-10-31T08:45:00Z"))],
      ["S3-Uploaded-1x"],
    );
    const fakes = fakesWith(drive, [olderUploaded]);
    const newer = JSON.stringify({ date: "2026-11-02T10:15:00.000Z", id: "18f0a00000000002" });
    fakes.props["report_source:seb/2026-10-20_positions.csv"] = newer;

    processHistoricalEmails();

    expect(fakes.props["report_source:seb/2026-10-20_positions.csv"]).toBe(newer);
    expect(fakes.s3Keys).toEqual([]);
  });

  test("a newer e-mail for the same date replaces the file on S3 and in Drive", () => {
    const drive = fakeDrive();
    const original = fakeThread([sebMessage("18f0a00000000001", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n")])], []);
    const fakes = fakesWith(drive, [original]);
    processNewEmails();
    const correction = correctionThread();
    GmailApp.search = () => [correction, original];

    processNewEmails();

    expect(fakes.s3Keys).toEqual(["seb/2026-10-20_positions.csv", "seb/2026-10-20_positions.csv"]);
    expect(drive.currentUsCopiesFor("tuleva_pos", "20261020").map((f) => f.content())).toEqual(["B,2\n"]);
    expect(JSON.parse(fakes.props["report_source:seb/2026-10-20_positions.csv"]).id).toBe("18f0a00000000002");
  });

  const s3Only = (threads, properties = {}) =>
    installAppsScriptFakes({
      properties: Object.assign({ AWS_ACCESS_KEY_ID: "test-key", AWS_SECRET_ACCESS_KEY: "test-secret" }, properties),
      threads,
    });
  const newerRecord = JSON.stringify({ date: "2026-11-02T10:15:00.000Z", id: "18f0a00000000009" });

  test("one message with an older positions report and a new pending report uploads only the pending one", () => {
    const thread = fakeThread(
      [
        sebMessage("18f0a00000000001", [
          fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n"),
          fakeAttachment("TULEVA_ootel_tehingud_20261020.csv", "P;1\n"),
        ]),
      ],
      [],
    );
    const fakes = s3Only([thread], { "report_source:seb/2026-10-20_positions.csv": newerRecord });

    processNewEmails();

    expect(fakes.s3Keys).toEqual(["seb/2026-10-20_pending_transactions.csv"]);
    expect(thread.labels).toEqual(["S3-Uploaded-1x"]);
  });

  test("a Swedbank portfolio report is guarded the same way", () => {
    const older = {
      ...noRecipients,
      getId: () => "18f0a00000000001",
      getDate: () => MORNING,
      getFrom: () => "Swedbank <" + SOURCES.SWEDBANK.senders[0] + ">",
      getAttachments: () => [fakeAttachment("TULEVA_PORTFOLIO_20261020.csv", "x")],
    };
    const fakes = s3Only([fakeThread([older], [])], { "report_source:portfolio/2026-10-20.csv": newerRecord });

    processNewEmails();

    expect(fakes.s3Keys).toEqual([]);
  });

  test("a record that cannot be saved after an upload neither stops the run nor repeats the upload", () => {
    const first = fakeThread([sebMessage("18f0a00000000001", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n")])], []);
    const second = fakeThread([sebMessage("18f0a00000000002", [fakeAttachment("TULEVA_pos_raport_20261021.csv", "B;1\n")])], []);
    const fakes = s3Only([second, first]);
    const store = PropertiesService.getScriptProperties();
    global.PropertiesService = {
      getScriptProperties: () => ({
        ...store,
        setProperty: (key, value) => {
          if (key.startsWith("report_source:")) throw new Error("properties quota");
          store.setProperty(key, value);
        },
      }),
    };

    expect(() => processNewEmails()).not.toThrow();
    processNewEmails();

    expect(fakes.s3Keys).toEqual(["seb/2026-10-20_positions.csv", "seb/2026-10-21_positions.csv"]);
    expect(first.labels).toEqual(["S3-Uploaded-1x"]);
    expect(second.labels).toEqual(["S3-Uploaded-1x"]);
  });

  test("a correction uploaded before the record existed beats an older e-mail even when no record can be saved", () => {
    const uploadedBeforeTheRecord = fakeThread(
      [sebMessage("18f0a00000000002", [fakeAttachment("TULEVA_pos_raport_20261020_korrigeeritud.csv", "B;2\n")], new Date("2026-11-02T10:15:00Z"))],
      ["S3-Uploaded-1x"],
    );
    const neverUploaded = fakeThread(
      [sebMessage("18f0a00000000001", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n")], new Date("2026-10-31T08:45:00Z"))],
      [],
    );
    const fakes = s3Only([uploadedBeforeTheRecord, neverUploaded]);
    const store = PropertiesService.getScriptProperties();
    global.PropertiesService = {
      getScriptProperties: () => ({
        ...store,
        setProperty: (key, value) => {
          if (key.startsWith("report_source:")) throw new Error("properties quota");
          store.setProperty(key, value);
        },
      }),
    };

    processHistoricalEmails();

    expect(fakes.s3Keys).toEqual([]);
    expect(neverUploaded.labels).toEqual(["S3-Uploaded-1x"]);
  });

  test("an unreadable record counts as none: the report uploads and the record is rewritten", () => {
    const thread = fakeThread([sebMessage("18f0a00000000001", [fakeAttachment("TULEVA_pos_raport_20261020.csv", "A;1\n")])], []);
    const fakes = s3Only([thread], { "report_source:seb/2026-10-20_positions.csv": "not json" });

    processNewEmails();

    expect(fakes.s3Keys).toEqual(["seb/2026-10-20_positions.csv"]);
    expect(JSON.parse(fakes.props["report_source:seb/2026-10-20_positions.csv"]).id).toBe("18f0a00000000001");
  });

  test("Flow Traders confirmations, named by allocation id, keep no e-mail record", () => {
    const drive = fakeDrive();
    const confirmation = {
      ...noRecipients,
      getId: () => "18f0a00000000003",
      getDate: () => MORNING,
      getFrom: () => "Flow Traders <" + SOURCES.FT.senders[0] + ">",
      getAttachments: () => [{ getName: () => "MID9DSWsDs-00.pdf", getBytes: () => [37, 80, 68, 70] }],
    };
    const fakes = fakesWith(drive, [fakeThread([confirmation], [])]);

    processNewEmails();

    expect(fakes.s3Keys).toEqual(["ft-confirmations/MID9DSWsDs-00.pdf"]);
    expect(Object.keys(fakes.props).filter((k) => k.startsWith("report_source:"))).toEqual([]);
  });
});

describe("alerts never throw", () => {
  test("two problems at once alert once each per day", () => {
    const { slack } = installAppsScriptFakes({ properties: { SLACK_WEBHOOK_URL: "https://hooks.example/x" } });

    for (let run = 0; run < 3; run++) {
      notifyDriveProblemOncePerDay("folder missing");
      notifyDriveProblemOncePerDay("file locked");
    }

    expect(slack).toHaveLength(2);
  });
  test("notifySlack swallows a Script Properties failure", () => {
    installAppsScriptFakes();
    global.PropertiesService = {
      getScriptProperties: () => {
        throw new Error("properties unavailable");
      },
    };
    expect(() => notifySlack("x")).not.toThrow();
  });
  test.each([
    ["a webhook error", new Error("timeout")],
    ["a non-2xx answer", 503],
  ])("after %s the next run tries the alert again", (name, failure) => {
    const { slack, slackResponses } = installAppsScriptFakes({ properties: { SLACK_WEBHOOK_URL: "https://hooks.example/x" } });
    slackResponses.push(failure);

    notifyDriveProblemOncePerDay("folder missing");
    notifyDriveProblemOncePerDay("folder missing");
    notifyDriveProblemOncePerDay("folder missing");

    expect(slack).toEqual([expect.stringContaining("folder missing")]);
  });

  test("a Drive problem alerts again on another day", () => {
    const { slack } = installAppsScriptFakes({ properties: { SLACK_WEBHOOK_URL: "https://hooks.example/x" } });

    notifyDriveProblemOncePerDay("folder missing");
    global.Utilities = { formatDate: () => "2026-11-03" };
    notifyDriveProblemOncePerDay("folder missing");

    expect(slack).toHaveLength(2);
  });
});
