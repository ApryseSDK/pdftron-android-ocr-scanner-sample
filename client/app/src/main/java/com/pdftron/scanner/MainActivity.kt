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
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import com.pdftron.pdf.config.ViewerConfig
import com.pdftron.pdf.controls.DocumentActivity
import com.scanlibrary.ScanConstants
import com.scanlibrary.ScannerContract
import com.scanlibrary.Utils
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var button: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    private val bucket = "FIREBASE_STORAGE_BUCKET"
    private val cloudFunctionUrl: String = "CLOUD_FUNCTION_URL"

    private val storage: FirebaseStorage = FirebaseStorage.getInstance(bucket)

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
                contentResolver.delete(uri!!, null, null)

                // Save bitmap to local cache as image then upload for processing
                val localJpeg = Utils.saveBitmapAsJpeg(bitmap, filesDir)

                // Process image on server
                uploadFile(localJpeg)

                // Show progress UI
                showProgress()
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

    private fun uploadFile(localFile: File) {
        val reference = storage.reference
        val fileName = localFile.name
        val fileReference = reference.child(fileName)
        val uploadTask = fileReference.putFile(Uri.fromFile(localFile))
        // Register observers to listen for when the download is done or if it fails
        uploadTask.addOnSuccessListener {
            // If successful, we run our cloud function with the given file
            runCloudFunction(fileName)
        }
    }

    private fun runCloudFunction(fileName: String) {
        // Call cloud function using HTTP request using OkHttp and RxJava
        Single.create<String> {
            // Create HTTP request to trigger cloud function
            val httpBuilder = cloudFunctionUrl.toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("file", fileName)
            val request = Request.Builder().url(httpBuilder.build()).build()
            val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                it.onSuccess(response.body!!.string())
            } else {
                it.onError(IOException(response.message))
            }
        }.apply {
            subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { it ->
                    // If processing is successful, download processed file from Firebase Storage
                    downloadStorageFile(it.replace("\"", "")) // trim result

                    // Optionally, delete uploaded file from Firebase Storage
                    deleteStorageFile(fileName)
                }
        }
    }

    private fun downloadStorageFile(fileName: String) {
        val reference = storage.reference
        val fileReference = reference.child(fileName)
        val localFile = File(cacheDir, fileName)

        fileReference.getFile(localFile).addOnSuccessListener {
            // Hide progress bar
            hideProgress()

            // Open processed document in PDF viewer
            val config = ViewerConfig.Builder().openUrlCachePath(cacheDir.absolutePath).build()
            DocumentActivity.openDocument(this@MainActivity, Uri.fromFile(localFile), config)

            // Optionally, delete processed file on Firebase Storage
            deleteStorageFile(fileName)
        }
    }

    private fun deleteStorageFile(fileName: String) {
        val reference = storage.reference
        val fileReference = reference.child(fileName)
        fileReference.delete()
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

    private fun saveBitmapAsJpeg(bitmap: Bitmap, folder: File): File {
        val imageFile = File(folder, File.createTempFile("image", ".jpg").name)

        val os = FileOutputStream(imageFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
        os.flush()
        os.close()

        return imageFile
    }
}