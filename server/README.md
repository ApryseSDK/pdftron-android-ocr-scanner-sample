# OCR Firebase Cloud function

## Setup
1. Create a `public` folder inside the `functions` folder

    For testing:
    - Download the PDFTron SDK that matches your test environment. For example, download the Mac version of the [PDFTron SDK](https://www.pdftron.com/documentation/linux/download/mac/) on a Mac
    - Place the `OCRModule` file from the download package inside the `public` folder

    For production:
    - Download the Linux version of the [PDFTron SDK](https://www.pdftron.com/documentation/linux/download/linux/)
    - Place the `OCRModule` file from the download package inside the `public` folder

2. If you have not yet created a Firebase project, go ahead and create one as described in step 1 of the [Official Guide](https://firebase.google.com/docs/functions/get-started#create-a-firebase-project)

3. Obtain a `serviceAccountKey.json` file by following step 2-3 of the [Official Guide](https://firebase.google.com/docs/functions/get-started#set-up-node.js-and-the-firebase-cli)

4. Place `serviceAccountKey.json` file inside the `functions` folder

5. Open `functions/index.js` file and modify the following project config to your own Firebase project:

```js
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: "<YOUR-DB-LINK-GOES-HERE>",
  storageBucket: "<YOUR-BUCKET-GOES-HERE>"
});
```

6. Run the following to test the REST call

```
firebase emulators:start
```

Usually the URL will look something like `http://localhost:5001/<YOUR-PROJECT-NAME>/us-central1/app/ocr?file=Preprocess.png`, the actual localhost port will be shown in your console output.

7. Deploy

```
firebase deploy --only functions
```
