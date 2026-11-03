// Investment CSV to S3
//
// Polls the funds@tuleva.ee Gmail mailbox for fund position-report attachments
// and uploads them to the tuleva-investment-reports S3 bucket. The Spring Boot
// ReportImportJob in TulevaEE/onboarding-service then pulls those files and
// ingests them into investment_report and investment_fund_position.
//
// SEB reports are also saved to a Google Drive folder for Tuleva's Google
// Sheets NAV fallback, so Drive holds the same file as S3 (see saveReportsToDrive).
//
// Required Apps Script Properties (Project Settings > Script properties):
//   AWS_ACCESS_KEY_ID
//   AWS_SECRET_ACCESS_KEY
//   AWS_REGION (defaults to eu-central-1)
//   DRIVE_SEB_REPORT_FOLDER_ID (the Drive copy is skipped when it isn't set)
//
// Cron entry point: processNewEmails() — run every 5 min via a time-driven trigger.
// Manual helpers:   processHistoricalEmails() (7-day catch-up), dryRun() (no-op preview).
//
// Per-thread `S3-Uploaded-Nx` Gmail labels track upload progress so a re-run
// of the cron skips messages that have already been processed.

var S3_BUCKET = "tuleva-investment-reports";
var UPLOAD_LABEL_PREFIX = "S3-Uploaded-";

var SOURCES = {
    SWEDBANK: {
        senders: ["fundadmin@swedbank.ee"],
        files: [
            {
                pattern: /^TULEVA_PORTFOLIO.*?(\d{8}).*\.csv$/,
                s3Prefix: "portfolio/",
                s3Suffix: ".csv"
            }
        ]
    },
    SEB: {
        senders: ["trustee@seb.ee", "taavi.pertman@tuleva.ee"],
        files: [
            {
                pattern: /^TULEVA_pos_raport_(\d{8})(?:[ _().\-].*)?\.csv$/i,
                s3Prefix: "seb/",
                s3Suffix: "_positions.csv",
                driveKeyword: "tuleva_pos"
            },
            {
                pattern: /^TULEVA_ootel_tehingud_(\d{8})(?:[ _().\-].*)?\.csv$/i,
                s3Prefix: "seb/",
                s3Suffix: "_pending_transactions.csv",
                driveKeyword: "tuleva_ootel"
            }
        ],
        // Filenames arriving from SEB senders that are NOT SEB position reports —
        // e.g. Taavi's internal "{fund} NAV arvutamine {ddmmyyyy}.csv" sheets.
        // Matching files are silently skipped: not uploaded, not alerted on.
        ignorePatterns: [
            /^T\w+\s+NAV\s+arvutamine\s+\d{8}\.csv$/i
        ]
    },
    FT: {
        senders: ["flowtraders@ullink.eu"],
        files: [
            {
                // Flow Traders names the confirmation PDF by allocation id,
                // e.g. "MID9DSWsDs-00.pdf" (confirmed against live FT email, 2026-08).
                pattern: /^(MID\w+-\d+)\.pdf$/i,
                s3Prefix: "ft-confirmations/",
                s3Suffix: ".pdf",
                binary: true,
                contentType: "application/pdf",
                keyFromCapture: true
            }
        ]
    }
};

// Every run holds the script lock for its S3 and Drive passes, so an overlapping
// trigger or catch-up run cannot check a report's e-mail record, upload and record
// in between and put an older report back.
var RUN_LOCK_WAIT_MS = 10000;

function processNewEmails() {
    if (!runAlone(function () {
        try {
            processEmailsFromDays(2);
        } catch (e) {
            notifySlack(":rotating_light: Uncaught error in `processNewEmails`: `" + e.message +
                "`. See Apps Script Executions: https://script.google.com/home/projects/1cpcI-qO0ZcCWqb6XdS0AjxE82N_F3rF926nhxyfgZlu3taeKD5qWLA8l/executions");
            throw e;
        }
    })) {
        Logger.log("Skipped, another run holds the script lock; the next trigger retries");
    }
}

function processHistoricalEmails() {
    runAloneOrRefuse("processHistoricalEmails", function () {
        processEmailsFromDays(7);
    });
}

function processHistoricalEmails30Days() {
    runAloneOrRefuse("processHistoricalEmails30Days", function () {
        processEmailsFromDays(30);
    });
}

function runAlone(run) {
    var lock = LockService.getScriptLock();
    if (!lock.tryLock(RUN_LOCK_WAIT_MS)) return false;
    try {
        run();
    } finally {
        lock.releaseLock();
    }
    return true;
}

function runAloneOrRefuse(name, run) {
    if (!runAlone(run)) {
        throw new Error("Run refused, another run holds the script lock; run it again in a minute: run=" + name);
    }
}

function processEmailsFromDays(days, options) {
    options = options || {};
    var minMessageDate = options.minMessageDate || null;

    var searchQuery = gmailSearchQuery(days);
    Logger.log("Searching with query: " + searchQuery + (minMessageDate ? ", minMessageDate=" + minMessageDate.toISOString() : ""));

    var threads = GmailApp.search(searchQuery);
    Logger.log("Found " + threads.length + " email threads");

    var newestUploaded = recordSourcesOfUploadedEmails(threads, minMessageDate);
    uploadToS3ThenSaveToDriveEvenIfAnUploadFails(threads, days, minMessageDate, newestUploaded);

    Logger.log("Processing complete");
}

