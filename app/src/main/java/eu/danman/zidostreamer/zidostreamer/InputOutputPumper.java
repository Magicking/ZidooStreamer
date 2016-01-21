package eu.danman.zidostreamer.zidostreamer;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class InputOutputPumper extends Thread {
    final InputStream inputStream;
    final OutputStream outputStream;
    final String LOG_TAG = InputOutputPumper.class.getSimpleName();

    public InputOutputPumper(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    @Override
    public void run() {
        try {
            byte[] buffer = new byte[32*1024];
            while (true) {
                int read = inputStream.read(buffer);
                outputStream.write(buffer, 0, read);
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            //TODO: onDestroy();
        }
    }
}
