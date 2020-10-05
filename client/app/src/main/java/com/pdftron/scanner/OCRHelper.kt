package com.pdftron.scanner

import android.net.Uri
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.google.firebase.storage.FirebaseStorage
import com.pdftron.pdf.config.ViewerConfig
import com.pdftron.pdf.controls.DocumentActivity
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

interface OCRListener {
    fun onOCRResult(file: File)
}

class OCRHelper(activity: FragmentActivity, storage: FirebaseStorage, cloudFunctionUrl: String) {

    private val mActivity: FragmentActivity = activity
    private val mStorage: FirebaseStorage = storage
    private val mListeners: MutableList<OCRListener> = ArrayList()
    private val mCloudFunctionUrl: String = cloudFunctionUrl

    private var client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun process(localJpeg: File) {
        uploadFile(localJpeg)
    }

    fun addOCRListener(listener: OCRListener) {
        mListeners += listener
    }

    private fun uploadFile(localFile: File) {
        val reference = mStorage.reference
        val fileName = localFile.name
        val fileReference = reference.child(fileName)
        val uploadTask = fileReference.putFile(Uri.fromFile(localFile))
        // Register observers to listen for when the download is done or if it fails
        uploadTask.addOnSuccessListener { taskSnapshot ->
            Log.d("ScannerSample", "File uploaded")
            processFile(fileName)
        }.addOnFailureListener {
            // Handle unsuccessful uploads
            Log.d("ScannerSample", "File not uploaded")
        }
    }

    private fun processFile(fileName: String) {
        val processFile = Single.create<String> {
            try {
                val httpBuilder = mCloudFunctionUrl
                    .toHttpUrlOrNull()!!
                    .newBuilder()
                httpBuilder.addQueryParameter("file", fileName)
                val request: Request = Request.Builder()
                    .url(httpBuilder.build())
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body
                if (response.isSuccessful) {
                    it.onSuccess(body!!.string())
                } else {
                    throw IOException("Unsuccessful")
                }
            } catch (e: IOException) {
                it.onError(e)
            }
        }
        processFile.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(Consumer {
                Log.d("ScannerSample", "Result is = $it")
                val trimmedResult = it.replace("\"", "")
                downloadFile(trimmedResult)
            }, Consumer {
                Log.d("ScannerSample", "Error = $it")
            })
    }

    private fun downloadFile(fileName: String) {
        val reference = mStorage.reference
        val fileReference = reference.child(fileName)
        val localFile = File(mActivity.cacheDir, fileName)

        fileReference.getFile(localFile).addOnSuccessListener {
            // Local temp file has been created
            Log.d("ScannerSample", "File downloaded")
            mListeners.forEach{
                it.onOCRResult(localFile)
            }

        }.addOnFailureListener {
            // Handle any errors
            Log.d("ScannerSample", "File not downloaded: $it")
        }
    }
}