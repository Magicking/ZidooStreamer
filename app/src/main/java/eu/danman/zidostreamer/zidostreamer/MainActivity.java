package eu.danman.zidostreamer.zidostreamer;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class MainActivity extends AppCompatActivity {

    private final String LOG_TAG = MainActivity.class.getSimpleName();
    private SurfaceView surfaceView = null;
    private SurfaceHolder mSurfaceHolder = null;
    private SurfaceHolder.Callback	callback = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceView = (SurfaceView) this.findViewById(R.id.surfaceView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_start_publishing) {
            Intent service = new Intent(this, StreamService.class);
            if( null == startService(service) ) {
                Log.e(LOG_TAG, "Failed to start service");
            }
        } else if (id == R.id.action_stop_publishing) {
            Intent service = new Intent(this, StreamService.class);
            if( !stopService(service) ) {
                Log.e(LOG_TAG, "Failed to stop service");
            }
        }

        return super.onOptionsItemSelected(item);
    }

}
