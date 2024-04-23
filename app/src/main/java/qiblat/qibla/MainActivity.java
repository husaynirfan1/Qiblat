package qiblat.qibla;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

public class MainActivity extends AppCompatActivity {
    private static final String LOCATION_PERMISSION = "200";
    public TextView tvheading, tvqiblatheading, tvlocation;
    public ImageButton relocate;
    public ImageView imageView;
    public CompassView arrow;
    private double currentLongitude;
    private double currentLatitude;
    private double currentAltitude;
    String address;
    String city;
    AlertDialog dialog;
    String state;
    String country;
    String postalCode;
    String knownName;
    public GPS gps;
    public QiblatCompass qiblatCompass;
    private AdView mAdView;
    private InterstitialAd mInterstitialAd;
    private ScheduledExecutorService scheduler;
    private boolean isVisible;

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            result -> {
                if (result) {
                    //Permission granted
                    Toast.makeText(this, "Location Permission Granted", Toast.LENGTH_SHORT).show();
                } else {
                    //permission denied
                    Toast.makeText(this, "Location Permission Denied", Toast.LENGTH_SHORT).show();
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, LOCATION_PERMISSION)) {
                        //show permission snackbar
                    } else {
                        //display error dialog
                    }
                }

            });


    private void ReqPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            //Permission already granted

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(@NonNull InitializationStatus initializationStatus) {
            }
        });
        mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
        loadInterstitial();

        ReqPermission();
        tvlocation = findViewById(R.id.location);
        tvheading = findViewById(R.id.heading);
        tvqiblatheading = findViewById(R.id.qiblathead);
        relocate = findViewById(R.id.getlocation);
        imageView = findViewById(R.id.imageCompass);
        arrow = findViewById(R.id.arrow);
        getLocation();
        qiblatCompass = new QiblatCompass(this, arrow, tvheading, currentLongitude, currentLatitude, currentAltitude, tvqiblatheading);

        relocate.setOnClickListener(v ->{
            refresh();
        });
    }
    private void refresh() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setCancelable(false);
        LayoutInflater layoutInflater = LayoutInflater.from(MainActivity.this);
        View promptView = layoutInflater.inflate(R.layout.loading_ui, null);

        builder.setView(promptView);

        Button cancelBtn = promptView.findViewById(R.id.cancelbutton_loadingui);

        dialog = builder.create();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                final Button defaultButton = cancelBtn;
                final CharSequence negativeButtonText = defaultButton.getText();
                new CountDownTimer(5000, 100) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        getLocation();


                        defaultButton.setText(String.format(
                                Locale.getDefault(), "%s (%d)",
                                negativeButtonText,
                                TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) + 1 //add one so it never displays zero
                        ));


                    }

                    @Override
                    public void onFinish() {
                        if (((AlertDialog) dialog).isShowing()) {
                            dialog.dismiss();
                            qiblatCompass = null;
                            qiblatCompass = new QiblatCompass(MainActivity.this, arrow, tvheading, currentLongitude, currentLatitude, currentAltitude, tvqiblatheading);
                            tvqiblatheading.invalidate();
                            arrow.invalidate();
                            imageView.invalidate();
                        }
                    }
                }.start();
            }
        });


        dialog.show();

    }
    public void getLocation() {
        gps = new GPS(MainActivity.this);
        if (gps.canGetLocation()) {
            currentLatitude = gps.getLatitude();
            currentLongitude = gps.getLongitude();
            currentAltitude = gps.getAltitute();
            Log.d("location", "currentLatitude : "+currentLatitude+" currentLongitude : "+currentLongitude+" currentAltitude : "+currentAltitude);
            Geocoder geocoder;
            List<Address> addresses;
            geocoder = new Geocoder(this.getApplicationContext(), Locale.getDefault());

            Log.e("latitude", "latitude--" + currentLatitude);


            try {
                Log.e("latitude", "inside latitude--" + currentLatitude);
                addresses = geocoder.getFromLocation(currentLatitude, currentLongitude, 1);

                if (addresses != null && addresses.size() > 0) {
                    address = addresses.get(0).getAddressLine(0);
                    city = addresses.get(0).getLocality();
                    state = addresses.get(0).getAdminArea();
                    country = addresses.get(0).getCountryName();
                    postalCode = addresses.get(0).getPostalCode();
                    knownName = addresses.get(0).getFeatureName();

                    StringBuilder s = new StringBuilder(100);


                    if (city != null){
                        s.append(city).append(", ");
                    }
                    if (state != null){
                        s.append(state).append(", ");
                    }
                    if (country != null){
                        s.append(country);
                    }

                    tvlocation.setText(s);


                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
            gps.showSettingsAlert();
        }
    }

    @Override
    protected void onStart(){
        super.onStart();
        isVisible = true;
        if(scheduler == null){
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(new Runnable() {
                public void run() {
                    Log.d("hello", "world");
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (mInterstitialAd != null) {
                                mInterstitialAd.show(MainActivity.this);
                            } else {
                                Log.d("interstitial", "The interstitial ad wasn't ready yet.");
                            }
                            loadInterstitial();
                        }
                    });
                }
            }, 300, 300, TimeUnit.SECONDS);

        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        scheduler.shutdownNow();
        scheduler = null;
        isVisible =false;
    }


    public void loadInterstitial(){
        AdRequest adRequest = new AdRequest.Builder().build();

        InterstitialAd.load(MainActivity.this,"ca-app-pub-2617339476984881/9907163035", adRequest,

                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        // The mInterstitialAd reference will be null until
                        // an ad is loaded.
                        mInterstitialAd = interstitialAd;
                        Log.d("Interstitial", "onAdLoaded");
                        mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                super.onAdDismissedFullScreenContent();
                                loadInterstitial();
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                                super.onAdFailedToShowFullScreenContent(adError);
                                mInterstitialAd = null;
                            }

                        });
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        // Handle the error
                        Log.d("iNTERSTITIAL", loadAdError.toString());
                        mInterstitialAd = null;
                    }
                });
    }

}