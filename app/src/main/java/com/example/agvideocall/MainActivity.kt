package com.example.agvideocall

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.android.gms.ads.formats.UnifiedNativeAd
import com.google.android.gms.ads.formats.UnifiedNativeAdView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import io.agora.rtc.Constants
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.RtcEngine
import io.agora.rtc.video.VideoCanvas
import io.agora.rtc.video.VideoEncoderConfiguration
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_ID = 1001
    private val REQUESTED_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    private val ROOM_NAME = "agora_room"
    private val DEFAULT_CHANNEL_NAME = "channel-1"

    private var mRtcEngine: RtcEngine? = null
    private var doubleBackToExitPressedOnce = false

    private var nativeAd: UnifiedNativeAd? = null
    private lateinit var mHandler: Handler
    private val mInterval = 15000
    private var currentChannel = ""
    private var channelAvailable = true

    // Write a message to the database
    private val myDatabase = Firebase.database
    private val myRef = myDatabase.reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        mHandler = Handler()
        bindAndSetupUi()
        firebaseListener()
    }

    private fun firebaseListener() {
        // Read from the database
        myRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                updateChannelStatus(dataSnapshot)
            }
            override fun onCancelled(error: DatabaseError) {
                // Failed to read value
                Log.w("ABC__", "Failed to read value.", error.toException())
            }
        })
    }

    private fun updateChannelStatus(dataSnapshot: DataSnapshot) {
        Toast.makeText(this, "FireDB Updated", Toast.LENGTH_SHORT).show()
        if (dataSnapshot.children.count() == 0) {
            // create channel if not available
            currentChannel = DEFAULT_CHANNEL_NAME
            createOrUpdateDatabaseWithChannelId(DEFAULT_CHANNEL_NAME, true)
        } else {
            var falseCount = 0
            for (channel in dataSnapshot.child(ROOM_NAME).children) {
                Log.d("ABC__00", "channelName: " + channel.key + " channelStatus: " + channel.value)
                if (channel.value == false)
                    falseCount++
                if (channel.value == true) {
                    channelAvailable = true
                    // setCurrent channel
                    currentChannel = channel.key.toString()
                    Log.d("ABC__111", "channelName: " + channel.key + " channelStatus: " + channel.value)
                    break
                } else {
                    if (falseCount == dataSnapshot.child(ROOM_NAME).children.count()) {
                        val channelNo = dataSnapshot.child(ROOM_NAME).children.count() + 1
                        currentChannel = "channel-$channelNo"
                        Log.d("ABC__222", "channelName: " + currentChannel + " channelStatus: " + !channelAvailable)
                        createOrUpdateDatabaseWithChannelId(currentChannel, true)
                        channelAvailable = true
                        break
                    }
                    continue
                }
            }
        }

    }

    private fun createOrUpdateDatabaseWithChannelId(currentChId: String, channelStatus: Boolean) {
        myRef.child(ROOM_NAME).child(currentChId).setValue(channelStatus)
    }

    private fun bindAndSetupUi() {
        startCall.setOnClickListener() {
            if (checkSelfPermission(REQUESTED_PERMISSIONS[0], PERMISSION_REQUEST_ID) &&
                    checkSelfPermission(REQUESTED_PERMISSIONS[1], PERMISSION_REQUEST_ID)) {
                initializeAgoraEngine()
                setupLocalVideoFeed()
                joinChannel()
                startCall.visibility = View.GONE
                endCall.visibility = View.VISIBLE

                closeCallIfNotConnectedIn(mInterval, endCall)
            }
        }
        endCall.setOnClickListener() {
            leaveChannelClicked()
            endCall.visibility = View.GONE
            startCall.visibility = View.VISIBLE


            removeHandlerCallbacks()
        }
    }

    private fun closeCallIfNotConnectedIn(mInterval: Int, endCallBtn: TextView) {
        mHandler.postDelayed({
            Toast.makeText(this@MainActivity, getString(R.string.try_again_msg), Toast.LENGTH_LONG).show()
            leaveChannel()
            endCallBtn.performClick()
        }, mInterval.toLong())
    }

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onFirstRemoteVideoDecoded(uid: Int, width: Int, height: Int, elapsed: Int) {
            runOnUiThread { setupRemoteVideoStream(uid) }
        }
        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                endCall.performClick()
            }
        }
    }

    private fun initializeAgoraEngine() {
        try {
            mRtcEngine = RtcEngine.create(baseContext, getString(R.string.agora_app_id), mRtcEventHandler)
        } catch (e: Exception) {
            throw RuntimeException("NEED TO check rtc sdk init fatal error" + Log.getStackTraceString(e))
        }
        setupUserSession()
    }

    private fun setupUserSession() {
        mRtcEngine!!.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
        mRtcEngine!!.enableVideo()
        mRtcEngine!!.setVideoEncoderConfiguration(VideoEncoderConfiguration(VideoEncoderConfiguration.VD_640x480, VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30,
            VideoEncoderConfiguration.STANDARD_BITRATE,
            VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT))
    }

    private fun setupLocalVideoFeed() {
        val videoContainer = findViewById<FrameLayout>(R.id.local_video_view)
        val videoSurface = RtcEngine.CreateRendererView(baseContext)
        videoSurface.setZOrderMediaOverlay(true)
        videoContainer.addView(videoSurface)
        mRtcEngine!!.setupLocalVideo(VideoCanvas(videoSurface, VideoCanvas.RENDER_MODE_FIT, 0))
    }

    private fun setupRemoteVideoStream(uid: Int) {
        createOrUpdateDatabaseWithChannelId(currentChannel, false)
        val videoContainer = findViewById<FrameLayout>(R.id.remote_video_view)
        val videoSurface = RtcEngine.CreateRendererView(baseContext)
        videoContainer.addView(videoSurface)
        mRtcEngine!!.setupRemoteVideo(VideoCanvas(videoSurface, VideoCanvas.RENDER_MODE_FIT, uid))
        mRtcEngine!!.setRemoteSubscribeFallbackOption(Constants.MEDIA_TYPE_AUDIO_ONLY)
        removeHandlerCallbacks()
    }

    private fun checkSelfPermission(permission: String?, requestCode: Int): Boolean {
        if (ContextCompat.checkSelfPermission(this,
                        permission!!)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    REQUESTED_PERMISSIONS,
                    requestCode)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_ID -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }
        }
    }

    private fun joinChannel() {
//        Toast.makeText(this, "chNo: $currentChannel", Toast.LENGTH_LONG).show()
        mRtcEngine!!.joinChannel(null, currentChannel, "Extra Optional Data", 0)
        setupLocalVideoFeed()
    }

    private fun leaveChannelClicked() {
        leaveChannel()
        removeVideo(R.id.local_video_view)
        removeVideo(R.id.remote_video_view)
//        createOrUpdateDatabaseWithChannelId(currentChannel, true)
    }

    private fun leaveChannel() {
        try {
            mRtcEngine!!.leaveChannel()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun removeVideo(containerID: Int) {
        val videoContainer = findViewById<FrameLayout>(containerID)
        videoContainer.removeAllViews()
    }


    private fun populateWithNativeAd(unifiedNativeAd: UnifiedNativeAd,adView: UnifiedNativeAdView) {


    }

    private fun removeHandlerCallbacks() {
        mHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        RtcEngine.destroy()
        mRtcEngine = null

        if (nativeAd != null)
            nativeAd!!.destroy()
        removeHandlerCallbacks()
    }

    override fun onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed()
            return
        }
        this.doubleBackToExitPressedOnce = true
        Toast.makeText(this, "Press again to exit. Any ongoing call will be disconnected.", Toast.LENGTH_LONG).show()
        Handler().postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
    }

}