function uploadToS3ThenSaveToDriveEvenIfAnUploadFails(threads, days, minMessageDate, newestUploaded) {
    try {
        uploadThreadsToS3(threads, minMessageDate, newestUploaded);
    } finally {
        saveReportsToDrive(threads, days, newestUploaded);
    }
}

function uploadThreadsToS3(threads, minMessageDate, newestUploaded) {
    // Process oldest threads first so the newest thread's upload wins on S3.
    for (var i = threads.length - 1; i >= 0; i--) {
        processThread(threads[i], minMessageDate, newestUploaded);
    }
}

function gmailSearchQuery(days) {
    return "to:funds@tuleva.ee has:attachment newer_than:" + days + "d";
}

function processThread(thread, minMessageDate, newestUploaded) {
    var messages = thread.getMessages();
    var uploadCount = getUploadCount(thread);
    var matchingCount = 0;
    for (var j = 0; j < messages.length; j++) {
        var message = messages[j];
        if (!isMessageWithinWindow(message.getDate(), minMessageDate)) continue;
        if (messageHasMatchingAttachments(message)) {
            matchingCount++;
            if (shouldProcessMessage(matchingCount, uploadCount)) {
                processMessage(message, thread, newestUploaded);
            }
        }
    }
}

function isMessageWithinWindow(messageDate, minMessageDate) {
    if (!minMessageDate) return true;
    return messageDate.getTime() >= minMessageDate.getTime();
}

function shouldProcessMessage(matchingMessageIndex, currentUploadCount) {
    return matchingMessageIndex > currentUploadCount;
}

function parseUploadCountFromLabelNames(labelNames) {
    for (var i = 0; i < labelNames.length; i++) {
        if (labelNames[i].indexOf(UPLOAD_LABEL_PREFIX) === 0) {
            var countStr = labelNames[i].replace(UPLOAD_LABEL_PREFIX, "").replace("x", "");
            return parseInt(countStr, 10) || 0;
        }
    }
    return 0;
}

function formatUploadLabelName(count) {
    return UPLOAD_LABEL_PREFIX + count + "x";
}

function partitionFilenamesByMatch(source, filenames) {
    var matched = [];
    var unmatched = [];
    for (var i = 0; i < filenames.length; i++) {
        if (isIgnoredFilename(source, filenames[i])) continue;
        if (getFileConfigAndDate(source, filenames[i])) {
            matched.push(filenames[i]);
        } else {
            unmatched.push(filenames[i]);
        }
    }
    return { matched: matched, unmatched: unmatched };
}

function isIgnoredFilename(source, filename) {
    var patterns = source.ignorePatterns || [];
    for (var i = 0; i < patterns.length; i++) {
        if (patterns[i].test(filename)) return true;
    }
    return false;
}

function shouldAlertOnUnmatched(sender, matchedCount, unmatchedFilenames) {
    if (addressOf(sender) !== FUNDS_MAILBOX) {
        return unmatchedFilenames.length > 0;
    }
    return unmatchedFilenames.some(looksLikeAMisnamedPositionReport);
}

function looksLikeAMisnamedPositionReport(filename) {
    return /^TULEVA_/i.test(filename);
}

function shouldNotifyAboutSkipped(messageId, filenames, alreadyNotified) {
    return !(dedupKeyFor(messageId, filenames) in alreadyNotified);
}

function dedupKeyFor(messageId, filenames) {
    var sortedNames = filenames.slice().sort().join(",");
    return "skipped_notified:" + messageId + ":" + simpleHash(sortedNames);
}

function formatUnmatchedAttachmentAlert(sender, unmatchedFilenames, matchedCount, dedupKey) {
    var prefix = matchedCount > 0
        ? ":warning: SEB email contains UNMATCHED attachments (alongside " + matchedCount + " matched). "
        : ":warning: SEB email skipped — no attachments matched. ";
    return prefix + "Sender: `" + sender +
        "`. Unmatched files: `" + unmatchedFilenames.join(",") +
        "`. Probably a new filename variant — check the regex in `appscripts/seb-position-uploader/src/Code.js`. " +
        "(To re-alert: delete the `" + dedupKey + "` Script Property.)";
}

function simpleHash(str) {
    var h = 0;
    for (var i = 0; i < str.length; i++) {
        h = ((h << 5) - h) + str.charCodeAt(i);
        h |= 0;
    }
    return (h >>> 0).toString(16);
}

function messageHasMatchingAttachments(message) {
    var sender = message.getFrom();
    var source = sourceOf(message);
    if (!source) return false;

    var filenames = message.getAttachments({ includeInlineImages: false })
        .map(function (a) { return a.getName(); });
    var partition = partitionFilenamesByMatch(source, filenames);

    Logger.log("Attachments from known sender: sender=" + sender +
        ", matched=[" + partition.matched.join(",") +
        "], unmatched=[" + partition.unmatched.join(",") + "]");

    if (shouldAlertOnUnmatched(sender, partition.matched.length, partition.unmatched)) {
        notifyOnceForUnmatchedAttachments(message.getId(), sender, partition.unmatched, partition.matched.length);
    }
    return partition.matched.length > 0;
}

