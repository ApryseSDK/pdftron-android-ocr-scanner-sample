package com.pdftron.scanner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.scanlibrary.ScanActivity
import com.scanlibrary.ScanConstants
import java.io.IOException

interface ScannerListener {
    fun onScannerResult(bitmap: Bitmap)
}

class ScannerHelper(activity: FragmentActivity) {

    private val mActivity: FragmentActivity = activity
    private val mListeners: MutableList<ScannerListener> = ArrayList();

    private val requestCameraPermission =
        mActivity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { map: Map<String, Boolean> ->

            // Make sure all permissions are available before launching scanner
            var hasPermission = true;
            for (entry in map) {
                if (!entry.value) {
                    hasPermission = false
                }
            }

            if (hasPermission) {
                scannerLauncher.launch(ScanConstants.OPEN_CAMERA)
            } else {
                Toast.makeText(
                    mActivity,
                    "Camera and Storage Permission Is Required",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val scannerLauncher =
        mActivity.registerForActivityResult(ScannerContract()) { uri ->
            if (uri != null) {
                try {
                    var bitmap: Bitmap? = null
                    val contentResolver = mActivity.contentResolver
                    bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    contentResolver.delete(uri!!, null, null)

                    mListeners.forEach { it ->
                        it.onScannerResult(bitmap)
                    }

                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }


    fun launch() {
        requestCameraPermission.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }

    fun addScannerListener(listener: ScannerListener) {
        mListeners += listener;
    }

    private fun hasScannerPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            mActivity,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    mActivity,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    mActivity,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED;
    }

    class ScannerContract : ActivityResultContract<Int, Uri?>() {
        override fun createIntent(context: Context, input: Int?): Intent {
            val preference: Int = ScanConstants.OPEN_CAMERA
            return Intent(context, ScanActivity::class.java).apply {
                putExtra(ScanConstants.OPEN_INTENT_PREFERENCE, input)
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            return if (resultCode == Activity.RESULT_OK) {
                intent?.extras?.getParcelable(ScanConstants.SCANNED_RESULT)
            } else {
                null
            }
        }

    }
}