package com.pdftron.scanner

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
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


class MainActivity : AppCompatActivity() {

    private lateinit var button: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

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

    fun androidRectToPdfRect(
        androidRect: android.graphics.Rect,
        ratio: Double,
        imgHeight: Double
    ): Rect {
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
                val image = InputImage.fromFilePath(this, Uri.fromFile(localJpeg))

                showProgress()

                // Process image using ML Kit
                processOCR(imgWidth, imgHeight, image, localJpeg)
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

    private fun processOCR(
        imgWidth: Double,
        imgHeight: Double,
        image: InputImage,
        localJpeg: File
    ) {
        val result = TextRecognition.getClient().process(image)
            .addOnSuccessListener { visionText ->

                // Create the PDF containing the recognized text
                val outputPath = createPDF(imgWidth, imgHeight, localJpeg, visionText)

                // Open the document in the viewer
                val config =
                    ViewerConfig.Builder().openUrlCachePath(cacheDir.absolutePath).build()
                DocumentActivity.openDocument(
                    this@MainActivity,
                    Uri.fromFile(outputPath),
                    config
                )
            }
            .addOnFailureListener { e ->
                hideProgress()
                Toast.makeText(this, "Could not recognize text", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createPDF(
        imgWidth: Double,
        imgHeight: Double,
        localJpeg: File,
        visionText: com.google.mlkit.vision.text.Text
    ): File {

        val doc = PDFDoc()
        val outputFile = File(
            this.filesDir, com.pdftron.pdf.utils.Utils.getFileNameNotInUse(
                "scanned_doc_output.pdf"
            )
        )

        // First convert the image to a PDF Doc
        Convert.toPdf(doc, localJpeg.absolutePath)

        val page = doc.getPage(1) // currently this sample only supports 1 page
        val ratio = page.pageWidth / imgWidth;

        // We will need to generate a JSON containing the text data, which will be used
        // to insert the text information into the PDF document
        val jsonWords = JSONArray()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val elementText = element.text
                    val elementFrame = element.boundingBox

                    val pdfRect =
                        androidRectToPdfRect(elementFrame!!, ratio, imgHeight)
                    pdfRect.normalize()

                    val word = JSONObject()
                    word.put("font-size", (pdfRect.y2 - pdfRect.y1).toInt())
                    word.put("length", (pdfRect.x2 - pdfRect.x1).toInt())
                    word.put("text", elementText)
                    word.put("orientation", "U")
                    word.put("x", pdfRect.x1.toInt())
                    word.put("y", pdfRect.y1.toInt())
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
        jsonPage.put("origin", "BottomLeft")

        jsonPages.put(jsonPage)
        jsonObj.put("Page", jsonPages)

        // Insert the text into the document
        OCRModule.applyOCRJsonToPDF(doc, jsonObj.toString());
        doc.save(outputFile.absolutePath, SDFDoc.SaveMode.LINEARIZED, null)
        return outputFile
    }

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

}