function notifyOnceForUnmatchedAttachments(messageId, sender, unmatchedFilenames, matchedCount) {
    var props = PropertiesService.getScriptProperties();
    var key = dedupKeyFor(messageId, unmatchedFilenames);
    var alreadyNotified = {};
    if (props.getProperty(key)) alreadyNotified[key] = props.getProperty(key);

    if (!shouldNotifyAboutSkipped(messageId, unmatchedFilenames, alreadyNotified)) return;

    var logPrefix = matchedCount > 0
        ? "WARNING: Known sender with mixed matched (" + matchedCount + ") + unmatched attachments: "
        : "WARNING: Known sender but no attachments matched any pattern: ";
    Logger.log(logPrefix + "sender=" + sender + ", unmatched=" + unmatchedFilenames.join(",") + ", messageId=" + messageId);
    notifySlack(formatUnmatchedAttachmentAlert(sender, unmatchedFilenames, matchedCount, key));
    props.setProperty(key, new Date().toISOString());
}

function notifySlack(message) {
    try {
        var webhookUrl = PropertiesService.getScriptProperties().getProperty("SLACK_WEBHOOK_URL");
        if (!webhookUrl) {
            Logger.log("SLACK_WEBHOOK_URL not configured — would have sent: " + message);
            return false;
        }
        var response = UrlFetchApp.fetch(webhookUrl, {
            method: "post",
            contentType: "application/json",
            payload: JSON.stringify({ text: "*Investment CSV to S3*: " + message }),
            muteHttpExceptions: true,
        });
        var code = response.getResponseCode();
        if (code < 200 || code >= 300) {
            Logger.log("Slack webhook non-2xx: status=" + code + ", body=" + response.getContentText());
            return false;
        }
        return true;
    } catch (e) {
        Logger.log("Slack notify threw: " + e.message);
        return false;
    }
}

function processMessage(message, thread, newestUploaded) {
    var source = sourceOf(message);

    if (!source) {
        return;
    }

    var attachments = message.getAttachments();
    var handledCount = 0;
    var emailSource = emailSourceOf(message);

    for (var k = 0; k < attachments.length; k++) {
        var attachment = attachments[k];
        var filename = attachment.getName();

        var result = getFileConfigAndDate(source, filename);
        if (result) {
            var s3Key = result.fileConfig.s3Prefix + result.keyStem + result.fileConfig.s3Suffix;
            var datedReport = !result.fileConfig.keyFromCapture;
            if (datedReport && isReplacedByNewerEmail(s3Key, emailSource, newestUploaded)) {
                Logger.log("S3 upload skipped, the file holds a newer e-mail's report: file=" + filename + ", key=" + s3Key + ", messageId=" + emailSource.id);
                handledCount++;
                continue;
            }
            var content = isBinaryFileConfig(result.fileConfig) ? attachment.getBytes() : attachment.getDataAsString();

            try {
                uploadToS3(S3_BUCKET, s3Key, content, resolveContentType(result.fileConfig));
                Logger.log("SUCCESS: Uploaded " + filename + " -> s3://" + S3_BUCKET + "/" + s3Key);
                handledCount++;
            } catch (e) {
                Logger.log("FAILED: " + filename + " -> " + e.message);
                throw e;
            }
            if (datedReport) {
                rememberNewest(newestUploaded, s3Key, emailSource);
                recordEmailSourceAfterUpload(s3Key, emailSource);
            }
        }
    }

    if (handledCount > 0) {
        incrementUploadLabel(thread);
    }
}

// A report named by its date keeps one file per date on S3 and in Drive. A Script
// Property per S3 key records the e-mail (date and Gmail id) whose report that
// file holds, so a catch-up replay or a retry of an older e-mail never puts an
// older report back; of two e-mails with the same time the higher Gmail id wins.
var REPORT_SOURCE_PROPERTY_PREFIX = "report_source:";
var REPORT_SOURCE_KEEP_DAYS = 45;

function emailSourceOf(message) {
    return { date: message.getDate().toISOString(), id: message.getId() };
}

function isReplacedByNewerEmail(s3Key, emailSource, newestUploaded) {
    var recorded = PropertiesService.getScriptProperties().getProperty(REPORT_SOURCE_PROPERTY_PREFIX + s3Key);
    if (isOlderThanCurrentSource(emailSource, parseEmailSource(s3Key, recorded))) return true;
    return Boolean(newestUploaded) && isOlderThanCurrentSource(emailSource, newestUploaded[s3Key] || null);
}

// The run's own memory of the newest uploaded e-mail per report, so a record
// write that fails cannot let an older e-mail through in the same run.
function rememberNewest(newestUploaded, s3Key, emailSource) {
    if (!newestUploaded) return;
    var current = newestUploaded[s3Key] || null;
    if (!current || isOlderThanCurrentSource(current, emailSource)) newestUploaded[s3Key] = emailSource;
}

function parseEmailSource(s3Key, recorded) {
    if (!recorded) return null;
    try {
        return JSON.parse(recorded);
    } catch (e) {
        Logger.log("Unreadable e-mail record ignored: key=" + s3Key + ", value=" + recorded);
        return null;
    }
}

