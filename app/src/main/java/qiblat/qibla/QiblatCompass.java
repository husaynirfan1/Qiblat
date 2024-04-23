package qiblat.qibla;/*
 * *
 *  * Created by Husayn on 22/10/2021, 5:04 PM
 *  * Copyright (c) 2021 . All rights reserved.
 *  * Last modified 22/10/2021, 2:29 PM
 *
 */

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.IBinder;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import java.util.ArrayDeque;

public class QiblatCompass extends Service implements SensorEventListener {

    public static CompassView arrow;
    public static TextView tvHeading;
    // device sensor manager
    private static SensorManager mSensorManager;
    Context context;
    Location userLoc = new Location("service provider");

    private TextView kaabahheading;
    Sensor accelerometer;
    Sensor magnetometer;
    Float azimut;



    public QiblatCompass(Context context, CompassView needle, TextView heading, double longi, double lati, double alti, TextView kaabahbear) {
        kaabahheading = kaabahbear;
        arrow = needle;

        // TextView that will tell the user what degree is he heading
        tvHeading = heading;
        userLoc.setLongitude(longi);
        userLoc.setLatitude(lati);
        userLoc.setAltitude(alti);

        mSensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (accelerometer != null && magnetometer != null) {
            // for the system's orientation sensor registered listeners
            mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        } else {
            Toast.makeText(context, "Not Supported", Toast.LENGTH_SHORT).show();
        }
        // initialize your android device sensor capabilities
        this.context = context;


    }

    @Override
    public void onCreate() {
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        super.onCreate();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
        mSensorManager = null;
    }

    public void Re_Register(){
        super.onDestroy();
        super.onCreate();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    float[] mGravity;
    float[] mGeomagnetic;
    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values;
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                azimut = orientation[0]; // orientation contains: azimut, pitch and roll

                AngleLowpassFilter lowpassFilter = new AngleLowpassFilter();
                lowpassFilter.add(azimut);
                azimut = lowpassFilter.average();
                azimut = azimut * 180 / (float)Math.PI;

                GeomagneticField geoField = new GeomagneticField(
                        (float) userLoc.getLatitude(),
                        (float) userLoc.getLongitude(),
                        (float) userLoc.getAltitude(),
                        System.currentTimeMillis());
                azimut += geoField.getDeclination(); // converts magnetic north to true north
                Location destinationLoc = new Location("service Provider");

                destinationLoc.setLatitude(21.4224779); //kaaba latitude setting
                destinationLoc.setLongitude(39.8251832); //kaaba longitude setting
                float bearing = userLoc.bearingTo(destinationLoc); // (it's already in degrees)
                float direction = azimut - bearing;
                if (direction < 0) {
                    direction = direction + 360;
                }
                if (bearing < 0) {
                    bearing = bearing + 360;
                }
                if (azimut < 0) {
                    azimut = azimut + 360;
                }

                arrow.setPhysical(0.5f, 15, 2000);
                arrow.rotationUpdate(-direction, true);
                arrow.invalidate();

                float uponRotation = -arrow.getRotation()+bearing;
                String a = String.format("%.2f", uponRotation);
                String k = String.format("%.2f", bearing);
                tvHeading.setText(a + " °");
                kaabahheading.setText(k + " °");

            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
    public class AngleLowpassFilter {

        private final int LENGTH = 10;

        private float sumSin, sumCos;

        private ArrayDeque<Float> queue = new ArrayDeque<Float>();

        public void add(float radians){

            sumSin += (float) Math.sin(radians);

            sumCos += (float) Math.cos(radians);

            queue.add(radians);

            if(queue.size() > LENGTH){

                float old = queue.poll();

                sumSin -= Math.sin(old);

                sumCos -= Math.cos(old);
            }
        }

        public float average(){

            int size = queue.size();

            return (float) Math.atan2(sumSin / size, sumCos / size);
        }
    }
}
