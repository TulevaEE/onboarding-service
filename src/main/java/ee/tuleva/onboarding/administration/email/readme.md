`appscript.gs` runs on Google App Script environment.
It reads email reports with Tuleva Portfolio update data and uploads the report CSV files to S3.
Uploads are done into `portfolio` directory as `{date}.csv`, where date represents the date of the report.
Java code then uses the same date to create a row into the database.

`appscript.gs` uses modified AWS JavaScript SDK to make S3 requests.
https://github.com/dxdc/aws-sdk-google-apps

https://developers.google.com/apps-script/guides/libraries#add_a_library_to_your_script_project

`appscript.gs` converts byte array to Uint8Array, because we polyfilled the AwsSdk Blob implementation to use Unit8Array, since App Script does not directly use `Blob`.

To deploy the functionality again, deploy `appscript.gs` with a timer and 
make a copy from AWS SDK and upgrade it's polyfill.gs to be able to handle `Blob`.
App Script environment does not have direct access to `Blob`, but through Utilities.

1. Add the existing Google Apps Script project as a Library
2. Script ID `1J6iN9mJE-NK6LGTlZcngsflJEx59tE3ZOW4-2cdHbgw0So2MmEcRZxKG`
3. Choose an identifier `AWSLIB`
4. Set up `AWS_CONFIG` variables