function recordEmailSource(s3Key, emailSource) {
    var props = PropertiesService.getScriptProperties();
    props.setProperty(REPORT_SOURCE_PROPERTY_PREFIX + s3Key, JSON.stringify(emailSource));
    try {
        forgetOldReportSources(props, new Date());
    } catch (e) {
        Logger.log("Old e-mail records not pruned: error=" + e.message);
    }
}

// An e-mail uploaded without a record (before the record existed, or when its
// record write failed) still counts: before any upload, each run records the
// newest already-uploaded e-mail per report, so an older e-mail found later is
// skipped instead of replacing it.
function recordSourcesOfUploadedEmails(threads, minMessageDate) {
    var newestUploaded = {};
    for (var i = 0; i < threads.length; i++) {
        var uploadCount = getUploadCount(threads[i]);
        var matchingCount = 0;
        var messages = threads[i].getMessages();
        for (var j = 0; j < messages.length && matchingCount < uploadCount; j++) {
            if (!isMessageWithinWindow(messages[j].getDate(), minMessageDate)) continue;
            var keys = datedReportKeysOf(messages[j]);
            if (keys === null) continue;
            matchingCount++;
            for (var k = 0; k < keys.length; k++) {
                rememberNewest(newestUploaded, keys[k], emailSourceOf(messages[j]));
                recordEmailSourceIfNewer(keys[k], emailSourceOf(messages[j]));
            }
        }
    }
    return newestUploaded;
}

function datedReportKeysOf(message) {
    var source = sourceOf(message);
    if (!source) return null;
    var attachments = message.getAttachments({ includeInlineImages: false });
    var matched = false;
    var keys = [];
    for (var i = 0; i < attachments.length; i++) {
        var filename = attachments[i].getName();
        if (isIgnoredFilename(source, filename)) continue;
        var result = getFileConfigAndDate(source, filename);
        if (!result) continue;
        matched = true;
        if (!result.fileConfig.keyFromCapture) {
            keys.push(result.fileConfig.s3Prefix + result.keyStem + result.fileConfig.s3Suffix);
        }
    }
    return matched ? keys : null;
}

function recordEmailSourceIfNewer(s3Key, emailSource) {
    try {
        var recorded = PropertiesService.getScriptProperties().getProperty(REPORT_SOURCE_PROPERTY_PREFIX + s3Key);
        var current = parseEmailSource(s3Key, recorded);
        if (current && !isOlderThanCurrentSource(current, emailSource)) return;
        recordEmailSource(s3Key, emailSource);
    } catch (e) {
        Logger.log("E-mail record of an earlier upload not saved: key=" + s3Key + ", error=" + e.message);
    }
}

function recordEmailSourceAfterUpload(s3Key, emailSource) {
    try {
        recordEmailSource(s3Key, emailSource);
    } catch (e) {
        Logger.log("E-mail record not saved, the upload itself went through: key=" + s3Key + ", error=" + e.message);
    }
}

var DRIVE_FOLDER_PROPERTY = "DRIVE_SEB_REPORT_FOLDER_ID";
var DRIVE_ALERTS_PROPERTY = "drive_alerts_today";
var DAY_MS = 24 * 60 * 60 * 1000;

function saveReportsToDrive(threads, days, newestUploaded) {
    try {
        var props = PropertiesService.getScriptProperties();
        var folderId = props.getProperty(DRIVE_FOLDER_PROPERTY);
        if (!folderId) {
            Logger.log("Drive copy skipped, folder Script Property not set: property=" + DRIVE_FOLDER_PROPERTY);
            notifyDriveProblemOncePerDay("SEB reports are not saved to Drive: the Script Property " + DRIVE_FOLDER_PROPERTY + " is not set");
            return;
        }
        var folder = DriveApp.getFolderById(folderId);
        var minMessageDate = new Date(Date.now() - days * DAY_MS);
        for (var i = threads.length - 1; i >= 0; i--) {
            saveThreadToDrive(threads[i], folder, newestUploaded, minMessageDate);
        }
    } catch (e) {
        Logger.log("Drive copy run failed: error=" + e.message);
        notifyDriveProblemOncePerDay("SEB reports could not be saved to Drive: `" + e.message + "`");
    }
}

function saveThreadToDrive(thread, folder, newestUploaded, minMessageDate) {
    driveReportsOfThreadOrNone(thread, minMessageDate).forEach(function (report) {
        saveReportToDriveWithoutStoppingTheOthers(folder, report, newestUploaded);
    });
}

function driveReportsOfThreadOrNone(thread, minMessageDate) {
    try {
        var reports = [];
        thread.getMessages().forEach(function (message) {
            if (!isMessageWithinWindow(message.getDate(), minMessageDate)) return;
            reports = reports.concat(driveReportsOf(message));
        });
        return reports;
    } catch (e) {
        Logger.log("Drive copy failed, thread not read: threadId=" + thread.getId() + ", error=" + e.message);
        notifyDriveCopyFailed(e);
        return [];
    }
}

function saveReportToDriveWithoutStoppingTheOthers(folder, report, newestUploaded) {
    try {
        saveReportToDrive(folder, report.attachment, report.fileConfig, report.reportDate, report.emailSource, newestUploaded);
    } catch (e) {
        Logger.log("Drive copy failed: file=" + report.attachment.getName() + ", messageId=" + report.emailSource.id + ", error=" + e.message);
        notifyDriveCopyFailed(e);
    }
}

