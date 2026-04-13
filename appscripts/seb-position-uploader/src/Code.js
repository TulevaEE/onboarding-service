/**
 * Google Apps Script to upload fund position CSV reports to S3.
 *
 * Supports two sources:
 * 1. Swedbank (fundadmin@swedbank.ee) - TULEVA_PORTFOLIO files -> portfolio/
 * 2. SEB (trustee@seb.ee, plus one named internal forwarder) - TULEVA_pos_raport and TULEVA_ootel_tehingud files -> seb/
 *
 * Setup:
 * 1. Create a new Google Apps Script project at script.google.com
 * 2. Copy this code into the script editor
 * 3. Add Script Properties (Project Settings > Script Properties):
 *    - AWS_ACCESS_KEY_ID: (your access key)
 *    - AWS_SECRET_ACCESS_KEY: (your secret key)
 *    - AWS_REGION: eu-central-1
 * 4. Set up a time-driven trigger to run processNewEmails() every 5 minutes
 *
 * Testing:
 * 1. Run testUpload() to verify S3 credentials work
 * 2. Run dryRun() to see what would be uploaded without making changes
 * 3. Run processHistoricalEmails() to process last 7 days (one-time catch-up)
 * 4. Run processNewEmails() for regular processing (last 2 days)
 *
 * S3 structure:
 * - portfolio/2026-01-23.csv          (Swedbank positions)
 * - seb/2026-01-23_positions.csv      (SEB positions)
 * - seb/2026-01-23_pending_transactions.csv   (SEB pending transactions)
 *
 * Labels are added to track uploads: "S3-Uploaded-1x", "S3-Uploaded-2x", etc.
 * If you see "S3-Uploaded-2x" or higher, it means a correction was received.
 */

var S3_BUCKET = "tuleva-investment-reports";
var UPLOAD_LABEL_PREFIX = "S3-Uploaded-";

// Source configurations. Each pattern captures an 8-digit YYYYMMDD as match[1]
// so getFileConfigAndDate can parse the report date from the filename without
// a second loose regex.
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

/**
 * Process emails from last 2 days (for scheduled runs every 5 minutes)
 */
function processNewEmails() {
    processEmailsFromDays(2);
}

/**
 * Process emails from last 7 days (for historical catch-up)
 * Run this manually once to upload historical files
 */
function processHistoricalEmails() {
    processEmailsFromDays(7);
}

/**
 * Process emails from last 30 days (for extended catch-up)
 */
function processHistoricalEmails30Days() {
    processEmailsFromDays(30);
}

function processEmailsFromDays(days, options) {
    options = options || {};
    var minMessageDate = options.minMessageDate || null;

    var searchQuery = "to:funds@tuleva.ee has:attachment newer_than:" + days + "d";
    Logger.log("Searching with query: " + searchQuery + (minMessageDate ? ", minMessageDate=" + minMessageDate.toISOString() : ""));

    var threads = GmailApp.search(searchQuery);
    Logger.log("Found " + threads.length + " email threads");

    // Iterate oldest-first so the newest thread's upload wins on S3
    for (var i = threads.length - 1; i >= 0; i--) {
        var thread = threads[i];
        var messages = thread.getMessages();
        var uploadCount = getUploadCount(thread);
        var matchingCount = 0;

        for (var j = 0; j < messages.length; j++) {
            var message = messages[j];
            // Per-message Date-header guard. GmailApp.search returns whole
            // threads; without this check, an old message in a recently-replied
            // thread could be re-processed by a tightly-scoped recovery run.
            if (!isMessageWithinWindow(message.getDate(), minMessageDate)) continue;
            if (messageHasMatchingAttachments(message)) {
                matchingCount++;
                if (matchingCount > uploadCount) {
                    processMessage(message, thread);
                }
            }
        }
    }

    Logger.log("Processing complete");
}

// PURE — directly testable with literal Date inputs, no Gmail mocking.
// Returns true if the message Date is on or after the cutoff (boundary inclusive),
// or true unconditionally when no cutoff is configured.
function isMessageWithinWindow(messageDate, minMessageDate) {
    if (!minMessageDate) return true;
    return messageDate.getTime() >= minMessageDate.getTime();
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

// PURE — replaces the old getFileConfig + extractDateFromFilename two-step.
// Returns { fileConfig, reportDate } using the captured 8-digit date group,
// or null if no source file pattern matches. Tying date extraction to the
// matching pattern eliminates the risk of a second loose regex picking up
// digits from elsewhere in the filename.
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

    // Remove old label if exists
    if (currentCount > 0) {
        var oldLabel = GmailApp.getUserLabelByName(UPLOAD_LABEL_PREFIX + currentCount + "x");
        if (oldLabel) {
            thread.removeLabel(oldLabel);
        }
    }

    // Add new label
    var newLabel = getOrCreateLabel(UPLOAD_LABEL_PREFIX + newCount + "x");
    thread.addLabel(newLabel);
    Logger.log("Labeled thread: " + UPLOAD_LABEL_PREFIX + newCount + "x");
}

