package com.aalto.balcazar.jj.sensors_reading;

import android.Manifest;
//import android.annotation.TargetApi;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.abs;
import static java.lang.Math.cos;
import static java.lang.Math.floor;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

//Telephony
import android.telephony.CellInfo;

import android.telephony.PhoneStateListener;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;


public class SensorsActivity extends AppCompatActivity implements SensorEventListener {

    //Sensor Manager
    private SensorManager mSensorManager;
    //Telephony Manager
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    //GPS Manager
    private LocationManager locationManager;
    private LocationListener locationListenerGPS;
    private LocationListener locationListenerNetwork;
    private GpsStatus.Listener GpsListener;
    private GpsStatus.NmeaListener NmeaListener_1;
    //Sensors
    private Sensor mLight;
    private Sensor mMag;
    private boolean Day = true;
    private float light;
    private float lightCurrent;
    private float lightPrev;
    private float[] Mag;
    private double MagAv;
    private double MagLat;
    private double MagAvCurrent;
    private double MagAvPrev;
    private double IncMagCurrent;
    private double IncMagPrev;
    //GPS - CELL
    private double Lat;
    private double Lng;
    private int Sat = -1;
    private float Sat_snr = 0;
    private float SatCurrent;
    private float SatPrev;
    private float SnrCurrent;
    private float SnrPrev;
    private int Cell_gsm;
    private int Cell_cdma;
    private float Cell_lte;
    private float CellCurrent;
    private float CellPrev;
    private boolean locationready;
    //Aux
    private long t;
    private float alpha = (float) 0.5;
    private boolean flag = true;
    private double a = 6378.1370;
    private double b = 6356.7523;
    private double B0 = 31.2;
    private double r;
    private double R_;
    private boolean Day_Init;
    private boolean out = false;
    private boolean map = false;
    //Probabilities
    private float M1 = 20;
    private float L1 = 0;
    private float L2 = 2500;
    private float S1 = 4;//2
    private float S2 = 20;//18
    private float C1 = 0;
    private float C2 = 100;
    private float SN1 = 0;
    private float SN2 = 36;
    private double P_out = 0;
    private double P_sum = 0;
    private double P_End = 0;
    private double P_EndPrev = -1;
    private double P_light_OUT;
    private double P_light_IN;
    private double P_mag_OUT;
    private double P_mag_IN;
    private double P_sat_OUT;
    private double P_sat_IN;
    private double P_cell_OUT;
    private double P_cell_IN;
    private double P_snr_OUT;
    private double P_snr_IN;
    private List<Float> P_list;
    private int cont = 0;


    //URL
    static String file_url = null;
    String uri = null;

    //Nmea
    String Nmea_Info = null;
    long t_nmea = 0;
    float Snr_1 = 0;
    float Snr_2 = 0;
    float Snr_3 = 0;
    float Snr_4 = 0;
    int cont_snr = 0;

