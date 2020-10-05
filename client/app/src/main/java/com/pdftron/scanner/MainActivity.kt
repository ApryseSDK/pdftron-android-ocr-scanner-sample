package com.pdftron.scanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import com.pdftron.pdf.config.ViewerConfig
import com.pdftron.pdf.controls.DocumentActivity
import com.scanlibrary.ScanActivity
import com.scanlibrary.ScanConstants
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var button: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    private lateinit var mScannerHelper: ScannerHelper
    private lateinit var mOCRHelper: OCRHelper

    private val bucket = "FIREBASE_STORAGE_BUCKET"
    private val cloudFunctionUrl = "CLOUD_FUNCTION_URL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mScannerHelper = ScannerHelper(this)
        mScannerHelper.addScannerListener(object : ScannerListener {
            override fun onScannerResult(bitmap: Bitmap) {
                // Show progress dialog when processing
                showProgress()

                // Save bitmap to local cache as image then upload for processing
                val localJpeg = saveBitmapAsJpeg(bitmap)

                // Process image on server
                mOCRHelper.process(localJpeg)
            }
        })

        FirebaseApp.initializeApp(this)
        mOCRHelper = OCRHelper(this, FirebaseStorage.getInstance(bucket), cloudFunctionUrl)
        mOCRHelper.addOCRListener(object : OCRListener {
            override fun onOCRResult(file: File) {
                // Hide progress bar and open processed document in PDF viewer
                hideProgress()
                val config = ViewerConfig.Builder()
                    .openUrlCachePath(cacheDir.absolutePath)
                    .build()
                DocumentActivity.openDocument(this@MainActivity, Uri.fromFile(file), config)
            }
        })

        button = findViewById(R.id.button)
        progressBar = findViewById(R.id.loading)
        progressText = findViewById(R.id.progress_text)
        button.setOnClickListener {
//            when {
//                hasScannerPermissions() -> {
//                }
//                else -> {
//                    // You can directly ask for the permission.
//                    // The registered ActivityResultCallback gets the result of this request.

            mScannerHelper.launch()
//                }
//            }
        }
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

    private fun saveBitmapAsJpeg(bitmap: Bitmap): File {
        val filesDir: File = filesDir
        val imageFile = File(filesDir, File.createTempFile("image", ".jpg").name)

        val os: OutputStream
        try {
            os = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
            os.flush()
            os.close()
        } catch (e: Exception) {
            // ignore
        }

        return imageFile
    }
}