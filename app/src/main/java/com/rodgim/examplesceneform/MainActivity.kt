package com.rodgim.examplesceneform

import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.rodgim.examplesceneform.utils.Utils
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.function.BiFunction


class MainActivity : AppCompatActivity() {
    private val RC_PERMISSIONS: Int = 0x0123
    private var cameraPermissionRequested: Boolean = false

    private lateinit var gestureDetector: GestureDetector
    private var loadingMessageSnackbar: Snackbar? = null

    private var arSceneView: ArSceneView? = null
    private lateinit var sunRenderable: ModelRenderable
    private lateinit var mercuryRenderable: ModelRenderable
    private lateinit var venusRenderable: ModelRenderable
    private lateinit var earthRenderable: ModelRenderable
    private lateinit var lunaRenderable: ModelRenderable
    private lateinit var marsRenderable: ModelRenderable
    private lateinit var jupiterRenderable: ModelRenderable
    private lateinit var saturnRenderable: ModelRenderable
    private lateinit var uranusRenderable: ModelRenderable
    private lateinit var neptuneRenderable: ModelRenderable
    private lateinit var solarControlsRenderable: ViewRenderable

    private val solarSettings: SolarSettings = SolarSettings()

    // True once scene is loaded
    private var hasFinishedLoading: Boolean = false
    // True once the scene has been placed
    private var hasPlacedSolarSystem: Boolean = false
    // Astronomical units to meters ratio. Used for positioning the planets of the solar system.
    private val AU_TO_METERS: Float = 0.5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Utils.checkIsSupportedDeviceOrFinish(this)){
            return
        }
        setContentView(R.layout.activity_main)
        arSceneView = findViewById(R.id.ar_scene_view)

        // Build all the planet models
        val sunStage: CompletableFuture<ModelRenderable> =
            ModelRenderable.builder().setSource(this, Uri.parse("Sol.sfb")).build()
        val mercuryStage: CompletableFuture<ModelRenderable> =
            ModelRenderable.builder().setSource(this, Uri.parse("Mercury.sfb")).build()
        val venusStage: CompletableFuture<ModelRenderable> =
            ModelRenderable.builder().setSource(this, Uri.parse("Venus.sfb")).build()
        val earthStage: CompletableFuture<ModelRenderable> =
            ModelRenderable.builder().setSource(this, Uri.parse("Earth.sfb")).build()
        val lunaStage: CompletableFuture<ModelRenderable> =
            ModelRenderable.builder().setSource(this, Uri.parse("Luna.sfb")).build()
        val marsStage: CompletableFuture<ModelRenderable> =
            ModelRenderable.builder().setSource(this, Uri.parse("Mars.sfb")).build()
        val jupiterStage: CompletableFuture<ModelRenderable> =
            ModelRenderable.builder().setSource(this, Uri.parse("Jupiter.sfb")).build()
        val saturnStage: CompletableFuture<ModelRenderable> =
            ModelRenderable.builder().setSource(this, Uri.parse("Saturn.sfb")).build()
        val uranusStage: CompletableFuture<ModelRenderable> =
            ModelRenderable.builder().setSource(this, Uri.parse("Uranus.sfb")).build()
        val neptuneStage: CompletableFuture<ModelRenderable> =
            ModelRenderable.builder().setSource(this, Uri.parse("Neptune.sfb")).build()

        // Build a renderable from a 2D View
        val solarControlsStage: CompletableFuture<ViewRenderable> =
            ViewRenderable.builder().setView(this, R.layout.solar_controls).build()

        CompletableFuture.allOf(
            sunStage,
            mercuryStage,
            venusStage,
            earthStage,
            lunaStage,
            marsStage,
            jupiterStage,
            saturnStage,
            uranusStage,
            neptuneStage,
            solarControlsStage)
        .handle(
            BiFunction { notUsed, throwable ->
                if (throwable != null){
                    Utils.displayError(this, "Unable to load renderable", throwable)
                    return@BiFunction null
                }

                try {
                    sunRenderable = sunStage.get()
                    mercuryRenderable = mercuryStage.get()
                    venusRenderable = venusStage.get()
                    earthRenderable = earthStage.get()
                    lunaRenderable = lunaStage.get()
                    marsRenderable = marsStage.get()
                    jupiterRenderable = jupiterStage.get()
                    saturnRenderable = saturnStage.get()
                    uranusRenderable = uranusStage.get()
                    neptuneRenderable = neptuneStage.get()
                    solarControlsRenderable = solarControlsStage.get()

                    hasFinishedLoading = true
                }catch (ex: InterruptedException){
                    Utils.displayError(this, "Unable to load renderable", ex)
                }catch (ex: ExecutionException){
                    Utils.displayError(this, "Unable to load renderable", ex)
                }
                return@BiFunction null
            }
        )

        gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent?): Boolean {
                    onSingleTap(e)
                    return true
                }

                override fun onDown(e: MotionEvent?): Boolean {
                    return true
                }
            }
        )

        arSceneView
            ?.scene
            ?.setOnTouchListener { hitTestResult, motionEvent ->
                // If the solar system hasn't been placed yet, detect a tap and the check to see if
                // the tap occurred on an ARCore plane to place the solar system
                if (!hasPlacedSolarSystem){
                    return@setOnTouchListener gestureDetector.onTouchEvent(motionEvent)
                }
                // Otherwise return false so that the touch event can propagate to the scene
                return@setOnTouchListener false
            }

        arSceneView
            ?.scene
            ?.addOnUpdateListener { frameTime ->
                if (loadingMessageSnackbar == null){
                    return@addOnUpdateListener
                }

                val frame = arSceneView?.arFrame ?: return@addOnUpdateListener

                if (frame.camera.trackingState != TrackingState.TRACKING){
                    return@addOnUpdateListener
                }

                for (plane in frame.getUpdatedTrackables(Plane::class.java)){
                    if (plane.trackingState == TrackingState.TRACKING){
                        hideLoadingMessage()
                    }
                }
            }

        Utils.requestCameraPermission(this, RC_PERMISSIONS)
    }

    override fun onResume() {
        super.onResume()
        if (arSceneView == null){
            return
        }

        if (arSceneView?.session == null){
            try {
                val lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                val session = if (cameraPermissionRequested){
                    Utils.createArSessionWithInstallRequest(this, lightEstimationMode)
                }else{
                    Utils.createArSessionNoInstallRequest(this, lightEstimationMode)
                }
                if (session == null){
                    cameraPermissionRequested = Utils.hasCameraPermission(this)
                    return
                }else{
                    arSceneView?.setupSession(session)
                }
            }catch (e: UnavailableException){
                Utils.handleSessionException(this, e)
            }
        }

        try {
            arSceneView?.resume()
        }catch (e: CameraNotAvailableException){
            Utils.displayError(this, "Unable to get camera", e)
            finish()
            return
        }

        if (arSceneView?.session != null){
            showLoadingMessage()
        }
    }

    override fun onPause() {
        super.onPause()
        if (arSceneView != null){
            arSceneView?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (arSceneView != null){
            arSceneView?.destroy()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!Utils.hasCameraPermission(this)){
            if (!Utils.shouldShowRequestPermissionRationale(this)){
                Utils.launchPermissionSettings(this)
            }else{
                Toast.makeText(
                    this,
                    "Camera permission is needed to run this application",
                    Toast.LENGTH_LONG
                ).show()
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus){
            window
                .decorView
                .setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun onSingleTap(tap: MotionEvent?){
        if (!hasFinishedLoading){
            return
        }

        val frame = arSceneView?.arFrame
        if (frame != null){
            if (!hasPlacedSolarSystem && tryPlaceSolarSystem(tap, frame)){
                hasPlacedSolarSystem = true
            }
        }
    }

    private fun tryPlaceSolarSystem(tap: MotionEvent?, frame: Frame): Boolean{
        if (tap != null && frame.camera.trackingState == TrackingState.TRACKING){
            for (hit in frame.hitTest(tap)){
                val trackable = hit.trackable
                if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)){
                    val anchor = hit.createAnchor()
                    val anchorNode = AnchorNode(anchor)
                    anchorNode.setParent(arSceneView?.scene)
                    val solarSystem = createSolarSystem()
                    anchorNode.addChild(solarSystem)
                    return true
                }
            }
        }
        return false
    }

    private fun createSolarSystem(): Node{
        val base = Node()

        val sun = Node()
        sun.setParent(base)
        sun.localPosition = Vector3(0.0f, 0.5f, 0.0f)

        val sunVisual = Node()
        sunVisual.setParent(sun)
        sunVisual.renderable = sunRenderable
        sunVisual.localScale = Vector3(0.5f, 0.5f, 0.5f)

        val solarControls = Node()
        solarControls.setParent(sun)
        solarControls.renderable = solarControlsRenderable
        solarControls.localPosition = Vector3(0.0f, 0.25f, 0.0f)

        val solarControlsView = solarControlsRenderable.view
        val orbitSpeedBar = solarControlsView.findViewById<SeekBar>(R.id.orbitSpeedBar)
        orbitSpeedBar.setProgress((solarSettings.orbitSpeedMultiplier * 10.0f).toInt())
        orbitSpeedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val ratio = progress.toFloat() / orbitSpeedBar.max.toFloat()
                solarSettings.orbitSpeedMultiplier = ratio * 10.0f
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }

        })

        val rotationSpeedBar = solarControlsView.findViewById<SeekBar>(R.id.rotationSpeedBar)
        rotationSpeedBar.setProgress((solarSettings.rotationSpeedMultiplier * 10.0f).toInt())
        rotationSpeedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val ratio = progress.toFloat() / rotationSpeedBar.max.toFloat()
                solarSettings.rotationSpeedMultiplier = ratio * 10.0f
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }

        })

        sunVisual.setOnTapListener { hitTestResult, motionEvent ->
            solarControls.isEnabled = !solarControls.isEnabled
        }

        createPlanet("Mercury", sun, 0.4f, 47f, mercuryRenderable, 0.019f, 0.03f)
        createPlanet("Venus", sun, 0.7f, 35f, venusRenderable, 0.0475f, 2.64f)
        val earth = createPlanet("Earth", sun, 1.0f, 29f, earthRenderable, 0.05f, 23.4f)
        createPlanet("Moon", earth, 1.15f, 100f, lunaRenderable, 0.018f, 6.68f)
        createPlanet("Mars", sun, 1.5f, 24f, marsRenderable, 0.0265f, 25.19f)
        createPlanet("Jupiter", sun, 2.2f, 13f, jupiterRenderable, 0.16f, 3.13f)
        createPlanet("Saturn", sun, 3.5f, 9f, saturnRenderable, 0.1325f, 26.73f)
        createPlanet("Uranus", sun, 5.2f, 7f, uranusRenderable, 0.1f, 82.23f)
        createPlanet("Neptune", sun, 6.1f, 5f, neptuneRenderable, 0.074f, 28.32f)
        return base
    }

    private fun createPlanet(
        name: String,
        parent: Node,
        auFromParent: Float,
        orbitDegreesPerSecond: Float,
        renderable: ModelRenderable,
        planetScale: Float,
        axisTilt: Float
    ): Node{
        // Orbit is a rotating node with no renderable positioned at the sun
        // The planet is positioned relative to the orbit so that it appears to rotate around the sun
        // This is done instead of making the sun rotate so each planet can orbit at its own speed
        val orbit = RotatingNode(solarSettings, true, false, 0f)
        orbit.setDegreesPerSecond(orbitDegreesPerSecond)
        orbit.setParent(parent)

        // Create the planet and position it relative to the sun
        val planet = Planet(
            this,
            name,
            planetScale,
            orbitDegreesPerSecond,
            axisTilt,
            renderable,
            solarSettings
        )
        planet.setParent(orbit)
        planet.localPosition = Vector3(auFromParent * AU_TO_METERS, 0.0f, 0.0f)
        return planet
    }

    private fun showLoadingMessage(){
        if (loadingMessageSnackbar != null && loadingMessageSnackbar?.isShownOrQueued == true){
            return
        }

        loadingMessageSnackbar = Snackbar.make(
            findViewById(android.R.id.content),
            R.string.plane_finding,
            Snackbar.LENGTH_INDEFINITE
        )
        loadingMessageSnackbar?.view?.setBackgroundColor(-0x40cdcdce)
        loadingMessageSnackbar?.show()
    }

    private fun hideLoadingMessage(){
        if (loadingMessageSnackbar == null){
            return
        }

        loadingMessageSnackbar?.dismiss()
        loadingMessageSnackbar = null
    }
}