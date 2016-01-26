package eu.danman.zidostreamer.zidostreamer;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


class ProcessStderrLogger extends Thread {
    private final Process process;

    public ProcessStderrLogger(Process process) {
        this.process = process;
        start();
    }

    @Override
    public void run() {
        final BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        final String LOG_TAG = process.toString();
        try {
            while(isProcessRunning()) {
                String log = stderr.readLine();
                if ((log != null) && (log.length() > 0)){
                    Log.d(LOG_TAG, log);
                } else {
                    sleep(100);
                }
            }
        } catch (InterruptedException | IOException e) {
            Log.d(LOG_TAG, e.getMessage(), e);
        }
    }

    private boolean isProcessRunning(){
        try {
            process.exitValue();
        } catch (IllegalThreadStateException e) {
            return true;
        }
        return false;
    }
}
