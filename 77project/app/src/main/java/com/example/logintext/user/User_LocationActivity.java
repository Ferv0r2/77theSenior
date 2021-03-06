package com.example.logintext.user;

import android.Manifest;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.logintext.PushAlarmReceiver;
import com.example.logintext.R;
import com.example.logintext.RankRegisterReceiver;
import com.example.logintext.SafeReceiver;
import com.example.logintext.UndeadService;
import com.example.logintext.protector.Pro_LocationActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.lang.Math.asin;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

public class User_LocationActivity extends AppCompatActivity
        implements OnMapReadyCallback,
        ActivityCompat.OnRequestPermissionsResultCallback{

    private GoogleMap mMap;

    private static final String TAG = "googlemap_example";
    private static final int GPS_ENABLE_REQUEST_CODE = 2001;
    private static final int UPDATE_INTERVAL_MS = 20000;  // 10???
    private static final int FAST_UPDATE_INTERVAL_MS = UPDATE_INTERVAL_MS / 2;  // 5???
    private static final int MAX_WAIT_TIME = 120000;  // 60???
    private static final int PERMISSIONS_REQUEST_CODE = 100;

    private boolean needRequest = false;

    //????????? ?????? ??????
    private String uid;
    private String[] REQUIRED_PERMISSIONS  = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION};  // ?????? ?????????

    private TextView address;
    private ImageButton back;

    private Location mCurrentLocation;
    private LatLng currentPosition;     // ??????

    private Circle currentCircle = null;

    private ClusterManager<MyItem> mClusterManager;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest locationRequest;
    private Location location;

    private FirebaseDatabase mDatabase;
    private DatabaseReference mReference, refer;
    private FirebaseUser user;

    private Intent foregroundServiceIntent;

    private AlarmManager alarmManager;
    private PendingIntent pendingIntent;

    private View mLayout;  // Snackbar ???????????? ????????? View ??????

    public static Context context_main;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.user_location_trace);

        mLayout = findViewById(R.id.layout_location);
        address = (TextView) findViewById(R.id.address);
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        back = (ImageButton) findViewById(R.id.back);

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(User_LocationActivity.this, User_MainActivity.class));
                finish();
            }
        });

        if(null == UndeadService.serviceIntent) {
            foregroundServiceIntent = new Intent(this, UndeadService.class);
            startService(foregroundServiceIntent);
//            Toast.makeText(getApplicationContext(),"Start Service", Toast.LENGTH_SHORT).show();
        } else {
            foregroundServiceIntent = UndeadService.serviceIntent;
//            Toast.makeText(getApplicationContext(),"Already", Toast.LENGTH_SHORT).show();
        }

        locationRequest = new LocationRequest()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL_MS)
                .setFastestInterval(FAST_UPDATE_INTERVAL_MS)
                .setMaxWaitTime(MAX_WAIT_TIME);

        LocationSettingsRequest.Builder builder =
                new LocationSettingsRequest.Builder();

        builder.addLocationRequest(locationRequest);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        mMap = googleMap;
        mClusterManager = new ClusterManager<>(this, mMap);

        mDatabase = FirebaseDatabase.getInstance();
        mReference = mDatabase.getReference("Users");
        user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user.getUid();

        mReference.child("user").child(uid).child("myProtector").get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                String myPro = task.getResult().getValue().toString();
                if (!myPro.equals("none")) {
                    mReference.child("protector").child(myPro).child("safeZone").addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            try {
                                String lat = snapshot.child("latitude").getValue().toString();
                                String lon = snapshot.child("longitude").getValue().toString();
                                String area = snapshot.child("area").getValue().toString();

                                if (currentCircle != null) currentCircle.remove();

                                LatLng position = new LatLng(Double.parseDouble(lat), Double.parseDouble(lon));

                                CircleOptions circleOptions = new CircleOptions().center(position) //??????
                                        .radius(Integer.parseInt(area) * 1000)      //????????? ?????? : m
                                        .strokeWidth(0f)  //????????? 0f : ?????????
                                        .fillColor(Color.parseColor("#880000ff")); //?????????

                                currentCircle = mMap.addCircle(circleOptions);
                            } catch (Exception e) {
                                Toast.makeText(User_LocationActivity.this, "??????????????? ????????? ???????????????.", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {

                        }
                    });
                } else {
                    Toast.makeText(User_LocationActivity.this, "???????????? ??????????????????.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        // ?????? ?????? + ?????? ????????? onRequestPermissionResult?????? ??????
        // ?????? ?????? ?????? ?????? ??????
        int hasFineLocationPermission = ContextCompat.checkSelfPermission(User_LocationActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        int hasCoarseLocationPermission = ContextCompat.checkSelfPermission(User_LocationActivity.this,
                Manifest.permission.ACCESS_COARSE_LOCATION);
        int hasBackgroundLocationPermission = ContextCompat.checkSelfPermission(User_LocationActivity.this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION);

        if (hasFineLocationPermission == PackageManager.PERMISSION_GRANTED &&
                hasCoarseLocationPermission == PackageManager.PERMISSION_GRANTED &&
                hasBackgroundLocationPermission == PackageManager.PERMISSION_GRANTED) {
            // ????????? ?????? ?????? + 6.0 ?????? ????????? ????????? ?????? ??????
            startLocationUpdates(); // ?????? ???????????? ??????

        } else {  // ????????? ?????? ??????
            // ?????? ????????? ?????? ?????? ???
            if (ActivityCompat.shouldShowRequestPermissionRationale(User_LocationActivity.this, REQUIRED_PERMISSIONS[0])) {

                // ????????? ????????? ??????
                Snackbar.make(mLayout, "??? ?????? ??????????????? ?????? ?????? ????????? ???????????????.",
                        Snackbar.LENGTH_INDEFINITE).setAction("??????", new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        // ?????? ??????
                        ActivityCompat.requestPermissions(User_LocationActivity.this, REQUIRED_PERMISSIONS,
                                PERMISSIONS_REQUEST_CODE);
                    }
                }).show();

            } else { // ?????? ????????? ?????? ?????? ???
                // ?????? ??????
                ActivityCompat.requestPermissions(User_LocationActivity.this, REQUIRED_PERMISSIONS,
                        PERMISSIONS_REQUEST_CODE);
            }
        }
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {

            @Override
            public void onMapClick(LatLng latLng) {
                Log.d(TAG, "onMapClick :");
            }
        });

    }


    LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);

            List<Location> locationList = locationResult.getLocations();

            if (locationList.size() > 0) {
                location = locationList.get(locationList.size() - 1);
                //location = locationList.get(0);

                currentPosition
                        = new LatLng(location.getLatitude(), location.getLongitude());

                SimpleDateFormat format = new SimpleDateFormat ( "yyyy??? MM???dd??? HH???mm???ss???");

                Calendar time = Calendar.getInstance();

                String format_time = format.format(time.getTime());

                final FirebaseDatabase database = FirebaseDatabase.getInstance();
                DatabaseReference ref = database.getReference("Users");
                DatabaseReference mReference = ref.child("user").child(uid).child("gps").child(format_time);

                Map<Object, String> his = new HashMap<>();
                his.put("latitude", ""+location.getLatitude());
                his.put("longitude", ""+location.getLongitude());
                his.put("address", ""+getCurrentAddress(currentPosition));
                his.put("time", ""+format_time);

                mReference.setValue(his);

                ref.child("user").child(uid).child("myProtector").get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
                    String myPro;
                    @Override
                    public void onComplete(@NonNull Task<DataSnapshot> task) {
                        myPro = task.getResult().getValue().toString();
                        if (!myPro.equals("none")) {
                            ref.child("protector").child(myPro).child("safeZone").get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
                                double latit, longi, area, distance;
                                @Override
                                public void onComplete(@NonNull Task<DataSnapshot> task) {
                                    try {
                                        latit = Double.parseDouble(task.getResult().child("latitude").getValue().toString());
                                        longi = Double.parseDouble(task.getResult().child("longitude").getValue().toString());
                                        area = Double.parseDouble(task.getResult().child("area").getValue().toString());

                                        double rad = 6372.8;

                                        double safeLat = Math.toRadians(latit - location.getLatitude());
                                        double safeLon = Math.toRadians(longi - location.getLongitude());

                                        double a = sin(safeLat / 2) * sin(safeLat / 2) + sin(safeLon / 2) * sin(safeLon / 2)
                                                * cos(Math.toRadians(location.getLatitude())) * cos(Math.toRadians(latit));

                                        double b = 2 * asin(sqrt(a));
                                        distance = rad * b;

                                        if (distance > area) {
                                            setAlarm();
                                        } else {
                                            cancelAlarm();
//                                            Toast.makeText(getApplicationContext(), "???????????? ???????????????.", Toast.LENGTH_SHORT).show();
                                        }
                                    } catch (Exception e) {
                                        Toast.makeText(User_LocationActivity.this, "??????????????? ????????? ???????????????.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        } else {
                            Toast.makeText(User_LocationActivity.this, "???????????? ??????????????????.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                address.setText(getCurrentAddress(currentPosition));    // ?????? ??????
                String markerTitle = "?????????";
                String markerSnippet = getCurrentAddress(currentPosition);

                Log.d(TAG, "onLocationResult : " + markerSnippet);

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, 14));

                //?????? ????????? ?????? ???????????? ??????
                setCurrentLocation(location, markerTitle, markerSnippet);
                mCurrentLocation = location;
            }
        }
    };

    private void startLocationUpdates() {

        if (!checkLocationServicesStatus()) {
            Log.d(TAG, "startLocationUpdates : call showDialogForLocationServiceSetting");
            showDialogForLocationServiceSetting();
        }else {
            int hasFineLocationPermission = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION);
            int hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION);

            if (hasFineLocationPermission != PackageManager.PERMISSION_GRANTED ||
                    hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED   ) {
                Log.d(TAG, "startLocationUpdates : ????????? ??????");
                return;
            }
            Log.d(TAG, "startLocationUpdates : call mFusedLocationClient.requestLocationUpdates");
            mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
            if (checkPermission()) mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        if (checkPermission()) {
            Log.d(TAG, "onStart : call mFusedLocationClient.requestLocationUpdates");
            mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
            if (mMap!=null) mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        context_main = this;
        if (mFusedLocationClient != null) {
            Log.d(TAG, "onStop : call stopLocationUpdates");
            mFusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    public String getCurrentAddress(LatLng latlng) {
        // GPS??? ????????? ??????
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        List<Address> addresses;
        try {
            addresses = geocoder.getFromLocation(latlng.latitude, latlng.longitude,1);
        } catch (IOException ioException) {     // ???????????? ??????
            Toast.makeText(this, "????????? ?????? ??????", Toast.LENGTH_LONG).show();
            return "????????? ?????? ??????";
        } catch (IllegalArgumentException illegalArgumentException) { // ????????? ??????
            Toast.makeText(this, "????????? GPS ??????", Toast.LENGTH_LONG).show();
            return "????????? GPS ??????";
        }
        if (addresses == null || addresses.size() == 0) {   // ????????? ?????? ???
            Toast.makeText(this, "?????? ?????????", Toast.LENGTH_LONG).show();
            return "?????? ?????????";
        } else {
            Address address = addresses.get(0);
            return address.getAddressLine(0).toString();
        }
    }

    public boolean checkLocationServicesStatus() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    public void setCurrentLocation(Location location, String markerTitle, String markerSnippet) {
        //if (currentMarker != null) currentMarker.remove(); // ?????? ?????? ?????????
        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        MyItem locateItem = new MyItem(currentLatLng);

        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(currentLatLng);
        mMap.moveCamera(cameraUpdate);  // ???????????? ????????? ??????, ????????? ??????
        mMap.animateCamera(CameraUpdateFactory.zoomTo(9));

        mClusterManager.addItem(locateItem);
        mMap.setOnCameraIdleListener(mClusterManager);
        mMap.setOnMarkerClickListener(mClusterManager);
    }

    // ?????? ????????? ?????? ?????????
    private boolean checkPermission() {

        int hasFineLocationPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        int hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION);

        if (hasFineLocationPermission == PackageManager.PERMISSION_GRANTED &&
                hasCoarseLocationPermission == PackageManager.PERMISSION_GRANTED   ) {
            return true;
        }
        return false;
    }

    // ActivityCompat.requestPermissions??? ????????? ?????? ????????? ????????? ???????????? ?????????
    @Override
    public void onRequestPermissionsResult(int permsRequestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grandResults) {

        // ?????? ????????? PERMISSIONS_REQUEST_CODE ??????, ????????? ?????? ???????????? ??????????????????
        super.onRequestPermissionsResult(permsRequestCode, permissions, grandResults); // ?????????
        if (permsRequestCode == PERMISSIONS_REQUEST_CODE && grandResults.length == REQUIRED_PERMISSIONS.length) {

            boolean check_result = true;

            // ?????? ?????? ?????? ?????? ??????
            for (int result : grandResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    check_result = false;
                    break;
                }
            }
            if (check_result) {
                // ?????? ?????? ???, ?????? ????????????
                startLocationUpdates();
            } else { // ????????? ????????? ?????? ??????

                if (ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[0])
                        || ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[1])
                        || ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[2])) {

                    // ???????????? ???????????? ????????? ????????? ?????? -> ??? ????????? ??????
                    Snackbar.make(mLayout, "????????? ?????????????????????. ?????? ?????? ???????????? ????????? ??????????????????. ",
                            Snackbar.LENGTH_INDEFINITE).setAction("??????", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            finish();
                        }
                    }).show();

                } else {
                    // "?????? ?????? ??????"??? ???????????? ???????????? ????????? ????????? ?????? -> ???????????? ?????? ??????
                    Snackbar.make(mLayout, "????????? ?????????????????????. ??????(??? ??????)?????? ????????? ???????????? ?????????. ",
                            Snackbar.LENGTH_INDEFINITE).setAction("??????", new View.OnClickListener() {

                        @Override
                        public void onClick(View view) {
                            finish();
                        }
                    }).show();
                }
            }
        }
    }

    // GPS ???????????? ?????? ?????????
    private void showDialogForLocationServiceSetting() {

        AlertDialog.Builder builder = new AlertDialog.Builder(User_LocationActivity.this);
        builder.setTitle("?????? ????????? ????????????");
        builder.setMessage("?????? ???????????? ???????????? ?????? ???????????? ???????????????.\n"
                + "?????? ????????? ?????????????????????????");
        builder.setCancelable(true);
        builder.setPositiveButton("??????", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                Intent callGPSSettingIntent
                        = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(callGPSSettingIntent, GPS_ENABLE_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("??????", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.create().show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case GPS_ENABLE_REQUEST_CODE:
                //???????????? GPS ?????? ?????? ??????
                if (checkLocationServicesStatus()) {
                    if (checkLocationServicesStatus()) {
                        Log.d(TAG, "onActivityResult : GPS ????????? ?????????");
                        needRequest = true;
                        return;
                    }
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

//        if (null != foregroundServiceIntent) {
//            stopService(foregroundServiceIntent);
//            foregroundServiceIntent = null;
//        }
    }

    public class MyItem implements ClusterItem {
        private LatLng currentLatLng;

        public MyItem(LatLng currentLatLng) {
            this.currentLatLng = currentLatLng;
        }

        @NonNull
        @Override
        public LatLng getPosition() {
            return currentLatLng;
        }

        @Nullable
        @Override
        public String getTitle() {
            return null;
        }

        @Nullable
        @Override
        public String getSnippet() {
            return null;
        }
    }

    private void setAlarm() {
        refer = mDatabase.getReference().child("Users").child("user").child(uid);

        refer.get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                String alarm = task.getResult().child("alarm").child("safeZone").toString();

                if (alarm.equals("y")) {
                    Intent receiverIntent = new Intent(User_LocationActivity.this, PushAlarmReceiver.class);
                    pendingIntent = PendingIntent.getBroadcast(User_LocationActivity.this, 0, receiverIntent, 0);

                    Calendar alarm_calendar = Calendar.getInstance();
                    alarm_calendar.set(Calendar.HOUR_OF_DAY, alarm_calendar.get(Calendar.HOUR_OF_DAY));
                    alarm_calendar.set(Calendar.MINUTE, alarm_calendar.get(Calendar.MINUTE));
                    alarm_calendar.set(Calendar.SECOND, alarm_calendar.get(Calendar.SECOND));

                    // 20????????? ??????
                    alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, alarm_calendar.getTimeInMillis(),
                            1000 * 60 * 20, pendingIntent);

                    Toast.makeText(getApplicationContext(), "??????????????? ??????????????????.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void cancelAlarm() {
        alarmManager.cancel(pendingIntent);
    }
}
