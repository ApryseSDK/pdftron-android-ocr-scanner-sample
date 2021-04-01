package com.pdftron.scanner

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.FirebaseApp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.pdftron.pdf.*
import com.pdftron.pdf.config.ViewerConfig
import com.pdftron.pdf.controls.DocumentActivity
import com.pdftron.sdf.SDFDoc
import com.scanlibrary.ScanConstants
import com.scanlibrary.ScannerContract
import com.scanlibrary.Utils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity() {

    private lateinit var button: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    private val bucket = "FIREBASE_STORAGE_BUCKET"
    private val cloudFunctionUrl: String = "CLOUD_FUNCTION_URL"

//    private val storage: FirebaseStorage = FirebaseStorage.getInstance(bucket)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        for (hasPermission in permissions.values) {
            if (!hasPermission) {
                Toast.makeText(this, "Missing Required Permissions", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    fun androidRectToPdfRect(androidRect: android.graphics.Rect, ratio: Double, imgHeight: Double): Rect {
        return Rect(
            androidRect.left.toDouble() * ratio,
            (imgHeight - androidRect.bottom.toDouble()) * ratio,
            androidRect.right.toDouble() * ratio,
            (imgHeight - androidRect.top.toDouble()) * ratio
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FirebaseApp.initializeApp(this)

        // Add callback to handle returned image from scanner
        val scannerLauncher = registerForActivityResult(ScannerContract()) { uri ->
            if (uri != null) {
                // Obtain the bitmap and save as a local image file
                var bitmap: Bitmap? = null
                bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                val imgHeight = bitmap.height.toDouble()
                val imgWidth = bitmap.width.toDouble()
                contentResolver.delete(uri!!, null, null)

                // Save bitmap to local cache as image then upload for processing
                val localJpeg = Utils.saveBitmapAsJpeg(bitmap, filesDir)

                // Process image on server
//                uploadFile(localJpeg)
                val recognizer = TextRecognition.getClient()
                val image = InputImage.fromFilePath(this, Uri.fromFile(localJpeg))
                Log.d("ScannerSample", "Path is = " + localJpeg.absolutePath)
                val result = recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        Log.d("ScannerSample", "Failed")
                        val doc = PDFDoc()
                        val outputPath = File(
                            this.filesDir, com.pdftron.pdf.utils.Utils.getFileNameNotInUse(
                                "scanned_doc.pdf"
                            )
                        )
                        Convert.toPdf(doc, localJpeg.absolutePath)

                        val page = doc.getPage(1)

                        val ratioWidth = page.pageWidth / imgWidth;
                        val ratioHeight = page.pageHeight / imgHeight;

                        val resultText = visionText.text

                        val jsonWords = JSONArray()


                        for (block in visionText.textBlocks) {
                            val blockText = block.text
                            val blockCornerPoints = block.cornerPoints
                            val blockFrame = block.boundingBox
                            val pdfRect = androidRectToPdfRect(blockFrame!!, ratioWidth, imgHeight)

//                            val sq1 = Square.create(doc, pdfRect)
//                            sq1.setColor(ColorPt(0.0, 1.0, 0.0), 3)
//                            sq1.refreshAppearance()
//                            page.annotPushBack(sq1)

                            val jsonLines = JSONArray()
                            for (line in block.lines) {
                                val lineText = line.text
                                val lineCornerPoints = line.cornerPoints
                                val lineFrame = line.boundingBox
                                val pdfRect = androidRectToPdfRect(lineFrame!!, ratioWidth, imgHeight)

//                                val sq2 = Square.create(doc, pdfRect)
//                                sq2.setColor(ColorPt(0.0, 0.0, 1.0), 3)
//                                sq2.refreshAppearance()
//                                page.annotPushBack(sq2)

                                for (element in line.elements) {
                                    val elementText = element.text
                                    val elementCornerPoints = element.cornerPoints
                                    val elementFrame = element.boundingBox

                                    val pdfRect = androidRectToPdfRect(elementFrame!!, ratioWidth, imgHeight)

//                                    val sq3 = Square.create(doc, pdfRect)
//                                    sq3.setColor(ColorPt(0.0, 0.0, 0.0), 3)
//                                    sq3.refreshAppearance()
//                                    page.annotPushBack(sq3)

                                    val word = JSONObject()
                                    word.put("font-size", (pdfRect.y2 - pdfRect.y1))
                                    word.put("length", (pdfRect.x2 - pdfRect.x1))
                                    word.put("text", elementText)
                                    word.put("x", pdfRect.x1)
                                    word.put("y", pdfRect.y1)
                                    jsonWords.put(word)
                                }
                            }
                        }

                        val jsonObj = JSONObject()
                        val jsonPages = JSONArray()

                        val jsonPage = JSONObject()
                        jsonPage.put("Word", jsonWords)
                        jsonPage.put("num", 1) // Only supports one page
                        jsonPage.put("dpi", 96)
                        jsonPage.put("origin", "BottomLeft'")

                        jsonPages.put(jsonPage)
                        jsonObj.put("Page", jsonPages)

                        OCRModule.applyOCRJsonToPDF(doc, jsonObj.toString());

                        doc.save(outputPath.absolutePath, SDFDoc.SaveMode.INCREMENTAL, null)

                        val config = ViewerConfig.Builder().openUrlCachePath(cacheDir.absolutePath).build()
                        DocumentActivity.openDocument(
                            this@MainActivity,
                            Uri.fromFile(outputPath),
                            config
                        )
                    }
                    .addOnFailureListener { e ->
                        Log.d("ScannerSample", "Failed")
                    }

                // Show progress UI
//                showProgress()
            }
        }

        button = findViewById(R.id.button)
        progressBar = findViewById(R.id.loading)
        progressText = findViewById(R.id.progress_text)
        button.setOnClickListener {
            // Launch the scanner activity
            scannerLauncher.launch(ScanConstants.OPEN_CAMERA)
        }

        // Check for permission before proceeding
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET
            )
        )
    }

//    private fun uploadFile(localFile: File) {
//        val reference = storage.reference
//        val fileName = localFile.name
//        val fileReference = reference.child(fileName)
//        val uploadTask = fileReference.putFile(Uri.fromFile(localFile))
//        // Register observers to listen for when the download is done or if it fails
//        uploadTask.addOnSuccessListener {
//            // If successful, we run our cloud function with the given file
//            runCloudFunction(fileName)
//        }
//    }

//    private fun runCloudFunction(fileName: String) {
//        // Call cloud function using HTTP request using OkHttp and RxJava
//        Single.create<String> {
//            // Create HTTP request to trigger cloud function
//            val httpBuilder = cloudFunctionUrl.toHttpUrlOrNull()!!.newBuilder()
//                .addQueryParameter("file", fileName)
//            val request = Request.Builder().url(httpBuilder.build()).build()
//            val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS)
//                .writeTimeout(60, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()
//            val response = client.newCall(request).execute()
//            if (response.isSuccessful) {
//                it.onSuccess(response.body!!.string())
//            } else {
//                it.onError(IOException(response.message))
//            }
//        }.apply {
//            subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe { it ->
//                    // If processing is successful, download processed file from Firebase Storage
//                    downloadStorageFile(it.replace("\"", "")) // trim result
//
//                    // Optionally, delete uploaded file from Firebase Storage
//                    deleteStorageFile(fileName)
//                }
//        }
//    }
//
//    private fun downloadStorageFile(fileName: String) {
//        val reference = storage.reference
//        val fileReference = reference.child(fileName)
//        val localFile = File(cacheDir, fileName)
//
//        fileReference.getFile(localFile).addOnSuccessListener {
//            // Hide progress bar
//            hideProgress()
//
//            // Open processed document in PDF viewer
//            val config = ViewerConfig.Builder().openUrlCachePath(cacheDir.absolutePath).build()
//            DocumentActivity.openDocument(this@MainActivity, Uri.fromFile(localFile), config)
//
//            // Optionally, delete processed file on Firebase Storage
//            deleteStorageFile(fileName)
//        }
//    }

//    private fun deleteStorageFile(fileName: String) {
//        val reference = storage.reference
//        val fileReference = reference.child(fileName)
//        fileReference.delete()
//    }

    private fun showProgress() {
        progressBar.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        button.visibility = View.GONE
    }

    private fun hideProgress() {
        progressBar.visibility = View.GONE
        progressText.visibility = View.GONE
        button.visibility = View.VISIBLE
    }

    private fun saveBitmapAsJpeg(bitmap: Bitmap, folder: File): File {
        val imageFile = File(folder, File.createTempFile("image", ".jpg").name)

        val os = FileOutputStream(imageFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
        os.flush()
        os.close()

        return imageFile
    }
}