function notifyDriveCopyFailed(e) {
    notifyDriveProblemOncePerDay("an SEB report is not in Drive yet: `" + e.message + "`. Each run retries e-mails from the last 2 days; " +
        "for older ones run processHistoricalEmails once the cause is fixed");
}

function driveReportsOf(message) {
    var source = sourceOf(message);
    if (!source) return [];
    var emailSource = emailSourceOf(message);
    var reports = [];
    message.getAttachments().forEach(function (attachment) {
        var result = getFileConfigAndDate(source, attachment.getName());
        if (result && result.fileConfig.driveKeyword) {
            reports.push({ attachment: attachment, fileConfig: result.fileConfig, reportDate: result.keyStem.replace(/-/g, ""), emailSource: emailSource });
        }
    });
    return lastAttachmentOfEachReportAsOnS3(reports);
}

function lastAttachmentOfEachReportAsOnS3(reports) {
    return reports.filter(function (report, i) {
        return !reports.slice(i + 1).some(function (later) {
            return later.fileConfig === report.fileConfig && later.reportDate === report.reportDate;
        });
    });
}

function saveReportToDrive(folder, attachment, fileConfig, reportDate, emailSource, newestUploaded) {
    var name = attachment.getName();
    if (isUsCopyName(name)) {
        Logger.log("Drive copy skipped, the Sheets scripts would read a _US.csv name as converted: file=" + name + ", messageId=" + emailSource.id);
        alertOnceAboutUsCopyName(name, emailSource);
        return;
    }
    var s3Key = fileConfig.s3Prefix + formatDateFromCapture(reportDate) + fileConfig.s3Suffix;
    if (isCurrentDriveCopyFrom(s3Key, emailSource)) {
        Logger.log("Drive copy skipped, the current copy already comes from this e-mail: file=" + name + ", messageId=" + emailSource.id);
        return;
    }
    if (isReplacedByNewerEmail(s3Key, emailSource, newestUploaded)) {
        Logger.log("Drive copy skipped, the current copy comes from a newer e-mail: file=" + name + ", messageId=" + emailSource.id);
        return;
    }
    recordEmailSource(s3Key, emailSource);

    var family = driveReportFamily(folder, fileConfig, reportDate);
    var currentUsCopies = family.current.filter(function (f) { return isUsCopyName(f.getName()); });
    var currentOriginals = family.current.filter(function (f) { return !isUsCopyName(f.getName()); });

    var content = attachment.getDataAsString();
    var usContent = convertSebCsvToUs(content);
    var currentUsContent = currentUsCopies.length > 0 ? contentOf(currentUsCopies[0]) : null;
    if (currentUsCopies.length === 1 && currentUsContent === usContent && isTheOnlyCurrentFileWith(currentOriginals, content)) {
        Logger.log("Drive copy unchanged, the folder already holds this content: file=" + name + ", messageId=" + emailSource.id);
        recordCompletedDriveCopy(s3Key, emailSource);
        return;
    }
    if (currentUsCopies.length === 0) {
        folder.createFile(usCopyName(name), usContent, MimeType.CSV);
    } else {
        updateTheOneCurrentUsCopyInPlace(folder, family, currentUsCopies, currentUsContent, usCopyName(name), usContent);
    }
    replaceTheOriginalLastSoFolderReadersSeeTheNewContentFirst(folder, currentOriginals, attachment, family.nextVersion);
    recordCompletedDriveCopy(s3Key, emailSource);
    Logger.log("Saved to Drive: file=" + name + ", messageId=" + emailSource.id);
}

function updateTheOneCurrentUsCopyInPlace(folder, family, currentUsCopies, currentUsContent, usName, usContent) {
    var current = currentUsCopies[0];
    if (currentUsContent !== usContent) {
        if (!isOldContentAlreadyTheLatestVersion(family, currentUsContent)) current.makeCopy(versionedDriveName(current.getName(), family.nextVersion), folder);
        current.setContent(usContent);
    }
    if (current.getName() !== usName) current.setName(usName);
    currentUsCopies.slice(1).forEach(function (f) { f.setName(versionedDriveName(f.getName(), family.nextVersion)); });
}

function replaceTheOriginalLastSoFolderReadersSeeTheNewContentFirst(folder, currentOriginals, attachment, version) {
    currentOriginals.forEach(function (f) { f.setName(versionedDriveName(f.getName(), version)); });
    folder.createFile(attachment.copyBlob());
}

var DRIVE_SOURCE_PROPERTY_PREFIX = "drive_source:";

function isCurrentDriveCopyFrom(s3Key, emailSource) {
    var recorded = PropertiesService.getScriptProperties().getProperty(DRIVE_SOURCE_PROPERTY_PREFIX + s3Key);
    var current = parseEmailSource(s3Key, recorded);
    return Boolean(current) && current.id === emailSource.id;
}

function recordCompletedDriveCopy(s3Key, emailSource) {
    try {
        PropertiesService.getScriptProperties().setProperty(DRIVE_SOURCE_PROPERTY_PREFIX + s3Key, JSON.stringify(emailSource));
    } catch (e) {
        Logger.log("Drive record not saved, the copy itself went through: key=" + s3Key + ", error=" + e.message);
    }
}

