package eu.danman.zidostreamer.zidostreamer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.util.Log;

import com.mstar.android.camera.MCamera;
import com.mstar.android.tv.TvCommonManager;
import com.mstar.android.tvapi.common.TvManager;
import com.mstar.android.tvapi.common.exception.TvCommonException;
import com.mstar.android.tvapi.common.vo.TvOsType;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


public class StreamService extends Service {

    String ffmpegBin;
    android.hardware.Camera mCamera;
    MediaRecorder mMediaRecorder;
    Process ffmpegProcess;

    final static String LOG_TAG = StreamService.class.getSimpleName();

    public StreamService() {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    @Override
    public void onCreate() {
        String myFilesPath = getApplicationContext().getFilesDir().getAbsolutePath();
        ffmpegBin = myFilesPath + "/ffmpeg";
        extractFfmpegBin(myFilesPath);
    }

    private void extractFfmpegBin(String myFilesPath) {
        File fmBin = new File(ffmpegBin);
        if (!fmBin.exists()) {
            File myPathDir = new File(myFilesPath);
            if (!myPathDir.exists()) {
                myPathDir.mkdirs();
            }

            copyFile("ffmpeg", ffmpegBin, this);

            fmBin = new File(ffmpegBin);
            fmBin.setExecutable(true);
        }
    }

    private static void copyFile(String assetPath, String localPath, Context context) {
        try {
            AssetManager am = context.getAssets();
            InputStream in = am.open(assetPath);
            FileOutputStream out = new FileOutputStream(localPath);
            int read;
            byte[] buffer = new byte[32*1024];
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.close();
            in.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "CopyFile", e);
            throw new RuntimeException(e);
        }
    }

    public static void changeInputSource(TvOsType.EnumInputSource eis)
    {
        TvCommonManager commonService = TvCommonManager.getInstance();

        if (commonService != null) {
            TvOsType.EnumInputSource currentSource = commonService.getCurrentInputSource();
            if (currentSource != null) {
                if (currentSource.equals(eis)) {
                    return;
                }
                commonService.setInputSource(eis);
            }

        }

    }

    public static synchronized boolean enableHDMI()
    {
        boolean bRet = false;
        try {
            changeInputSource(TvOsType.EnumInputSource.E_INPUT_SOURCE_STORAGE);
            changeInputSource(TvOsType.EnumInputSource.E_INPUT_SOURCE_HDMI);
            bRet = TvManager.getInstance().getPlayerManager().isSignalStable();
        }
        catch (TvCommonException e) {
            Log.e(LOG_TAG,e.getMessage(),e);
        }
        return bRet;
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(5); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    private String buildFfmpegCmdUDP(String ip, String port) {
        return
                ffmpegBin
                + " -i - "
                + " -strict experimental -codec:v copy -codec:a copy -bsf:v dump_extra"
                + " -f mpegts udp://"+ip+":"+port;
    }

    private String buildFfmpegCmdRTMP(String url) {
        return
                ffmpegBin
                + " -i - "
                + " -strict experimental -codec:a copy -bsf:a aac_adtstoasc -codec:v copy"
                + " -f flv " + url;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        releaseMediaPipeline();

        // Start ffmpeg
        try {
            String ffmpegCmd = buildFfmpegCmdUDP("224.0.0.0", "10000");
            Log.d(LOG_TAG, ffmpegCmd);
            ffmpegProcess = Runtime.getRuntime().exec(ffmpegCmd);
            new ProcessStderrLogger(ffmpegProcess);
        } catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            return Service.START_NOT_STICKY;
        }

        //make a pipe containing a read and a write parcelfd
        ParcelFileDescriptor[] camcoderPipe;
        try {
            camcoderPipe = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            return Service.START_NOT_STICKY;
        }
        //get a handle to your read and write fd objects.
        FileDescriptor camcoderReadFd = camcoderPipe[0].getFileDescriptor();
        FileDescriptor camcoderWriteFd = camcoderPipe[1].getFileDescriptor();;

        InputOutputPumper encoder2ffmpegPumper = new InputOutputPumper(
                new FileInputStream(camcoderReadFd),
                ffmpegProcess.getOutputStream());
        encoder2ffmpegPumper.start();


        // initialize recording hardware
        enableHDMI();
        mCamera = getCameraInstance();
        Camera.Parameters camParams = mCamera.getParameters();
        camParams.set(MCamera.Parameters.KEY_TRAVELING_RES, MCamera.Parameters.E_TRAVELING_RES_1920_1080);
        camParams.set(MCamera.Parameters.KEY_TRAVELING_MODE, MCamera.Parameters.E_TRAVELING_ALL_VIDEO);
        camParams.set(MCamera.Parameters.KEY_TRAVELING_MEM_FORMAT, MCamera.Parameters.E_TRAVELING_MEM_FORMAT_YUV422_YUYV);
        camParams.set(MCamera.Parameters.KEY_MAIN_INPUT_SOURCE, MCamera.Parameters.MAPI_INPUT_SOURCE_HDMI);
        camParams.set(MCamera.Parameters.KEY_TRAVELING_FRAMERATE, 30);
        camParams.set(MCamera.Parameters.KEY_TRAVELING_SPEED, MCamera.Parameters.E_TRAVELING_SPEED_FAST);
        mCamera.setParameters(camParams);

        // Step 1: Unlock and set camera to MediaRecorder
        mMediaRecorder = new MediaRecorder();
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        //mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

        // set TS
        mMediaRecorder.setOutputFormat(8);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
        mMediaRecorder.setAudioSamplingRate(44100);
        mMediaRecorder.setAudioEncodingBitRate(128 * 1024);

        mMediaRecorder.setVideoSize(1920, 1080);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoEncodingBitRate(2000 * 1024);

        // Step 4: Set output file
        mMediaRecorder.setOutputFile(camcoderWriteFd);

        // Step 5: Set the preview output
        //mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());

        // Step 6: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(LOG_TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
        } catch (IOException e) {
            Log.d(LOG_TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
        }
        mMediaRecorder.start();

        return Service.START_STICKY;

    }

    public void onDestroy() {
        releaseMediaPipeline();
    }

    private void releaseMediaPipeline() {
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
        stopFFMPEG();
    }

    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    private void stopFFMPEG(){
        if (ffmpegProcess != null){
            ffmpegProcess.destroy();
            ffmpegProcess = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
