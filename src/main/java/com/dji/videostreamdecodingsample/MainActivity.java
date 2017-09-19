package com.dji.videostreamdecodingsample;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.dji.videostreamdecodingsample.agora.common.Constant;
import com.dji.videostreamdecodingsample.agora.openlive.model.AGEventHandler;
import com.dji.videostreamdecodingsample.agora.openlive.model.ConstantApp;
import com.dji.videostreamdecodingsample.agora.openlive.model.EngineConfig;
import com.dji.videostreamdecodingsample.agora.openlive.model.MyEngineEventHandler;
import com.dji.videostreamdecodingsample.agora.openlive.model.WorkerThread;
import com.dji.videostreamdecodingsample.media.DJIVideoStreamDecoder;
//import com.dji.videostreamdecodingsample.agora.openlive.model.

import com.dji.videostreamdecodingsample.media.NativeHelper;
import dji.common.product.Model;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.camera.Camera;
import io.agora.rtc.Constants;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.video.AgoraVideoFrame;
import io.agora.rtc.video.VideoCanvas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends AppCompatActivity implements DJIVideoStreamDecoder.IYuvDataListener, AGEventHandler {
    private static final String TAG = MainActivity.class.getSimpleName();
    static final int MSG_WHAT_SHOW_TOAST = 0;
    static final int MSG_WHAT_UPDATE_TITLE = 1;
    static final boolean useSurface = true;

    private TextView titleTv;
    private TextureView videostreamPreviewTtView;
    private SurfaceView videostreamPreviewSf;
    private SurfaceHolder videostreamPreviewSh;

    private BaseProduct mProduct;
    private Camera mCamera;
    private DJICodecManager mCodecManager;
    byte[] bytes;

    private TextView savePath;
    private TextView screenShot;
    private List<String> pathList = new ArrayList<>();
    private final ConcurrentHashMap<AGEventHandler, Integer> mEventHandlerList = new ConcurrentHashMap<>();
    //private final HashMap<Integer, SurfaceView> mUidsList = new HashMap<>();

    private HandlerThread backgroundHandlerThread;
    public Handler backgroundHandler;
    private WorkerThread mWorkerThread;

    private boolean isBroadcaster(int cRole) {
        return cRole == Constants.CLIENT_ROLE_BROADCASTER;
    }

    protected VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;
    public AgoraVideoFrame vf = new AgoraVideoFrame();

    @Override
    protected void onResume() {
        super.onResume();
        if (useSurface) {
            DJIVideoStreamDecoder.getInstance().resume();
        }
        notifyStatusChange();
    }

    @Override
    protected void onPause() {
        if (mCamera != null) {
            if (VideoFeeder.getInstance().getVideoFeeds() != null
                    && VideoFeeder.getInstance().getVideoFeeds().size() > 0) {
                VideoFeeder.getInstance().getVideoFeeds().get(0).setCallback(null);
            }
        }
        if (useSurface) {
            DJIVideoStreamDecoder.getInstance().stop();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (useSurface) {
            DJIVideoStreamDecoder.getInstance().destroy();
            NativeHelper.getInstance().release();
        }
        if (mCodecManager != null) {
            mCodecManager.destroyCodec();
        }
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NativeHelper.getInstance().init();

        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        setContentView(R.layout.activity_main);

        backgroundHandlerThread = new HandlerThread("background handler thread");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());

        initUi();
        initPreviewer();

    }

    public Handler mainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_WHAT_SHOW_TOAST:
                    Toast.makeText(getApplicationContext(), (String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case MSG_WHAT_UPDATE_TITLE:
                    if (titleTv != null) {
                        titleTv.setText((String) msg.obj);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private void showToast(String s) {
        mainHandler.sendMessage(
                mainHandler.obtainMessage(MSG_WHAT_SHOW_TOAST, s)
        );
    }

    protected final EngineConfig config() {
        return ((VideoDecodingApplication) getApplication()).getWorkerThread().getEngineConfig();

    }

    protected final WorkerThread worker() {
        return ((VideoDecodingApplication) getApplication()).getWorkerThread();
    }
    protected RtcEngine rtcEngine() {
        return ((VideoDecodingApplication) getApplication()).getWorkerThread().getRtcEngine();
    }

    private void updateTitle(String s) {
        mainHandler.sendMessage(
                mainHandler.obtainMessage(MSG_WHAT_UPDATE_TITLE, s)
        );
    }

    private void initUi() {
        //savePath = (TextView) findViewById(R.id.activity_main_save_path);

        screenShot = (TextView) findViewById(R.id.activity_main_screen_shot);
        screenShot.setSelected(false);
        titleTv = (TextView) findViewById(R.id.title_tv);
        videostreamPreviewTtView = (TextureView) findViewById(R.id.livestream_preview_ttv);
        videostreamPreviewSf = (SurfaceView) findViewById(R.id.livestream_preview_sf);
        videostreamPreviewSh = videostreamPreviewSf.getHolder();
        mWorkerThread = ((VideoDecodingApplication)getApplication()).getWorkerThread();
        mWorkerThread.configEngine(1,ConstantApp.DEFAULT_PROFILE_IDX);
        mWorkerThread.joinChannel("SWOO", 1234223);


        /*SurfaceView surfaceV = RtcEngine.CreateRendererView(getApplicationContext());
        rtcEngine().setupLocalVideo(new VideoCanvas(surfaceV, VideoCanvas.RENDER_MODE_HIDDEN, 0));
        surfaceV.setZOrderOnTop(true);
        surfaceV.setZOrderMediaOverlay(true);
*/
        if (useSurface) {
            videostreamPreviewSf.setVisibility(View.VISIBLE);
            videostreamPreviewTtView.setVisibility(View.GONE);
            videostreamPreviewSh.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    DJIVideoStreamDecoder.getInstance().init(getApplicationContext(), videostreamPreviewSh.getSurface());
                    DJIVideoStreamDecoder.getInstance().setYuvDataListener(MainActivity.this);
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    DJIVideoStreamDecoder.getInstance().changeSurface(holder.getSurface());
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {

                }
            });
        } else {
            videostreamPreviewSf.setVisibility(View.GONE);
            videostreamPreviewTtView.setVisibility(View.VISIBLE);
        }
    }

    private void notifyStatusChange() {

        mProduct = VideoDecodingApplication.getProductInstance();

        Log.d(TAG, "notifyStatusChange: " + (mProduct == null ? "Disconnect" : (mProduct.getModel() == null ? "null model" : mProduct.getModel().name())));
        if (mProduct != null && mProduct.isConnected() && mProduct.getModel() != null) {
            updateTitle(mProduct.getModel().name() + " Connected");
        } else {
            updateTitle("Disconnected");
        }

        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataCallBack = new VideoFeeder.VideoDataCallback() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {

                Log.d(TAG, "camera recv video data size: " + size);
                if (useSurface) {
                    DJIVideoStreamDecoder.getInstance().parse(videoBuffer, size);
                } else if (mCodecManager != null) {
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                }

            }
        };

        if (null == mProduct || !mProduct.isConnected()) {
            mCamera = null;
            showToast("Disconnected");
        } else {
            if (!mProduct.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                if (VideoFeeder.getInstance().getVideoFeeds() != null
                        && VideoFeeder.getInstance().getVideoFeeds().size() > 0) {
                    VideoFeeder.getInstance().getVideoFeeds().get(0).setCallback(mReceivedVideoDataCallBack);
                }
            }
        }
    }

    /**
     * Init a fake texture view to for the codec manager, so that the video raw data can be received
     * by the camera
     */
    private void initPreviewer() {
        videostreamPreviewTtView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "real onSurfaceTextureAvailable");
                if (mCodecManager == null) {
                    mCodecManager = new DJICodecManager(getApplicationContext(), surface, width, height);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (mCodecManager != null) mCodecManager.cleanSurface();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    @Override
    public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
        Log.d(TAG, "onJoinChannelSuccess " + channel + " " + uid + " " + (uid & 0xFFFFFFFFL) + " " + elapsed);

        Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
        while (it.hasNext()) {
            AGEventHandler handler = it.next();
            handler.onJoinChannelSuccess(channel, uid, elapsed);
        }
    }

    @Override //when is this called??
    public void onYuvDataReceived(byte[] yuvFrame, int width, int height) {

//        /* I think this isn't needed.... but I might be wrong*/
//
//        byte[] y = new byte[width * height];
//        byte[] u = new byte[width * height / 4];
//        byte[] v = new byte[width * height / 4];
//        byte[] nu = new byte[width * height / 4];
//        byte[] nv = new byte[width * height / 4];
//        System.arraycopy(yuvFrame, 0, y, 0, y.length);
//        for (int i = 0; i < u.length; i++) {
//            v[i] = yuvFrame[y.length + 2 * i];
//            u[i] = yuvFrame[y.length + 2 * i + 1];
//        }
//        int uvWidth = width / 2;
//        int uvHeight = height / 2;
//        for (int j = 0; j < uvWidth / 2; j++) {
//            for (int i = 0; i < uvHeight / 2; i++) {
//                byte uSample1 = u[i * uvWidth + j];
//                byte uSample2 = u[i * uvWidth + j + uvWidth / 2];
//                byte vSample1 = v[(i + uvHeight / 2) * uvWidth + j];
//                byte vSample2 = v[(i + uvHeight / 2) * uvWidth + j + uvWidth / 2];
//                nu[2 * (i * uvWidth + j)] = uSample1;
//                nu[2 * (i * uvWidth + j) + 1] = uSample1;
//                nu[2 * (i * uvWidth + j) + uvWidth] = uSample2;
//                nu[2 * (i * uvWidth + j) + 1 + uvWidth] = uSample2;
//                nv[2 * (i * uvWidth + j)] = vSample1;
//                nv[2 * (i * uvWidth + j) + 1] = vSample1;
//                nv[2 * (i * uvWidth + j) + uvWidth] = vSample2;
//                nv[2 * (i * uvWidth + j) + 1 + uvWidth] = vSample2;
//            }
//        }
//        //nv21test
//        bytes = new byte[yuvFrame.length];
//        System.arraycopy(y, 0, bytes, 0, y.length);
//        //Log.d(TAG, new String(bytes));
//
//        for (int i = 0; i < u.length; i++) {
//            bytes[y.length + (i * 2)] = nv[i];
//            bytes[y.length + (i * 2) + 1] = nu[i];
//        }

        vf.format = AgoraVideoFrame.FORMAT_NV21;
        vf.timeStamp = System.currentTimeMillis();
        vf.stride = width;
        vf.height = height;
        //vf.buf = bytes;
        vf.buf = yuvFrame;

        Log.d(TAG, "we are here MPS123");
        sendVFtoAgora(vf);

    }

    private void sendVFtoAgora(AgoraVideoFrame vf) {

        /*
         * Conjecture: by the time we reach here there should be a WorkerThread which has invoked
         * an instance of rtcEngine (agora.openlive.model.WorkerThread)
         *
         * We can call the following methods of WorkerThread (amongst others)
         *
         * joinChannel(String channel, int uid), leaveChannel(String channel), configEngine(cRole, vProfile)
         * we should import com.dji...agora.openlive.ConstantApp; in order to access VIDEO_PROFILES
         * Then call the following methods to configure the Agora rtcEngine and join a Channel
         * Then the next task is to modify the WorkerThread to load in an externalVideoSource
         * so that we can then call PushExternalVideoFrame
         *
         *
         * mWorkerThread.configEngine(cRole, io.agora.rtc.Constants.VIDEO_PROFILE_720P(?)) // cRole should be broadcaster
         * mWorkerThread.joinChannel(String channel, int uid(?))
         */
        Log.d(TAG, "SWOOOOOO starting broadcast as broadcaster, calling WorkerThread"); //this works but are we pushing data to a channel

        // an attempt at craziest code of the year award
        Log.d(TAG, "Trying to push video frame");

        if(mWorkerThread.getRtcEngine().pushExternalVideoFrame(vf)) {
            Log.d(TAG, "Frame successfully pushed");
        } else {
            Log.d(TAG,"frame not pushed");
        }

    }

    public void onClick(View v) {
        if (screenShot.isSelected()) { //if live stream is select
            screenShot.setText("B/C is OFF");
            screenShot.setSelected(false);
            if (useSurface) {
                DJIVideoStreamDecoder.getInstance().changeSurface(videostreamPreviewSh.getSurface());
            }

        } else {
            screenShot.setText("B/C is ON");
            screenShot.setSelected(true);
            if (useSurface) {
                DJIVideoStreamDecoder.getInstance().changeSurface(null);
            }

        }
    }

    /*private void displayPath(String path) {
        path = path + "\n\n";
        if (pathList.size() < 6) {
            pathList.add(path);
        } else {
            pathList.remove(0);
            pathList.add(path);
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < pathList.size(); i++) {
            stringBuilder.append(pathList.get(i));
        }
        savePath.setText(stringBuilder.toString());
    }

    private void doSwitchToBroadcaster(boolean broadcaster) {
        final int currentHostCount = mUidsList.size();
        final int uid = config().mUid;
        Log.d(TAG, "doSwitchToBroadcaster " + currentHostCount + " " + (uid & 0XFFFFFFFFL) + " " + broadcaster);

        if (broadcaster) {
            doConfigEngine(Constants.CLIENT_ROLE_BROADCASTER);
        } else {
            doConfigEngine(Constants.CLIENT_ROLE_AUDIENCE);
        }
    }

    private void doConfigEngine(int cRole) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        /*int prefIndex = pref.getInt(ConstantApp.PrefManager.PREF_PROPERTY_PROFILE_IDX, ConstantApp.DEFAULT_PROFILE_IDX);
        if (prefIndex > ConstantApp.VIDEO_PROFILES.length - 1) {
            prefIndex = ConstantApp.DEFAULT_PROFILE_IDX;
        }
        int vProfile = ConstantApp.DEFAULT_PROFILE_IDX;

        worker().configEngine(cRole, vProfile);
        Log.d(TAG,"configEngine() - worker thread asynchronously " + cRole + " " + vProfile);
    }*/

}