function isOldContentAlreadyTheLatestVersion(family, content) {
    return Boolean(family.latestUsVersion) && contentOf(family.latestUsVersion) === content;
}

var US_NAME_ALERT_PROPERTY_PREFIX = "us_name_alerted:";

function alertOnceAboutUsCopyName(name, emailSource) {
    var key = US_NAME_ALERT_PROPERTY_PREFIX + emailSource.id + ":" + simpleHash(name);
    try {
        var props = PropertiesService.getScriptProperties();
        if (props.getProperty(key)) return;
        var delivered = notifyDriveProblemOncePerDay("`" + name + "` goes to S3 but was not saved to Drive: its name ends in _US.csv, " +
            "which the Google Sheets scripts would read unconverted. Resend it without the _US ending");
        if (delivered) props.setProperty(key, JSON.stringify(emailSource));
    } catch (e) {
        Logger.log("Record of the _US.csv alert not saved: key=" + key + ", error=" + e.message);
    }
}

function isTheOnlyCurrentFileWith(files, content) {
    return files.length === 1 && contentOf(files[0]) === content;
}

function contentOf(file) {
    return file.getBlob().getDataAsString();
}

function isOlderThanCurrentSource(source, current) {
    if (!current || current.id === source.id) return false;
    var sourceTime = new Date(source.date).getTime();
    var currentTime = new Date(current.date).getTime();
    return sourceTime < currentTime || (sourceTime === currentTime && source.id < current.id);
}

function forgetOldReportSources(props, now) {
    var oldest = now.getTime() - REPORT_SOURCE_KEEP_DAYS * 24 * 60 * 60 * 1000;
    var all = props.getProperties();
    Object.keys(all).forEach(function (key) {
        if (!isEmailRecordKey(key)) return;
        if (sourceTime(all[key]) < oldest) props.deleteProperty(key);
    });
}

function isEmailRecordKey(key) {
    return [REPORT_SOURCE_PROPERTY_PREFIX, DRIVE_SOURCE_PROPERTY_PREFIX, US_NAME_ALERT_PROPERTY_PREFIX].some(function (prefix) {
        return key.indexOf(prefix) === 0;
    });
}

function sourceTime(record) {
    try {
        return new Date(JSON.parse(record).date).getTime() || 0;
    } catch (e) {
        return 0;
    }
}

function driveReportFamily(folder, fileConfig, reportDate) {
    var current = [];
    var names = [];
    var latestUsVersion = null;
    var files = folder.getFiles();
    while (files.hasNext()) {
        var file = files.next();
        var name = file.getName();
        if (!isSameDriveReport(name, fileConfig, reportDate)) continue;
        names.push(name);
        if (!isVersionedDriveName(name)) current.push(file);
        else if (isUsVersionName(name) && (!latestUsVersion || versionOf(name) > versionOf(latestUsVersion.getName()))) latestUsVersion = file;
    }
    return { current: current, nextVersion: nextDriveVersion(names), latestUsVersion: latestUsVersion };
}

function isUsVersionName(name) {
    return /_us_v\d+\.csv$/i.test(name);
}

function versionOf(name) {
    return parseInt(name.match(/_v(\d+)\.csv$/i)[1], 10);
}

function isSameDriveReport(name, fileConfig, reportDate) {
    var match = name.match(fileConfig.pattern);
    return !!match && match[1] === reportDate;
}

function isVersionedDriveName(name) {
    return /_v\d+\.csv$/i.test(name);
}

function nextDriveVersion(names) {
    var next = 1;
    names.forEach(function (name) {
        var version = name.match(/_v(\d+)\.csv$/i);
        if (version) next = Math.max(next, parseInt(version[1], 10) + 1);
    });
    return next;
}

function versionedDriveName(name, version) {
    return name.replace(/\.csv$/i, "") + "_v" + version + ".csv";
}

function usCopyName(name) {
    return name.replace(/\.csv$/i, "") + "_US.csv";
}

function isUsCopyName(name) {
    return /_us\.csv$/i.test(name);
}

function convertSebCsvToUs(content) {
    return content.replace(/,/g, ".").replace(/;/g, ",");
}

function alertsSentToday(stored, today) {
    try {
        var alerted = JSON.parse(stored);
        if (alerted && alerted.day === today && Array.isArray(alerted.problems)) return alerted;
    } catch (e) {
        Logger.log("Drive alert record unreadable, starting a new one: error=" + e.message);
    }
    return { day: today, problems: [] };
}

function notifyDriveProblemOncePerDay(problem) {
    try {
        var today = Utilities.formatDate(new Date(), "Europe/Tallinn", "yyyy-MM-dd");
        var props = PropertiesService.getScriptProperties();
        var alerted = alertsSentToday(props.getProperty(DRIVE_ALERTS_PROPERTY), today);
        var key = simpleHash(problem);
        if (alerted.problems.indexOf(key) !== -1) return true;
        if (!notifySlack(":warning: " + problem + ". The Google Sheets NAV fallback reads the Drive folder.")) return false;
        alerted.problems.push(key);
        props.setProperty(DRIVE_ALERTS_PROPERTY, JSON.stringify(alerted));
        return true;
    } catch (e) {
        Logger.log("Drive alert failed: error=" + e.message + ", problem=" + problem);
        return false;
    }
}

