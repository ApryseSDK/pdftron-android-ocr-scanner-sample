const express = require('express')
const bodyParser = require('body-parser');
const cors = require('cors');

const fs = require('fs');
const path = require('path');
const os = require('os');

const uuid = require('uuid');

const app = express();

const admin = require("firebase-admin");

const functions = require('firebase-functions');

const serviceAccount = require("./serviceAccountKey.json");

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: "https://pdftron-mobile-ocr.firebaseio.com",
  storageBucket: "pdftron-mobile-ocr.appspot.com"
});

const bucket = admin.storage().bucket();

const { PDFNet } = require('@pdftron/pdfnet-node');

app.use(cors());

app.use(bodyParser.urlencoded({ extended: false }));
app.use(bodyParser.json());

app.get('/ocr', async (req, res) => {

    const inputPath = req.query.file;
    console.log('input', req.query);

    const file = await downloadFile(inputPath);
    console.log('file:', file);

    const outputFile = await applyOCR(file);
    console.log('output file:', outputFile);

    const uploadedPath = await uploadFile(outputFile);
    console.log('done upload', uploadedPath);

    fs.unlinkSync(file); // clean up input file

    res.json(uploadedPath);
});

const uploadFile = async (filePath) => {
  const basename = path.basename(filePath);
  await bucket.upload(filePath, {
    destination: basename,
  });
  fs.unlinkSync(filePath)
  return basename;
};

const downloadFile = async (filePath) => {
  const tempFilePath = path.join(os.tmpdir(), filePath);
  await bucket.file(filePath).download({destination: tempFilePath});
  console.log('Image downloaded locally to', tempFilePath);
  return tempFilePath;
};

const applyOCR = async (file) => {
    await PDFNet.initialize();

    const ver = await PDFNet.getVersion();
    console.log("ver: ", ver);

    await PDFNet.addResourceSearchPath('public');

    const hasModule = await PDFNet.OCRModule.isModuleAvailable();
    console.log('isModuleAvailable', hasModule);

    if (hasModule) {
        // Setup empty destination doc
        const doc = await PDFNet.PDFDoc.create();
        const image_path = file;

        console.log('before imageToPDF');
        await PDFNet.OCRModule.imageToPDF(doc, image_path);
        console.log('after imageToPDF');

        // save
        const outputPath = 'output-' + uuid.v4() + '.pdf';
        const tempOutPath = path.join(os.tmpdir(), outputPath);
        console.log('save to', tempOutPath);
        await doc.save(tempOutPath, PDFNet.SDFDoc.SaveOptions.e_linearized);
        console.log('done save to', tempOutPath);
        PDFNet.shutdown();
        return tempOutPath;
    }
    PDFNet.shutdown();
    return null;
};

exports.app = functions.https.onRequest(app);