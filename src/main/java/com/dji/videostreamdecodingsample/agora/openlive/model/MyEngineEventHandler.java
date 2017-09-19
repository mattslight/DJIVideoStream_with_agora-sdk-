package com.dji.videostreamdecodingsample.agora.openlive.model;

import android.content.Context;
import android.util.Log;

import io.agora.rtc.IRtcEngineEventHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class MyEngineEventHandler {
    public MyEngineEventHandler(Context ctx, EngineConfig config) {
        this.mContext = ctx;
        this.mConfig = config;
    }

    private final EngineConfig mConfig;

    private final Context mContext;

    private static final String TAG = MyEngineEventHandler.class.getSimpleName();

    private final ConcurrentHashMap<AGEventHandler, Integer> mEventHandlerList = new ConcurrentHashMap<>();

    public void addEventHandler(AGEventHandler handler) {
        this.mEventHandlerList.put(handler, 0);
    }

    public void removeEventHandler(AGEventHandler handler) {
        this.mEventHandlerList.remove(handler);
    }

    final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        private final Logger log = LoggerFactory.getLogger(this.getClass());

        @Override
        public void onFirstRemoteVideoDecoded(int uid, int width, int height, int elapsed) {
            log.debug("onFirstRemoteVideoDecoded " + (uid & 0xFFFFFFFFL) + width + " " + height + " " + elapsed);

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                //handler.onFirstRemoteVideoDecoded(uid, width, height, elapsed);
            }
        }

        @Override
        public void onFirstLocalVideoFrame(int width, int height, int elapsed) {
            Log.d(TAG,"onFirstLocalVideoFrame " + width + " " + height + " " + elapsed);
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            Log.d(TAG,"another broadcaster joined channel");
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            // FIXME this callback may return times
            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                //handler.onUserOffline(uid, reason);
            }
        }

        @Override
        public void onUserMuteVideo(int uid, boolean muted) {
        }

        @Override
        public void onRtcStats(RtcStats stats) {
        }


        @Override
        public void onLeaveChannel(RtcStats stats) {

        }

        @Override
        public void onLastmileQuality(int quality) {
            Log.d(TAG, "onLastmileQuality " + quality);
        }

        @Override
        public void onError(int err) {
            super.onError(err);
            Log.d(TAG, "onError " + err);
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

        public void onRejoinChannelSuccess(String channel, int uid, int elapsed) {
            Log.d(TAG, "onRejoinChannelSuccess " + channel + " " + uid + " " + elapsed);
        }

        public void onWarning(int warn) {
            Log.d(TAG, "onWarning " + warn);
        }
    };

}