var FUNDS_MAILBOX = "funds@tuleva.ee";

function sourceOf(message) {
    var sender = message.getFrom();
    var sentByFundsMailbox = addressOf(sender) === FUNDS_MAILBOX && wasSentByFundsMailbox(message);
    return getSourceForMessage(sender, recipientsOf(message), sentByFundsMailbox);
}

function wasSentByFundsMailbox(message) {
    var messageId = (message.getHeader("Message-ID") || "").replace(/^<|>$/g, "");
    return messageId !== "" && GmailApp.search("in:sent rfc822msgid:" + messageId).length > 0;
}

function getSourceForMessage(sender, recipients, sentByFundsMailbox) {
    if (addressOf(sender) === FUNDS_MAILBOX) {
        return isCorrectionTheFundsMailboxSentItself(recipients, sentByFundsMailbox) ? SOURCES.SEB : null;
    }
    return getSourceForSender(sender);
}

function isCorrectionTheFundsMailboxSentItself(recipients, sentByFundsMailbox) {
    return sentByFundsMailbox && isAddressedOnlyTo(recipients, FUNDS_MAILBOX);
}

function recipientsOf(message) {
    return [message.getTo(), message.getCc(), message.getBcc()].join(", ");
}

function addressOf(mailbox) {
    var bracketed = mailbox.match(/<([^>]*)>\s*$/);
    return (bracketed ? bracketed[1] : mailbox).trim().toLowerCase();
}

function isAddressedOnlyTo(recipients, address) {
    var addresses = recipients.match(/[^\s<>,;"]+@[^\s<>,;"]+/g) || [];
    return addresses.length > 0 && addresses.every(function (a) { return a.toLowerCase() === address; });
}

function getSourceForSender(sender) {
    var address = addressOf(sender);
    for (var key in SOURCES) {
        var senders = SOURCES[key].senders;
        for (var i = 0; i < senders.length; i++) {
            if (senders[i].toLowerCase() === address) {
                return SOURCES[key];
            }
        }
    }
    return null;
}

function getFileConfigAndDate(source, filename) {
    for (var i = 0; i < source.files.length; i++) {
        var fileConfig = source.files[i];
        var match = filename.match(fileConfig.pattern);
        if (match && match[1]) {
            var keyStem = fileConfig.keyFromCapture ? match[1] : formatDateFromCapture(match[1]);
            return { fileConfig: fileConfig, keyStem: keyStem };
        }
    }
    return null;
}

function formatDateFromCapture(ymd) {
    return ymd.substring(0, 4) + "-" + ymd.substring(4, 6) + "-" + ymd.substring(6, 8);
}

function isBinaryFileConfig(fileConfig) {
    return !!fileConfig.binary;
}

function resolveContentType(fileConfig) {
    return fileConfig.contentType || "text/csv";
}

function incrementUploadLabel(thread) {
    var currentCount = getUploadCount(thread);
    var newCount = currentCount + 1;
    var newLabelName = formatUploadLabelName(newCount);

    if (currentCount > 0) {
        var oldLabel = GmailApp.getUserLabelByName(formatUploadLabelName(currentCount));
        if (oldLabel) {
            thread.removeLabel(oldLabel);
        }
    }

    var newLabel = getOrCreateLabel(newLabelName);
    thread.addLabel(newLabel);
    Logger.log("Labeled thread: " + newLabelName);
}

function getUploadCount(thread) {
    var labelNames = thread.getLabels().map(function (l) { return l.getName(); });
    return parseUploadCountFromLabelNames(labelNames);
}

function getOrCreateLabel(labelName) {
    var label = GmailApp.getUserLabelByName(labelName);
    if (!label) {
        label = GmailApp.createLabel(labelName);
    }
    return label;
}

function uploadToS3(bucket, key, content, contentType) {
    var props = PropertiesService.getScriptProperties();
    var accessKey = props.getProperty("AWS_ACCESS_KEY_ID");
    var secretKey = props.getProperty("AWS_SECRET_ACCESS_KEY");
    var region = props.getProperty("AWS_REGION") || "eu-central-1";

    if (!accessKey || !secretKey) {
        throw new Error("AWS credentials not configured in Script Properties");
    }

    var host = bucket + ".s3." + region + ".amazonaws.com";
    var url = "https://" + host + "/" + key;

    var now = new Date();
    var amzDate = formatAmzDate(now);
    var dateStamp = amzDate.substring(0, 8);

    var contentBytes = Utilities.newBlob(content).getBytes();
    var payloadHash = sha256Hex(contentBytes);

    var headers = {
        "Content-Type": contentType,
        "x-amz-content-sha256": payloadHash,
        "x-amz-date": amzDate
    };

    var signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date";
    var canonicalHeaders =
        "content-type:" + contentType + "\n" +
        "host:" + host + "\n" +
        "x-amz-content-sha256:" + payloadHash + "\n" +
        "x-amz-date:" + amzDate + "\n";

    var canonicalRequest =
        "PUT\n" +
        "/" + key + "\n" +
        "\n" +
        canonicalHeaders + "\n" +
        signedHeaders + "\n" +
        payloadHash;

    var algorithm = "AWS4-HMAC-SHA256";
    var credentialScope = dateStamp + "/" + region + "/s3/aws4_request";
    var stringToSign =
        algorithm + "\n" +
        amzDate + "\n" +
        credentialScope + "\n" +
        sha256Hex(Utilities.newBlob(canonicalRequest).getBytes());

    var signingKey = getSignatureKey(secretKey, dateStamp, region, "s3");
    var signature = hmacSha256Hex(signingKey, stringToSign);

    var authorizationHeader =
        algorithm + " " +
        "Credential=" + accessKey + "/" + credentialScope + ", " +
        "SignedHeaders=" + signedHeaders + ", " +
        "Signature=" + signature;

    headers["Authorization"] = authorizationHeader;

    var options = {
        "method": "PUT",
        "headers": headers,
        "payload": contentBytes,
        "muteHttpExceptions": true
    };

    var response = UrlFetchApp.fetch(url, options);
    var responseCode = response.getResponseCode();

    if (responseCode !== 200) {
        throw new Error("S3 upload failed: " + responseCode + " - " + response.getContentText());
    }

    return response;
}

