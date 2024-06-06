// Configuration for AWS access
const AWS_CONFIG = {
  accessKey: 'KEY',
  secretKey: 'SECRET',
  region: 'REGION'
};

// Initialize AWS configuration
function initAWS() {
  AWSLIB.initConfig(AWS_CONFIG);
}

// Function to upload email attachments to S3
async function uploadEmailAttachmentsToS3() {
  initAWS();

  const bucketName = 'analytics-administration-data';

  var s3 = new AWSLIB.AWS.S3({
    apiVersion: '2006-03-01',
    params: { Bucket: bucketName }
  });

  var query = 'has:attachment newer_than:1d';
  var threads = GmailApp.search(query);

  for (const thread of threads) {
    var messages = thread.getMessages();
    for (const message of messages) {
      var attachments = message.getAttachments();
      for (const attachment of attachments) {
        var attachmentNameLowerCase = attachment.getName().toLowerCase();
        if (attachmentNameLowerCase.startsWith('tuleva_portfolio') && attachmentNameLowerCase.endsWith('.csv')) {
          try {
            const byteArray = attachment.getBytes();
            const typedArray = new Uint8Array(byteArray); // Convert byte array to Uint8Array because we polyfilled the AwsSdk Blob implementation to use Unit8Array

            const emailDate = message.getDate();
            const formattedDate = Utilities.formatDate(emailDate, Session.getScriptTimeZone(), 'yyyy-MM-dd');
            const fileName = `portfolio/${formattedDate}.csv`;

            const params = {
              Bucket: bucketName,
              Key: fileName,
              Body: typedArray,
              ContentType: attachment.getContentType()
            };

            // Asynchronously upload each attachment
            let uploadResult = await new Promise((resolve, reject) => {
              s3.putObject(params, function(err, data) {
                if (err) {
                  console.error('Failed to upload ' + attachment.getName() + ' due to: ' + err.toString());
                  reject(err);
                } else {
                  console.log('Upload successful for: ' + attachment.getName() + ' as ' + fileName);
                  resolve(data);
                }
              });
            });
          } catch (e) {
            console.error('Failed to upload ' + attachment.getName() + ' due to: ' + e.toString());
          }
        }
      }
    }
  }
}

// Setup a frequent trigger to run the upload function
function setupFrequentTrigger() {
  ScriptApp.newTrigger('uploadEmailAttachmentsToS3')
    .timeBased()
    .everyHours(1)
    .create();
}