    float Snr = (float) -1;
    String aux_nmea_1 = null;
    String aux_nmea_2 = null;
    String aux_nmea_3 = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensors);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mMag = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        //Set Telephony Manager
        telephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        //Set GPS Manager
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);


        P_list = new ArrayList<Float>();

        t = System.currentTimeMillis();
        setupSwitchButton();
        setupGPS();
        //Timer - Day
        //setupTimer();
        Day_Init = false;
        //Cell
        setupCell();

    }


    private void setupCell() {

        phoneStateListener = new PhoneStateListener() {

            @TargetApi(Build.VERSION_CODES.M)
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {

                String ssignal = signalStrength.toString();
                String[] parts = ssignal.split(" ");
                int dbm = -110;
                if ( telephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_LTE){
                    dbm = Integer.parseInt(parts[8])*2-113;

                }
                else{
                    if (signalStrength.getGsmSignalStrength() != 99) {

                        dbm  = -113 + 2*signalStrength.getGsmSignalStrength();
                    }
                }

                Cell_lte = (float) (5.0*(dbm+110)/3.0); //map [-110,150] to (0,100)
                //Cell_lte = 50; // DANI

            }

        };
    }


    private void setupTimer() {
        Runnable Runnable_Day = new Runnable() {
            public void run() {
                Calendar cal = Calendar.getInstance();
                if (locationready)
                    Day = sun(Lat, Lng, cal);
            }
        };

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(Runnable_Day, 0, 1, TimeUnit.MINUTES);

    }


    private void setupGPS() {

        locationListenerNetwork = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.

                Lat = location.getLatitude();
                Lng = location.getLongitude();
                locationready = true;
                Sat = 1;
                uri = String.format(Locale.ENGLISH, "geo:%f,%f", Lat, Lng);
                file_url  = "https://maps.googleapis.com/maps/api/directions/json?origin=Leppavaaranaukio4Espoo&destination=Otakaari1Espoo&mode=walking&key=AIzaSyBHOW7vFZFB39qxVVoeUkAjpo_8TW5gh2k";

                //Initialize Day
                if (!Day_Init) {
                    Calendar cal = Calendar.getInstance();
                    Day = sun(Lat, Lng, cal);
                    Day_Init = true;
                    Toast.makeText(getApplicationContext(), "Location Ready", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
                Sat = -1;
                Snr = -1;
                locationready = false;
            }
        };
        //////////////////////////////
        locationListenerGPS = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {

            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };
        ///////////NMEA///////////////
        NmeaListener_1 = new GpsStatus.NmeaListener() {

            @Override
            public void onNmeaReceived(long timestamp, String nmea) {
                String[] Nmea_Info = nmea.split(",");
                if ("$GPGSV".equals(Nmea_Info[0])) {

                    switch (Integer.valueOf(Nmea_Info[1])) {
                        case 1:
                            Snr = snr(Nmea_Info);
                            break;
                        case 2:
                            switch (Integer.valueOf(Nmea_Info[2])){
                                case 1:
                                    Snr_1 = snr(Nmea_Info);
                                    break;
                                case 2:
                                    Snr_2 = snr(Nmea_Info);
                                    cont_snr = 0;
                                    if (Snr_1 != 0){
                                        cont_snr++;
                                    }
                                    if (Snr_2 != 0){
                                        cont_snr++;
                                    }
                                    if (cont_snr != 0) {
                                        Snr = (Snr_1 + Snr_2) / cont_snr;
                                    }
                                    else{
                                        Snr = 0;
                                    }
                                    break;
                                default:
                                    break;
                            }
                            Snr_2 = snr(Nmea_Info);
                            break;

                        case 3:
                            switch (Integer.valueOf(Nmea_Info[2])){
                                case 1:
                                    Snr_1 = snr(Nmea_Info);
                                    break;
                                case 2:
                                    Snr_2 = snr(Nmea_Info);
                                    break;
                                case 3:
                                    Snr_3 = snr(Nmea_Info);
                                    cont_snr = 0;
                                    if (Snr_1 != 0){
                                        cont_snr++;
                                    }
                                    if (Snr_2 != 0){
                                        cont_snr++;
                                    }
                                    if (Snr_3 != 0){
                                        cont_snr++;
                                    }
                                    if(cont_snr!=0) {
                                        Snr = (Snr_1 + Snr_2 + Snr_3) / cont_snr;
                                    }
                                    else{
                                        Snr = 0;
                                    }
                                    break;

                                default:
                                    break;
                            }
                            break;
                        case 4:
                            switch (Integer.valueOf(Nmea_Info[2])){
                                case 1:
                                    Snr_1 = snr(Nmea_Info);
                                    break;
                                case 2:
                                    Snr_2 = snr(Nmea_Info);
                                    break;
                                case 3:
                                    Snr_3 = snr(Nmea_Info);
                                    break;
                                case 4:
                                    Snr_4 = snr(Nmea_Info);
                                    cont_snr = 0;
                                    if (Snr_1 != 0){
                                        cont_snr++;
                                    }
                                    if (Snr_2 != 0){
                                        cont_snr++;
                                    }
                                    if (Snr_3 != 0){
                                        cont_snr++;
                                    }
                                    if (Snr_4 != 0){
                                        cont_snr++;
                                    }
                                    if(cont_snr!=0) {
                                        Snr = (Snr_1 + Snr_2 + Snr_3 + Snr_4) / cont_snr;
                                    }
                                    else{
                                        Snr = 0;
                                    }
                                    break;
                                default:
                                    break;
                            }
                            break;
                        default:
                            break;
                    }

                }

            }
        };
        //////////GPS Status/////////////
        GpsListener = new GpsStatus.Listener() {
            @Override
            public void onGpsStatusChanged(int event) {
                switch (event) {
                    case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                        GpsStatus gpsStatus = locationManager.getGpsStatus(null);
                        int MaxSat = gpsStatus.getMaxSatellites();
                        Sat = 0;
                        Sat_snr = 0;
                        Iterable<GpsSatellite> list = gpsStatus.getSatellites();
                        for (GpsSatellite satellite : list) {
                            if (satellite.usedInFix()) {
                                Sat++;
                                Sat_snr += satellite.getSnr();
                            }
                        }
                        Sat_snr = Sat_snr/Sat;
                        break;
                    default:
                        break;
                }
            }
        };
    }

    private float snr(String[] info){
        float Snr_aux = 0;
        int cont_abcd = 0;
        int Snr_a = 0;
        int Snr_b = 0;
        int Snr_c = 0;
        int Snr_d = 0;

        //only one message
        if (info[7].isEmpty()) {
            Snr_a = 0;
        }
        else{
            String[] parts = info[7].split("\\*");
            if (parts[0].isEmpty()) {
                Snr_a = 0;
            } else {
                Snr_a = Integer.valueOf(parts[0]);
                cont_abcd++;
            }

        }
        if(info.length > 11) {
            if (info[11].isEmpty()) {
                Snr_b = 0;
            } else {
                String[] parts = info[11].split("\\*");
                if (parts[0].isEmpty()) {
                    Snr_b = 0;
                } else {
                    Snr_b = Integer.valueOf(parts[0]);
                    cont_abcd++;
                }
            }
        }
        else{
            Snr_b = 0;
        }
        if(info.length > 15) {
            if (info[15].isEmpty()) {
                Snr_c = 0;
            } else {
                String[] parts = info[15].split("\\*");
                if (parts[0].isEmpty()) {
                    Snr_c = 0;
                } else {
                    Snr_c = Integer.valueOf(parts[0]);
                    cont_abcd++;
                }
            }
        }
        else{
            Snr_c = 0;
        }
        if(info.length > 19){
            if (info[19].isEmpty()) {
                Snr_d = 0;
            } else {
                String[] parts = info[19].split("\\*");
                if (parts[0].isEmpty()) {
                    Snr_d = 0;
                } else {
                    Snr_d = Integer.valueOf(parts[0]);
                    cont_abcd++;
                }
            }
        }
        else{
            Snr_d = 0;
        }
        if (cont_abcd != 0) {
            Snr_aux = (Snr_a + Snr_b + Snr_c + Snr_d) / cont_abcd;
        }
        else {
            Snr_aux = 0;
        }

        return Snr_aux;
    }

    private void setupSwitchButton() {

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //startActivity(new Intent(SensorsActivity.this, MapsActivity.class)); // go to maps
                if (locationready) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                    map = true;
                    startActivity(new Intent(SensorsActivity.this, MapsActivity.class ));
                } else {
                    Snackbar.make(view, "Location not Available", Snackbar.LENGTH_SHORT).setAction("Action", null).show();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_sensors, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        switch (event.sensor.getType()) {
            case Sensor.TYPE_MAGNETIC_FIELD:
                Mag = event.values.clone();
                MagAv = sqrt(pow(Mag[0], 2) + pow(Mag[1], 2) + pow(Mag[2], 2));
                //Toast.makeText(getApplicationContext(), String.valueOf(Mag[0]), Toast.LENGTH_SHORT).show();
                break;
            case Sensor.TYPE_LIGHT:
                light = event.values[0];
                //Toast.makeText(getApplicationContext(), String.valueOf(light), Toast.LENGTH_SHORT).show();
                break;
        }

        if (System.currentTimeMillis() - t >= 500) {

            main(); //Call the main function
            t = System.currentTimeMillis();
        }

    }

    private void main() {

        //Main function
        if (flag) {

            MagAvCurrent = MagAv;
            lightCurrent = light;
            CellCurrent = Cell_lte;
            SatCurrent = Sat;
            SnrCurrent = Snr;
            IncMagCurrent = 0;
            flag = false;

        } else {

            MagAvPrev = MagAvCurrent;
            lightPrev = lightCurrent;
            CellPrev = CellCurrent;
            SatPrev = SatCurrent;
            SnrPrev = SnrCurrent;
            MagAvCurrent = alpha * MagAvPrev + (1 - alpha) * MagAv;
            lightCurrent = alpha * lightPrev + (1 - alpha) * light;
            CellCurrent = alpha * CellPrev + (1 - alpha) * Cell_lte;
            SatCurrent = alpha * SatPrev + (1 - alpha) * Sat;
            SnrCurrent = alpha * SnrPrev + (1 - alpha) * Snr;

            r = sqrt((pow(a * a * cos(Math.toRadians(Lat)), 2) + pow(b * b * sin(Math.toRadians(Lat)), 2)) / (pow(a * cos(Math.toRadians(Lat)), 2) + pow(b * sin(Math.toRadians(Lat)), 2)));
            R_ = r / a;

            MagLat = (B0 / pow(R_, 3)) * sqrt(1 + 3 * pow(sin(Math.toRadians(Lat)), 2));

            IncMagPrev = IncMagCurrent;
            IncMagCurrent = alpha * IncMagPrev + (1 - alpha) * abs(MagAvCurrent - MagLat);

        }

        //Do the probabilities

        final ImageView image = (ImageView) findViewById(R.id.imageINOUT);

        // LIGHT

        if (lightCurrent > L2) {
            P_light_OUT = 0.06;
            P_light_IN = 0;
        } else {
            if (lightCurrent < L1) {
                P_light_OUT = 0;
                P_light_IN = 0.12;
            } else {
                P_light_OUT = 0.012 * pow(lightCurrent / 100 - L1 / 100, 0.5);
                P_light_IN = 0.000192 * pow(lightCurrent / 100 - L2 / 100, 2);
            }
        }

        // MAGNETIC FIELD

        if (IncMagCurrent > M1) {
            P_mag_OUT = 0.0375;
            P_mag_IN = 0.0625;
        } else {
            P_mag_OUT = 0.0625 - 0.00125 * IncMagCurrent;
            P_mag_IN = 0.0375 + 0.00125 * IncMagCurrent;
        }

        // CELL STRENGTH

        if (CellCurrent > C2) {
            P_cell_OUT = 0.2;
            P_cell_IN = 0;
        } else {
            if (CellCurrent < C1) {
                P_cell_OUT = 0;
                P_cell_IN = 0.2;
            } else {
                P_cell_OUT = 0.02 * (CellCurrent / 10 - C1 / 10);
                P_cell_IN = 0.2 - 0.02 * (CellCurrent / 10 - C1 / 10);
            }
        }

        // Satellites Availables
        if (SatCurrent > 0) {
            //Satellites Availables
            if (SatCurrent > S2) {
                P_sat_OUT = 0.0936;
                P_sat_IN = 0;
            } else {
                if (SatCurrent < S1) {
                    P_sat_OUT = 0;
                    P_sat_IN = 0.187;
                } else {
                    P_sat_OUT = 0.0234 * pow(SatCurrent - S1, 0.5);
                    P_sat_IN = 0.000732 * pow(SatCurrent - S2, 2);
                }
            }
            if (SnrCurrent > SN2) { //Signal-Noise Ratio
                P_snr_OUT = 0.0416;
                P_snr_IN = 0;
            } else {
                if (SnrCurrent < SN1) {
                    P_snr_OUT = 0;
                    P_snr_IN = 0.083;
                } else {
                    P_snr_OUT = 0.00694 * pow(SnrCurrent - SN1, 0.5);
                    P_snr_IN = 0.0000643 * pow(SnrCurrent - SN2, 2);
                }
            }

            //P_snr_OUT = 0.5;
            //P_snr_IN = 0.5;


            if (Day) //take into account everything
                if (0.5 * P_light_OUT * P_mag_OUT * P_cell_OUT * P_sat_OUT * P_snr_OUT + 0.5 * P_light_IN * P_mag_IN * P_cell_IN * P_sat_IN * P_snr_IN > 0)
                    P_out = 0.5 * P_light_OUT * P_mag_OUT * P_cell_OUT * P_sat_OUT * P_snr_OUT / (0.5 * P_light_OUT * P_mag_OUT * P_cell_OUT * P_sat_OUT * P_snr_OUT + 0.5 * P_light_IN * P_mag_IN * P_cell_IN * P_sat_IN * P_snr_IN);
                else
                    P_out = 1;
            else //take into account sat mag cell
                if (0.5 * P_mag_OUT * P_cell_OUT * P_sat_OUT * P_snr_OUT + 0.5 * P_mag_IN * P_cell_IN * P_sat_IN * P_snr_IN > 0)
                    P_out = 0.5 * P_mag_OUT * P_cell_OUT * P_sat_OUT * P_snr_OUT / (0.5 * P_mag_OUT * P_cell_OUT * P_sat_OUT * P_snr_OUT + 0.5 * P_mag_IN * P_cell_IN * P_sat_IN * P_snr_IN);
                else
                    P_out = 1;
        } else {
            //Satellites not availables
            if (Day) // take into account light mag cell
                if (0.5 * P_light_OUT * P_mag_OUT * P_cell_OUT + 0.5 * P_light_IN * P_mag_IN * P_cell_IN > 0)
                    P_out = 0.5 * P_light_OUT * P_mag_OUT * P_cell_OUT / (0.5 * P_light_OUT * P_mag_OUT * P_cell_OUT + 0.5 * P_light_IN * P_mag_IN * P_cell_IN);
                else
                    P_out = 1;
            else // take into account mag cell
                if (0.5 * P_mag_OUT * P_cell_OUT + 0.5 * P_mag_IN * P_cell_IN > 0)
                    P_out = 0.5 * P_mag_OUT * P_cell_OUT / (0.5 * P_mag_OUT * P_cell_OUT + 0.5 * P_mag_IN * P_cell_IN);
                else
                    P_out = 1;
        }

        //Final Probabilities


        P_sum += P_out;
        cont++;

        if (cont >= 5) {
            P_End = P_sum / cont;
            if (P_EndPrev >= 0) {
                P_End = P_End * 0.6 + P_EndPrev * 0.4;
            }
            P_EndPrev = P_End;
            P_sum = 0;
            cont = 0;
        }


        //P_list.remove(0);
        //P_out = 0.25 * P_list.get(P_list.size() - 1) + 0.25 * P_list.get(P_list.size() - 2) + 0.25 * P_list.get(P_list.size() - 3) + 0.25 * P_list.get(P_list.size() - 4);

        if (P_End > 0.5) {
            image.setImageResource(R.drawable.out);
            if (!out) {
                Toast.makeText(getApplicationContext(), "You are OUT", Toast.LENGTH_LONG).show();
                out = true;
            }

        } else {
            image.setImageResource(R.drawable.in);
            if (out){
                Toast.makeText(getApplicationContext(), "You are IN", Toast.LENGTH_LONG).show();
                out = false;
            }
            /*if (map){
                //intent to finish googlemaps
                //startActivity(new Intent(this, SensorsActivity.class));
                Intent backIntent = new Intent(SensorsActivity.this, SensorsActivity.class);
                backIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(backIntent);
                map = false;
            }*/
        }

        ((TextView) findViewById(R.id.TextView1)).setText(String.format("Light Sensor: %.2f | Day: %b", lightCurrent, Day));
        ((TextView) findViewById(R.id.TextView2)).setText(String.format("Magnetic Difference: %.2f\nCell Strength: %.2f", IncMagCurrent, CellCurrent));
        ((TextView) findViewById(R.id.TextView3)).setText(String.format("Number of Satellites: %.2f\nSignal-Noise Ratio: %.2f", SatCurrent, SnrCurrent));
//        ((TextView) findViewById(R.id.TextView3)).setText("NMEA 1:" + aux_nmea_1+ "\nNMEA 2: " + aux_nmea_2 + "\nNMEA 3: " + aux_nmea_3);
        ((TextView) findViewById(R.id.TextView4)).setText(String.format("Probability out: %.3f", P_End));


    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    protected void onResume() {
        // Register a listener for the sensor.
        super.onResume();
        mSensorManager.registerListener(this, mLight, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mMag, SensorManager.SENSOR_DELAY_NORMAL);
        //Telephony
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        //GPS Stuff
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListenerNetwork);//
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListenerGPS);
        locationManager.addNmeaListener(NmeaListener_1); //Nmea
        locationManager.addGpsStatusListener(GpsListener); //Satellites
    }

    @Override
    protected void onPause() {
        // Be sure to unregister the sensor when the activity pauses.
        super.onPause();
        /*mSensorManager.unregisterListener(this);
        //Telephony ¿?
        //GPS Stuff
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.removeUpdates(locationListenerNetwork);
        locationManager.removeUpdates(locationListenerGPS);
        locationManager.removeNmeaListener(NmeaListener_1);
        locationManager.removeGpsStatusListener(GpsListener);*/

    }

    ///////////////
    protected boolean sun( double Lat, double Lng, Calendar now){

        int Year = now.get(Calendar.YEAR);
        int Month = now.get(Calendar.MONTH) + 1; // Note: zero based!
        int Day = now.get(Calendar.DAY_OF_MONTH);
        int Hour = now.get(Calendar.HOUR_OF_DAY);
        int Minute = now.get(Calendar.MINUTE);

        // Get SUNSET-SUNRISE
        float PI = (float) 3.1416;

        int localOffset = 2;
        float Zenith = (float) 90.8333;
        float D2R = PI / 180;
        float R2D = 180 / PI;

        // Day of the Year
        double N1 = Math.floor(275 * Month / 9);
        double N2 = Math.floor((Month + 9) / 12);
        double N3 = (1 + Math.floor((Year - 4 * Math.floor(Year / 4) + 2) / 3));
        double N = N1 - (N2 * N3) + Day - 30;

        double lnHour = Lng / 15;

        //sunrise aprox time
        double t_sunrise = N + ((6 - lnHour) / 24);
        //sunset aprox time
        double t_sunset = N + ((18 - lnHour) / 24);

        //Sun's mean anomaly
        double M_sunrise = (0.9856 * t_sunrise) - 3.289;
        double M_sunset = (0.9856 * t_sunset) - 3.289;

        //Sun's true longitude
        double L_sunrise = M_sunrise + (1.916 * sin(M_sunrise * D2R)) + (0.020 * sin(2 * M_sunrise * D2R)) + 282.634;
        double L_sunset = M_sunset + (1.916 * sin(M_sunset * D2R)) + (0.020 * sin(2 * M_sunset * D2R)) + 282.634;

        if (L_sunrise > 360)
            L_sunrise = L_sunrise - 360;
        else if (L_sunrise < 0)
            L_sunrise = L_sunrise + 360;

        if (L_sunset > 360)
            L_sunset = L_sunset - 360;
        else if (L_sunset < 0)
            L_sunset = L_sunset + 360;


        //Sun's right ascension
        double RA_sunrise = R2D * Math.atan(0.91764 * Math.tan(L_sunrise * D2R));
        double RA_sunset = R2D * Math.atan(0.91764 * Math.tan(L_sunset * D2R));

        if (RA_sunrise > 360)
            RA_sunrise = RA_sunrise - 360;
        else if (RA_sunrise < 0)
            RA_sunrise = RA_sunrise + 360;

        if (RA_sunset > 360)
            RA_sunset = RA_sunset - 360;
        else if (RA_sunset < 0)
            RA_sunset = RA_sunset + 360;

        // RA value needs to be in the same qua
        double Lquadrant_sunrise = (Math.floor(L_sunrise / (90))) * 90;
        double RAquadrant_sunrise = (Math.floor(RA_sunrise / 90)) * 90;
        RA_sunrise = RA_sunrise + (Lquadrant_sunrise - RAquadrant_sunrise);

        double Lquadrant_sunset = (Math.floor(L_sunset / (90))) * 90;
        double RAquadrant_sunset = (Math.floor(RA_sunset / 90)) * 90;
        RA_sunset = RA_sunset + (Lquadrant_sunset - RAquadrant_sunset);

        //RA needs to be converted to hours
        RA_sunrise = RA_sunrise / 15;
        RA_sunset = RA_sunset / 15;

        // Calculate de Sun's declination
        double sinDec_sunrise = 0.39782 * sin(L_sunrise * D2R);
        double cosDec_sunrise = cos(Math.asin(sinDec_sunrise));

        double sinDec_sunset = 0.39782 * sin(L_sunset * D2R);
        double cosDec_sunset = cos(Math.asin(sinDec_sunset));

        // Sun's local hour angle
        double cosH_sunrise = (cos(Zenith * D2R) - (sinDec_sunrise * sin(Lat * D2R))) / (cosDec_sunrise * cos(Lat * D2R));
        double cosH_sunset = (cos(Zenith * D2R) - (sinDec_sunset * sin(Lat * D2R))) / (cosDec_sunset * cos(Lat * D2R));

        double H_sunrise = (360 - R2D * Math.acos(cosH_sunrise))/15;
        double H_sunset = (R2D * Math.acos(cosH_sunset))/15;

        // Calculate local mean time of rising/setting
        double T_sunrise = H_sunrise + RA_sunrise - (0.06571 * t_sunrise) - 6.622;
        double T_sunset = H_sunset + RA_sunset - (0.06571 * t_sunset) - 6.622;

        // Adjust back to UTC
        double UT_sunrise = T_sunrise - lnHour;
        double UT_sunset = T_sunset - lnHour;

        if (UT_sunrise > 24)
            UT_sunrise = UT_sunrise - 24;
        else if (UT_sunrise < 0)
            UT_sunrise = UT_sunrise + 24;

        if (UT_sunset > 24)
            UT_sunset = UT_sunset - 24;
        else if (UT_sunset < 0)
            UT_sunset = UT_sunset + 24;

        // Convert UT value to local time zone

        double sunrise = UT_sunrise + localOffset;
        double sunset = UT_sunset + localOffset;

        double Time = Hour + Minute/60;

        return Time > sunrise && Time < sunset; //true = day ... false = night

    }

}
