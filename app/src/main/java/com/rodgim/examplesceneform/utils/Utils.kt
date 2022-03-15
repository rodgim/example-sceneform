package com.rodgim.examplesceneform.utils

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.ArCoreApk.InstallStatus
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.*
import com.rodgim.examplesceneform.MIN_OPENGL_VERSION
import com.rodgim.examplesceneform.R
import com.rodgim.examplesceneform.TAG


object Utils {
    fun displayError(
        context: Context,
        errorMsg: String,
        error: Throwable?
    ){
        val tag = context.javaClass.simpleName

        val toastText = when {
            error?.message != null -> {
                Log.e(tag, errorMsg, error)
                "$errorMsg: ${error.message}"
            }
            error != null -> {
                Log.e(tag, errorMsg, error)
                errorMsg
            }
            else -> {
                Log.e(tag, errorMsg)
                errorMsg
            }
        }

        Handler(Looper.getMainLooper())
            .post {
                val toast = Toast.makeText(context, toastText, Toast.LENGTH_LONG)
                toast.setGravity(Gravity.CENTER, 0, 0)
                toast.show()
            }
    }

    @Throws(UnavailableException::class)
    private fun createArSession(
        activity: Activity,
        installRequested: Boolean,
        lightEstimationMode: Config.LightEstimationMode
    ): Session?{
        var session: Session? = null
        if (hasCameraPermission(activity)){
            when(ArCoreApk.getInstance().requestInstall(activity, !installRequested)){
                InstallStatus.INSTALL_REQUESTED -> {
                    //installRequested = true
                    return null
                }
                InstallStatus.INSTALLED ->{}
            }
            session = Session(activity)
            // ArSceneView requires the LATEST_CAMERA_IMAGE non-blocking update mode
            val config = Config(session)
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            config.lightEstimationMode = lightEstimationMode
            session.configure(config)
        }
        return session
    }

    @Throws(UnavailableException::class)
    fun createArSessionWithInstallRequest(
        activity: Activity,
        lightEstimationMode: Config.LightEstimationMode
    ): Session? = createArSession(activity, true, lightEstimationMode)

    @Throws(UnavailableException::class)
    fun createArSessionNoInstallRequest(
        activity: Activity,
        lightEstimationMode: Config.LightEstimationMode
    ): Session? = createArSession(activity, false, lightEstimationMode)

    /**
     *  Ask for permission, if we don't have
     */
    fun requestCameraPermission(activity: Activity, requestCode: Int){
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.CAMERA),
            requestCode
        )
    }

    /**
     *  Check to see we have the necessary permissions for this app
     */
    fun hasCameraPermission(activity: Activity): Boolean{
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     *  Check to see if we need to show the rationale for this permission
     */
    fun shouldShowRequestPermissionRationale(activity: Activity): Boolean{
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.CAMERA
        )
    }

    /**
     * Launch Application Setting to grant permission
     */
    fun launchPermissionSettings(activity: Activity){
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", activity.packageName, null)
        activity.startActivity(intent)
    }

    fun handleSessionException(activity: Activity, sessionException: UnavailableException){
        val message = when(sessionException){
            is UnavailableArcoreNotInstalledException -> "Please install ARCore"
            is UnavailableApkTooOldException -> "Please update ARCore"
            is UnavailableSdkTooOldException -> "Please update this app"
            is UnavailableDeviceNotCompatibleException -> "This device does not support AR"
            else -> "Failed to create AR session"
        }
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Returns false and displays an error message if Sceneform ca not run,
     * true if Sceneform can run on this device
     */
    fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean{
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N){
            Log.e(TAG, activity.getString(R.string.error_sceneform_requires))
            Toast.makeText(activity, R.string.error_sceneform_requires, Toast.LENGTH_LONG).show()
            activity.finish()
            return false
        }

        val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val openGlVersionString = activityManager.deviceConfigurationInfo.glEsVersion
        if (openGlVersionString.toDouble() < MIN_OPENGL_VERSION){
            Log.e(TAG, activity.getString(R.string.error_sceneform_opengl_version))
            Toast.makeText(activity, R.string.error_sceneform_opengl_version, Toast.LENGTH_LONG).show()
            activity.finish()
            return false
        }
        return true
    }
}