function getUploadCount(thread) {
    var labels = thread.getLabels();
    for (var i = 0; i < labels.length; i++) {
        var labelName = labels[i].getName();
        if (labelName.indexOf(UPLOAD_LABEL_PREFIX) === 0) {
            var countStr = labelName.replace(UPLOAD_LABEL_PREFIX, "").replace("x", "");
            return parseInt(countStr, 10) || 0;
        }
    }
    return 0;
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

// ============================================================================
// TEST FUNCTIONS
// ============================================================================

/**
 * Test S3 upload credentials - uploads test files to verify AWS credentials.
 * Run this first to verify AWS credentials are configured correctly.
 */
function testUpload() {
    var testContent = "ReportDate;NAVDate;Portfolio\n06.01.2026;05.01.2026;Test";

    uploadToS3(S3_BUCKET, "seb/test_positions.csv", testContent, "text/csv");
    Logger.log("Test upload successful: s3://" + S3_BUCKET + "/seb/test_positions.csv");

    uploadToS3(S3_BUCKET, "seb/test_pending_transactions.csv", testContent, "text/csv");
    Logger.log("Test upload successful: s3://" + S3_BUCKET + "/seb/test_pending_transactions.csv");

    Logger.log("All test uploads successful!");
}

/**
 * Dry run - shows what would be uploaded without actually uploading
 * Useful for testing the email search and parsing logic
 */
function dryRun() {
    var days = 7;
    var searchQuery = "to:funds@tuleva.ee has:attachment newer_than:" + days + "d";
    Logger.log("DRY RUN - Searching with query: " + searchQuery);

    var threads = GmailApp.search(searchQuery);
    Logger.log("Found " + threads.length + " email threads");

    for (var i = 0; i < threads.length; i++) {
        var thread = threads[i];
        var messages = thread.getMessages();
        var currentLabel = getUploadCount(thread);

        Logger.log("Thread: " + thread.getFirstMessageSubject() + " (current label: " + currentLabel + "x)");

        for (var j = 0; j < messages.length; j++) {
            var message = messages[j];
            var sender = message.getFrom();
            var date = message.getDate();
            var source = getSourceForSender(sender);

            Logger.log("  Message from: " + sender + " at " + date);

            if (!source) {
                Logger.log("    -> Would SKIP (not from known sender)");
                continue;
            }

            Logger.log("    -> Source: " + (sender.indexOf("swedbank") !== -1 ? "SWEDBANK" : "SEB"));

            var attachments = message.getAttachments();
            for (var k = 0; k < attachments.length; k++) {
                var attachment = attachments[k];
                var filename = attachment.getName();

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

    Logger.log("DRY RUN complete");
}

/**
 * Clean up test files from S3
 */
function cleanupTestFiles() {
    Logger.log("To delete test files, run:");
    Logger.log("  aws s3 rm s3://" + S3_BUCKET + "/portfolio/test.csv");
    Logger.log("  aws s3 rm s3://" + S3_BUCKET + "/seb/test_positions.csv");
    Logger.log("  aws s3 rm s3://" + S3_BUCKET + "/seb/test_pending_transactions.csv");
}

/**
 * Remove all S3-Uploaded labels from threads (for re-processing)
 * Use with caution - this will cause all emails to be re-uploaded on next run
 */
function removeAllUploadLabels() {
    for (var count = 1; count <= 10; count++) {
        var labelName = UPLOAD_LABEL_PREFIX + count + "x";
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

// ============================================================================
// Node.js compatibility shim — invisible to the V8 Apps Script runtime.
// Lets Jest require this file and import pure helpers for unit testing
// without any build step. The `if (typeof module !== "undefined")` block
// is a no-op when this file runs in Apps Script.
// ============================================================================
if (typeof module !== "undefined" && module.exports) {
    module.exports = {
        SOURCES,
        getSourceForSender,
        getFileConfigAndDate,
        isMessageWithinWindow,
    };
}
