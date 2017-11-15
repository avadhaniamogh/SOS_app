package health.wireless.sbu.org.health;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    private SensorManager mSensorManager;
    private Sensor senAccelerometer;
    LocationManager locationManager;
    private Sensor magnetometer;
    private Double previousAcceleration = null;
    private boolean isDecreasing = false;
    private boolean hasHitGround = false;
    private long hitTime;
    private long decreaseTime;

    @BindView(R.id.x)
    TextView accelerometer_x_textview;
    @BindView(R.id.y)
    TextView accelerometer_y_textview;
    @BindView(R.id.z)
    TextView accelerometer_z_textview;
    @BindView(R.id.azimuth)
    TextView azimuth_textview;
    @BindView(R.id.pitch)
    TextView pitch_textview;
    @BindView(R.id.roll)
    TextView roll_textview;

    private String TAG = "Health App";

    float[] mGravity = null;
    float[] mGeomagnetic = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        senAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_UI);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor mySensor = sensorEvent.sensor;

        float azimuth = 0;
        float pitch = 0;
        float roll = 0;

        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            mGravity = sensorEvent.values;

            final float x = sensorEvent.values[0];
            final float y = sensorEvent.values[1];
            final float z = sensorEvent.values[2];

            try {
                // get the path to sdcard
                File sdcard = Environment.getExternalStorageDirectory();
                // to this path add a new directory path
                File dir = new File(sdcard.getAbsolutePath() + "/Health/");
                // create this directory if not already created
                dir.mkdir();
                // create the file in which we will write the contents
                File file = new File(dir, "Acc.txt");
                file.createNewFile();
                FileOutputStream fOut = new FileOutputStream(file, true);
                OutputStreamWriter osw = new OutputStreamWriter(fOut);
                osw.write("x: " + x + ", " + "y: " + y + ", " + "z: " + z);
                osw.flush();
                osw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "Values " + "x: " + x + ", " + "y: " + y + ", " + "z: " + z);

            // Calculate acceleration
            double acceleration = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
            // initializing previousAcceleration
            if (previousAcceleration == null) {
                previousAcceleration = acceleration;
            } else {
                // If device has not hit the ground
                if(!hasHitGround) {
                    // Detecting fall
                    if ((previousAcceleration - acceleration) > 3 && !isDecreasing) {
                        isDecreasing = true;
                        decreaseTime = System.currentTimeMillis();
                        Log.d(TAG, "Events Detecting fall" + "previous: " + previousAcceleration + " current: " + acceleration + " current time: " + System.currentTimeMillis());
                        // Detecting spike
                    } else if (acceleration - previousAcceleration > 3 && isDecreasing) {
                        // Wait for 200 ms
                        if(System.currentTimeMillis() - decreaseTime > 20) {
                            Log.d(TAG, "Events Detecting spike" + "previous: " + previousAcceleration + " current: " + acceleration + " current time: " + System.currentTimeMillis());
                            hasHitGround = true;
                            hitTime = System.currentTimeMillis();
                            // Reset isDecreasing
                        } else {
                            Log.d(TAG, "Events Reset decreasing" + "previous: " + previousAcceleration + " current: " + acceleration + " current time: " + System.currentTimeMillis());
                            isDecreasing = false;
                        }
                    }
                    // Waiting for 2000ms
                } else {
                    if(System.currentTimeMillis() - hitTime > 2000) {
                        if(Math.abs(previousAcceleration - acceleration) < 0.5) {
                            Log.d(TAG, "Events Raise event" + "previous: " + previousAcceleration + " current: " + acceleration + " current time: " + System.currentTimeMillis());
                            hasHitGround = false;
                            isDecreasing = false;
                        }
                    }
                }
                previousAcceleration = acceleration;
            }

            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                public void run() {
                    if (accelerometer_x_textview != null) {
                        String x_str = Float.toString(x);
                        accelerometer_x_textview.setText(x_str);
                    }

                    if (accelerometer_y_textview != null) {
                        String y_str = Float.toString(y);
                        accelerometer_y_textview.setText(y_str);
                    }

                    if (accelerometer_z_textview != null) {
                        String z_str = Float.toString(z);
                        accelerometer_z_textview.setText(z_str);
                    }
                }
            });
        }

        if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = sensorEvent.values;
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                azimuth = orientation[0]; // orientation contains: azimut, pitch and roll
                pitch = orientation[1];
                roll = orientation[2];
            }
        }

        Log.d(TAG, "Rotation " + "azimuth: " + azimuth + ", " + "pitch: " + pitch + ", " + "roll: " + roll);

        Handler handler = new Handler(Looper.getMainLooper());
        final float finalAzimuth = azimuth;
        final float finalPitch = pitch;
        final float finalRoll = roll;
        handler.post(new Runnable() {
            public void run() {
                if (accelerometer_x_textview != null) {
                    String azimuth_str = Float.toString(finalAzimuth);
                    azimuth_textview.setText(azimuth_str);
                }

                if (accelerometer_y_textview != null) {
                    String pitch_str = Float.toString(finalPitch);
                    pitch_textview.setText(pitch_str);
                }

                if (accelerometer_z_textview != null) {
                    String roll_str = Float.toString(finalRoll);
                    roll_textview.setText(roll_str);
                }
            }
        });

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }
}
