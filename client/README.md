# Scanner OCR Android App
## Prerequisites
- Setup the OCR Firebase Cloud Function here: https://github.com/PDFTron/pdftron-android-ocr-scanner-sample/tree/master/server

## Setup
1. Go to your Firebase console and obtain a `google-services.json` file as described in [this guide](https://support.google.com/firebase/answer/7015592?hl=en).
2. Place your `google-services.json` file into the app folder.
3. In the MainActivity.kt file, update the values for `bucket` and `cloudFunction` to point to your Firebase storage bucket and cloud function URL (which will look something like `http://localhost:5001/<YOUR-PROJECT-NAME>/us-central1/app/ocr`
4. Import the `client` folder into Android Studio.
5. Run the sample.