function formatAmzDate(date) {
    return Utilities.formatDate(date, "UTC", "yyyyMMdd'T'HHmmss'Z'");
}

function sha256Hex(data) {
    var hash = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, data);
    return bytesToHex(hash);
}

function hmacSha256(key, data) {
    var dataBytes = (typeof data === 'string') ? Utilities.newBlob(data).getBytes() : data;
    var keyBytes = (typeof key === 'string') ? Utilities.newBlob(key).getBytes() : key;
    return Utilities.computeHmacSignature(Utilities.MacAlgorithm.HMAC_SHA_256, dataBytes, keyBytes);
}

function hmacSha256Hex(key, data) {
    var signature = hmacSha256(key, data);
    return bytesToHex(signature);
}

function getSignatureKey(key, dateStamp, region, service) {
    var kDate = hmacSha256("AWS4" + key, dateStamp);
    var kRegion = hmacSha256(kDate, region);
    var kService = hmacSha256(kRegion, service);
    var kSigning = hmacSha256(kService, "aws4_request");
    return kSigning;
}

function bytesToHex(bytes) {
    return bytes.map(function(byte) {
        return ("0" + (byte & 0xFF).toString(16)).slice(-2);
    }).join("");
}

function testUpload() {
    var testContent = "ReportDate;NAVDate;Portfolio\n06.01.2026;05.01.2026;Test";

    uploadToS3(S3_BUCKET, "seb/test_positions.csv", testContent, "text/csv");
    Logger.log("Test upload successful: s3://" + S3_BUCKET + "/seb/test_positions.csv");

    uploadToS3(S3_BUCKET, "seb/test_pending_transactions.csv", testContent, "text/csv");
    Logger.log("Test upload successful: s3://" + S3_BUCKET + "/seb/test_pending_transactions.csv");

    Logger.log("All test uploads successful!");
}

function dryRun() {
    var searchQuery = gmailSearchQuery(7);
    Logger.log("DRY RUN - Searching with query: " + searchQuery);

    var threads = GmailApp.search(searchQuery);
    Logger.log("Found " + threads.length + " email threads");

    for (var i = 0; i < threads.length; i++) {
        dryRunThread(threads[i]);
    }

    Logger.log("DRY RUN complete");
}

function dryRunThread(thread) {
    var currentLabel = getUploadCount(thread);
    Logger.log("Thread: " + thread.getFirstMessageSubject() + " (current label: " + currentLabel + "x)");

    var messages = thread.getMessages();
    for (var j = 0; j < messages.length; j++) {
        var message = messages[j];
        var sender = message.getFrom();
        var source = sourceOf(message);

        Logger.log("  Message from: " + sender + " at " + message.getDate());

        if (!source) {
            Logger.log("    -> Would SKIP (not from known sender)");
            continue;
        }

        var attachments = message.getAttachments();
        for (var k = 0; k < attachments.length; k++) {
            var filename = attachments[k].getName();
            var result = getFileConfigAndDate(source, filename);
            if (result) {
                var s3Key = result.fileConfig.s3Prefix + result.keyStem + result.fileConfig.s3Suffix;
                Logger.log("    -> Would UPLOAD: " + filename + " to s3://" + S3_BUCKET + "/" + s3Key);
            } else {
                Logger.log("    -> Would SKIP: Unknown file pattern: " + filename);
            }
        }
    }
}

function removeAllUploadLabels() {
    for (var count = 1; count <= 10; count++) {
        var labelName = formatUploadLabelName(count);
        var label = GmailApp.getUserLabelByName(labelName);
        if (label) {
            var threads = label.getThreads();
            Logger.log("Removing label " + labelName + " from " + threads.length + " threads");
            for (var i = 0; i < threads.length; i++) {
                threads[i].removeLabel(label);
            }
        }
    }
    Logger.log("All upload labels removed");
}

// Node.js compatibility shim — invisible to the Apps Script V8 runtime; lets Jest
// require this file for unit tests with no build step.
if (typeof module !== "undefined" && module.exports) {
    module.exports = {
        SOURCES,
        getSourceForSender,
        getSourceForMessage,
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
        isBinaryFileConfig,
        resolveContentType,
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
    };
}
