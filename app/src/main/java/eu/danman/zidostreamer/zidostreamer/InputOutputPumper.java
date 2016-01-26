package eu.danman.zidostreamer.zidostreamer;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class InputOutputPumper extends Thread {
    final private InputStream inputStream;
    final private OutputStream outputStream;
    final private String LOG_TAG = InputOutputPumper.class.getSimpleName();
    final private InputOutputPumperCallback callback;

    public interface InputOutputPumperCallback {
        void onComplete();
    }

    public InputOutputPumper(InputStream inputStream, OutputStream outputStream, InputOutputPumperCallback callback) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            int read;
            byte[] buffer = new byte[32*1024];
            while ( (read = inputStream.read(buffer)) > 0 ) {
                outputStream.write(buffer, 0, read);
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            if(callback!=null)
                callback.onComplete();
        }
    }
}
