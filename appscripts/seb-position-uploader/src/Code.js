// Investment CSV to S3
//
// Polls the funds@tuleva.ee Gmail mailbox for fund position-report attachments
// and uploads them to the tuleva-investment-reports S3 bucket. The Spring Boot
// ReportImportJob in TulevaEE/onboarding-service then pulls those files and
// ingests them into investment_report and investment_fund_position.
//
// Required Apps Script Properties (Project Settings > Script properties):
//   AWS_ACCESS_KEY_ID
//   AWS_SECRET_ACCESS_KEY
//   AWS_REGION (defaults to eu-central-1)
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
                s3Suffix: "_positions.csv"
            },
            {
                pattern: /^TULEVA_ootel_tehingud_(\d{8})(?:[ _().\-].*)?\.csv$/i,
                s3Prefix: "seb/",
                s3Suffix: "_pending_transactions.csv"
            }
        ]
    }
};

function processNewEmails() {
    processEmailsFromDays(2);
}

function processHistoricalEmails() {
    processEmailsFromDays(7);
}

function processHistoricalEmails30Days() {
    processEmailsFromDays(30);
}

function processEmailsFromDays(days, options) {
    options = options || {};
    var minMessageDate = options.minMessageDate || null;

    var searchQuery = gmailSearchQuery(days);
    Logger.log("Searching with query: " + searchQuery + (minMessageDate ? ", minMessageDate=" + minMessageDate.toISOString() : ""));

    var threads = GmailApp.search(searchQuery);
    Logger.log("Found " + threads.length + " email threads");

    // Process oldest threads first so the newest thread's upload wins on S3.
    for (var i = threads.length - 1; i >= 0; i--) {
        processThread(threads[i], minMessageDate);
    }

    Logger.log("Processing complete");
}

function gmailSearchQuery(days) {
    return "to:funds@tuleva.ee has:attachment newer_than:" + days + "d";
}

function processThread(thread, minMessageDate) {
    var messages = thread.getMessages();
    var uploadCount = getUploadCount(thread);
    var matchingCount = 0;
    for (var j = 0; j < messages.length; j++) {
        var message = messages[j];
        if (!isMessageWithinWindow(message.getDate(), minMessageDate)) continue;
        if (messageHasMatchingAttachments(message)) {
            matchingCount++;
            if (shouldProcessMessage(matchingCount, uploadCount)) {
                processMessage(message, thread);
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

function messageHasMatchingAttachments(message) {
    var sender = message.getFrom();
    var source = getSourceForSender(sender);
    if (!source) return false;

    var attachments = message.getAttachments();
    for (var k = 0; k < attachments.length; k++) {
        if (getFileConfigAndDate(source, attachments[k].getName())) {
            return true;
        }
    }
    return false;
}

function processMessage(message, thread) {
    var sender = message.getFrom();
    var source = getSourceForSender(sender);

    if (!source) {
        return;
    }

    var attachments = message.getAttachments();
    var uploadedCount = 0;

    for (var k = 0; k < attachments.length; k++) {
        var attachment = attachments[k];
        var filename = attachment.getName();

        var result = getFileConfigAndDate(source, filename);
        if (result) {
            var s3Key = result.fileConfig.s3Prefix + result.reportDate + result.fileConfig.s3Suffix;
            var content = attachment.getDataAsString();

            try {
                uploadToS3(S3_BUCKET, s3Key, content, "text/csv");
                Logger.log("SUCCESS: Uploaded " + filename + " -> s3://" + S3_BUCKET + "/" + s3Key);
                uploadedCount++;
            } catch (e) {
                Logger.log("FAILED: " + filename + " -> " + e.message);
                throw e;
            }
        }
    }

    if (uploadedCount > 0) {
        incrementUploadLabel(thread);
    }
}

function getSourceForSender(sender) {
    for (var key in SOURCES) {
        var senders = SOURCES[key].senders;
        for (var i = 0; i < senders.length; i++) {
            if (sender.indexOf(senders[i]) !== -1) {
                return SOURCES[key];
            }
        }
    }
    return null;
}

function getFileConfigAndDate(source, filename) {
    for (var i = 0; i < source.files.length; i++) {
        var match = filename.match(source.files[i].pattern);
        if (match && match[1]) {
            var ymd = match[1];
            var reportDate = ymd.substring(0, 4) + "-" + ymd.substring(4, 6) + "-" + ymd.substring(6, 8);
            return { fileConfig: source.files[i], reportDate: reportDate };
        }
    }
    return null;
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
        var source = getSourceForSender(sender);

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
                var s3Key = result.fileConfig.s3Prefix + result.reportDate + result.fileConfig.s3Suffix;
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
        getFileConfigAndDate,
        isMessageWithinWindow,
        shouldProcessMessage,
        parseUploadCountFromLabelNames,
        formatUploadLabelName,
    };
}
