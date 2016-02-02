package eu.danman.zidostreamer.zidostreamer;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class FfmpegProcess {

    private static String ffmpegBinPath;
    final private static String LOG_TAG = FfmpegProcess.class.getSimpleName();

    private ProcessStderrLogger stderrLogger;
    private Process ffmpegProcess;


    public FfmpegProcess(Context context) {
        extractFfmpegBin(context);
    }

    public static void extractFfmpegBin(Context context) {
        String ffmpegBinPath = context.getFilesDir().getAbsolutePath() + "/ffmpeg";
        File ffmpegBinFile = new File(ffmpegBinPath);
        if (!ffmpegBinFile.exists()) {
            File ffmpegBinDir = new File(ffmpegBinFile.getParent());
            if (!ffmpegBinDir.exists()) {
                if(!ffmpegBinDir.mkdirs() )
                    throw new RuntimeException("Failed to create "+ffmpegBinDir.toString());
            }

            extractAssetFile("ffmpeg", ffmpegBinPath, context);

            ffmpegBinFile = new File(ffmpegBinPath);
            if(!ffmpegBinFile.setExecutable(true)) {
                throw new RuntimeException("Failed to set executable "+ffmpegBinDir.toString());
            }
        }
        FfmpegProcess.ffmpegBinPath = ffmpegBinPath;
    }


    private static synchronized void extractAssetFile(String assetPath, String localPath, Context context) {
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


    public void startFfmpegProcessUDP(String ip, String port) throws IOException {
        startFfmpegProcess( buildFfmpegCmdUDP(ip, port) );
    }


    public void startFfmpegProcessRTMP(String url) throws IOException {
        startFfmpegProcess( buildFfmpegCmdRTMP(url) );
    }


    private void startFfmpegProcess(String ffmpegCmd) throws IOException {
        if( ffmpegProcess != null ) {
            throw new IOException("Fmpeg process already started");
        }
        Log.d(LOG_TAG, ffmpegCmd);
        ffmpegProcess = Runtime.getRuntime().exec(ffmpegCmd);
        stderrLogger = new ProcessStderrLogger(ffmpegProcess);
    }


    private String buildFfmpegCmdUDP(String ip, String port) {
        return
                ffmpegBinPath
                        + " -i - "
                        + " -strict experimental -codec:v copy -codec:a copy -bsf:v dump_extra"
                        + " -f mpegts udp://"+ip+":"+port;
    }


    private String buildFfmpegCmdRTMP(String url) {
        return
                ffmpegBinPath
                        + " -i - -strict experimental "
                        + " -codec:v copy"
                        + " -codec:a aac -b:a 128k"
                        + " -f flv " + url;
    }


    public OutputStream getFfmpegInputFeed() {
        return ffmpegProcess.getOutputStream();
    }


    public void stopAndDestroy() {
        Log.d(LOG_TAG,"stopAndDestroy thread "+Thread.currentThread().getId());

        if( ffmpegProcess != null ) {
            ffmpegProcess.destroy();
            ffmpegProcess = null;
        }

        if( stderrLogger != null ) {
            try {
                stderrLogger.join(1000);
            } catch (InterruptedException e ) {
                Log.e(LOG_TAG, e.getMessage(), e);
            }

            if( stderrLogger.isAlive() )
                Log.e(LOG_TAG,"stderrLogger is still alive");
            stderrLogger = null;
        }
    }
}
