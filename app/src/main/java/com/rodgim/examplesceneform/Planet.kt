package com.rodgim.examplesceneform

import android.content.Context
import android.view.MotionEvent
import android.widget.TextView
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.HitTestResult
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import java.util.function.Consumer
import java.util.function.Function


class Planet(
    context: Context,
    planetName: String,
    planetScale: Float,
    orbitDegreesPerSecond: Float,
    axisTilt: Float,
    planetRenderable: ModelRenderable,
    solarSettings: SolarSettings
): Node(), Node.OnTapListener {
    private var planetName: String
    private var planetScale: Float
    private var orbitDegreesPerSecond: Float
    private var axisTilt: Float
    private var planetRenderable: ModelRenderable
    private var solarSettings: SolarSettings

    private var infoCard: Node? = null
    private var planetVisual: RotatingNode? = null
    private val context: Context
    private val INFO_CARD_Y_POS_COEFF = 0.55f

    init {
        this.context = context
        this.planetName = planetName
        this.planetScale = planetScale
        this.orbitDegreesPerSecond = orbitDegreesPerSecond
        this.axisTilt = axisTilt
        this.planetRenderable = planetRenderable
        this.solarSettings = solarSettings
        setOnTapListener(this)
    }

    override fun onActivate() {
        if (scene == null){
            throw IllegalStateException("Scene is null!")
        }

        if (infoCard == null){
            infoCard = Node()
            infoCard?.setParent(this)
            infoCard?.isEnabled = false
            infoCard?.localPosition = Vector3(0.0f, planetScale * INFO_CARD_Y_POS_COEFF, 0.0f)

            ViewRenderable.builder()
                .setView(context, R.layout.planet_card_view)
                .build()
                .thenAccept(
                    Consumer { renderable: ViewRenderable ->
                        infoCard?.renderable = renderable
                        val textView = renderable.view as TextView
                        textView.text = planetName
                    }
                ).exceptionally(
                    Function<Throwable, Void>{ throwable: Throwable? ->
                        throw AssertionError("Could not load plane card view", throwable)
                    }
                )
        }

        if (planetVisual == null){
            val counterOrbit = RotatingNode(solarSettings, true, true, 0f)
            counterOrbit.setDegreesPerSecond(orbitDegreesPerSecond)
            counterOrbit.setParent(this)

            planetVisual = RotatingNode(solarSettings, false, false, axisTilt)
            planetVisual?.setParent(counterOrbit)
            planetVisual?.renderable = planetRenderable
            planetVisual?.localScale = Vector3(planetScale, planetScale, planetScale)
        }

    }

    override fun onTap(hitTestResult: HitTestResult?, motionEvent: MotionEvent?) {
        if (infoCard == null){
            return
        }

        infoCard?.isEnabled = !infoCard!!.isEnabled
    }

    override fun onUpdate(frameTime: FrameTime?) {
        if (infoCard == null){
            return
        }

        if (scene == null){
            return
        }

        val cameraPosition = scene?.camera?.worldPosition
        val cardPosition = infoCard?.worldPosition
        val direction = Vector3.subtract(cameraPosition, cardPosition)
        val lookRotation = Quaternion.lookRotation(direction, Vector3.up())
        infoCard?.worldRotation = lookRotation
    }
}