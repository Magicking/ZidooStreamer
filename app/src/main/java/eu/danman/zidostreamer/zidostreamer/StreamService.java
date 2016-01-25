package eu.danman.zidostreamer.zidostreamer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.StrictMode;
import android.util.Log;


public class StreamService extends Service {

    StreamSession session = null;
    final static String LOG_TAG = StreamService.class.getSimpleName();


    public StreamService() {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }


    @Override
    public void onCreate() {
        FfmpegProcess.extractFfmpegBin(getApplicationContext());
        checkAndStopCurrentSession();
        super.onCreate();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        checkAndStopCurrentSession();
        try {
            session = new StreamSession(getApplicationContext(), new StreamSession.StreamSessionCallback() {
                @Override
                public void onComplete() {
                    stopSelf();
                }
            });
            session.start();
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            checkAndStopCurrentSession();
            return Service.START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }


    @Override
    public void onDestroy() {
        Log.e(LOG_TAG, "onDestroy");
        checkAndStopCurrentSession();
        super.onDestroy();
    }


    private void checkAndStopCurrentSession() {
        if (session != null) {
            session.stopAndDestroy();
            session = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
