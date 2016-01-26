package eu.danman.zidostreamer.zidostreamer;


import android.content.Context;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.mstar.android.camera.MCamera;
import com.mstar.android.tv.TvCommonManager;
import com.mstar.android.tvapi.common.TvManager;
import com.mstar.android.tvapi.common.exception.TvCommonException;
import com.mstar.android.tvapi.common.vo.TvOsType;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

@SuppressWarnings("deprecation")
class StreamSession {

    private final static String LOG_TAG = StreamSession.class.getSimpleName();
    private final Context context;
    private final StreamSessionCallback callback;

    private android.hardware.Camera mCamera;
    private MediaRecorder mMediaRecorder;
    private FfmpegProcess ffmpegProcess;

    interface StreamSessionCallback {
        void onComplete();
    }

    public StreamSession(Context context, StreamSessionCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void start() throws Exception {
        startStreaming();
    }

    public void stopAndDestroy() {
        Log.e(LOG_TAG, "stopAndDestroy");
        releaseMediaPipeline();
    }

    private static void changeInputSource(TvOsType.EnumInputSource eis)
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

    private static synchronized void enableHDMI() throws TvCommonException
    {
        boolean bRet;
        int i=0;
        do {
            changeInputSource(TvOsType.EnumInputSource.E_INPUT_SOURCE_STORAGE);
            changeInputSource(TvOsType.EnumInputSource.E_INPUT_SOURCE_HDMI);
            bRet = TvManager.getInstance().getPlayerManager().isSignalStable();
        } while( !bRet && i++<3 );

        if(!bRet)
            Log.e(LOG_TAG, "enableHDMI: signal is not stable");
    }

    /** A safe way to get an instance of the Camera object. */
    private static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(5); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    private void startStreaming()
           throws Exception
    {

        ffmpegProcess = new FfmpegProcess(context);
        try {
            ffmpegProcess.startFfmpegProcessUDP("224.0.0.0", "10000");
        } catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            throw e;
        }

        //make a pipe containing a read and a write parcelfd
        ParcelFileDescriptor[] camcoderPipe;
        try {
            camcoderPipe = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            throw e;
        }
        //get a handle to your read and write fd objects.
        final FileDescriptor camcoderReadFd = camcoderPipe[0].getFileDescriptor();
        final FileDescriptor camcoderWriteFd = camcoderPipe[1].getFileDescriptor();

        InputOutputPumper encoder2ffmpegPumper = new InputOutputPumper(
                new FileInputStream(camcoderReadFd),
                ffmpegProcess.getFfmpegInputFeed(),
                new InputOutputPumper.InputOutputPumperCallback() {
                    @Override
                    public void onComplete() {
                        Log.e(LOG_TAG, "onComplete");
                        if(callback!=null)
                            callback.onComplete();
                    }
                });
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
            releaseMediaPipeline();
            throw new IOException(e.getMessage(),e);
        } catch (IOException e) {
            Log.d(LOG_TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaPipeline();
            throw e;
        }
        mMediaRecorder.start();
    }


    private void releaseMediaPipeline() {
        Log.e(LOG_TAG, "releaseMediaPipeline");
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
        stopFFMPEG();
    }

    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            Log.e(LOG_TAG, "releaseMediaRecorder");
            mMediaRecorder.stop();
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    private void releaseCamera(){
        if (mCamera != null){
            Log.e(LOG_TAG, "releaseCamera");
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    private void stopFFMPEG(){
        if (ffmpegProcess != null){
            Log.e(LOG_TAG, "stopFFMPEG");
            ffmpegProcess.stopAndDestroy();
            ffmpegProcess = null;
        }
    }
}
