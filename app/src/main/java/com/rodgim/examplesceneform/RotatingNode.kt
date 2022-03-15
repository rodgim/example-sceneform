package com.rodgim.examplesceneform

import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.QuaternionEvaluator
import com.google.ar.sceneform.math.Vector3


class RotatingNode(
    solarSettings: SolarSettings,
    isOrbit: Boolean,
    clockwise: Boolean,
    axisTiltDeg: Float
): Node() {
    private var orbitAnimation: ObjectAnimator? = null
    private var degreesPerSecond: Float = 90.0f
    private val solarSettings: SolarSettings
    private var isOrbit: Boolean
    private var clockwise: Boolean
    private var axisTiltDeg: Float

    private var lastSpeedMultiplier: Float = 1.0f

    init {
        this.solarSettings = solarSettings
        this.isOrbit = isOrbit
        this.clockwise = clockwise
        this.axisTiltDeg = axisTiltDeg
    }

    override fun onUpdate(frameTime: FrameTime?) {
        super.onUpdate(frameTime)
        if (orbitAnimation == null){
            return
        }

        // Check if we need to change the speed of rotation
        val speedMultiplier = getSpeedMultiplier()

        // Nothing has changed. Continue rotating at the same speed
        if (lastSpeedMultiplier == speedMultiplier){
            return
        }

        if (speedMultiplier == 0.0f){
            orbitAnimation?.pause()
        }else{
            orbitAnimation?.resume()

            val animatedFraction = orbitAnimation?.animatedFraction
            orbitAnimation?.duration = getAnimationDuration()
            animatedFraction?.let {
                orbitAnimation?.setCurrentFraction(it)
            }
        }
        lastSpeedMultiplier = speedMultiplier
    }

    fun setDegreesPerSecond(degreesPerSecond: Float){
        this.degreesPerSecond = degreesPerSecond
    }

    override fun onActivate() {
        startAnimation()
    }

    override fun onDeactivate() {
        stopAnimation()
    }

    private fun getAnimationDuration(): Long{
        return (1000 * 360 / (degreesPerSecond * getSpeedMultiplier())).toLong()
    }

    private fun getSpeedMultiplier(): Float{
        return if (isOrbit){
            solarSettings.orbitSpeedMultiplier
        }else{
            solarSettings.rotationSpeedMultiplier
        }
    }

    private fun startAnimation(){
        if (orbitAnimation != null){
            return
        }

        orbitAnimation = createAnimator(clockwise, axisTiltDeg)
        orbitAnimation?.target = this
        orbitAnimation?.duration = getAnimationDuration()
        orbitAnimation?.start()
    }

    private fun stopAnimation(){
        if (orbitAnimation == null){
            return
        }

        orbitAnimation?.cancel()
        orbitAnimation = null
    }

    private fun createAnimator(clockwise: Boolean, axisTiltDeg: Float): ObjectAnimator{
        // Node's setLocalRotation method accepts Quaternions as parameters.
        // First, set up orientations that will animate a circle.
        val orientations = arrayOfNulls<Quaternion>(4)
        // Rotation to apply first, to tilt its axis.
        val baseOrientation = Quaternion.axisAngle(Vector3(1.0f, 0f, 0.0f), axisTiltDeg)
        for (i in orientations.indices){
            var angle = (i * 360 / (orientations.size - 1)).toFloat()
            if (clockwise){
                angle = 360 - angle
            }
            val orientation = Quaternion.axisAngle(Vector3(0.0f, 1.0f, 0.0f), angle)
            orientations[i] = Quaternion.multiply(baseOrientation, orientation)
        }

        val orbitAnimation = ObjectAnimator()
        // Cast to Object[] to make sure the varargs overload is called.
        orbitAnimation.setObjectValues(*orientations)
        // Next, give it the localRotation property.
        orbitAnimation.setPropertyName("localRotation")
        // Use Sceneform's QuaternionEvaluator.
        orbitAnimation.setEvaluator(QuaternionEvaluator())

        // Allow orbitAnimation to repeat forever
        orbitAnimation.repeatCount = ObjectAnimator.INFINITE
        orbitAnimation.repeatMode = ObjectAnimator.RESTART
        orbitAnimation.interpolator = LinearInterpolator()
        orbitAnimation.setAutoCancel(true)
        return orbitAnimation
    }
}