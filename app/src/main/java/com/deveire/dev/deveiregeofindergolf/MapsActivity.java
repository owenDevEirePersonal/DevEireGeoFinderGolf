package com.deveire.dev.deveiregeofindergolf;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback
{

    private GoogleMap mMap;

    private float courseLat;
    private float courseLong;
    private Location courseLocation;
    private float userLat;
    private float userLong;
    private Location userLocation;
    private float courseRadius; //in meters

    private TextView mapText;
    private Button swingButton;

    private String nearestGolfCourse;

    SharedPreferences aSaveState;
    private ArrayList<Location> swings;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        nearestGolfCourse = "";
        swings = new ArrayList<Location>();

        //[Retrieve all previously saved swings]
        Context thisContext = getApplicationContext();
        aSaveState = thisContext.getSharedPreferences("SavedSwings", Context.MODE_PRIVATE);
        int currentIndexOfSaveState = 1;
        while(currentIndexOfSaveState > 0)
        {
            String currentSave = aSaveState.getString("Swing" + currentIndexOfSaveState, null);
            if(currentSave != null)
            {
                Location currentSwingLocation = new Location("Swing" + currentIndexOfSaveState);
                currentSwingLocation.setLatitude(aSaveState.getFloat("Swing" + currentIndexOfSaveState + "Lat", 0));
                currentSwingLocation.setLongitude(aSaveState.getFloat("Swing" + currentIndexOfSaveState + "Long", 0));
                swings.add(currentSwingLocation);
                currentIndexOfSaveState++;
            }
            else
            {
                currentIndexOfSaveState = 0;
                //breaks the loop
            }
        }
        //[/Retrieve all previously saved swings]

        mapText = (TextView) findViewById(R.id.mapText);

        swingButton = (Button) findViewById(R.id.swingButton);
        swingButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Location aNewSwing = new Location("Swing" + (swings.size() + 1));
                aNewSwing.setLatitude(userLat);
                aNewSwing.setLongitude(userLong);
                swings.add(aNewSwing);
            }
        });

        Intent inIntent = getIntent();
        courseLat = 52.671359f;
        courseLong = -8.546629f;
        courseLocation = new Location("Golf Course");
        courseLocation.setLatitude(courseLat);
        courseLocation.setLongitude(courseLong);
        courseRadius = 200;//in meters
        userLat = /*52.671355f;*/inIntent.getFloatExtra("userLat", 0);
        userLong = /*-8.546625f;*/inIntent.getFloatExtra("userLong", 0);
        userLocation = new Location ("You are here");
        userLocation.setLatitude(userLat);
        userLocation.setLongitude(userLong);

        if(userLocation.distanceTo(courseLocation) <= courseRadius)//in meters
        {
            nearestGolfCourse = "Jim Bob Memorial Golf Course";
            mapText.setText("You are in the " + nearestGolfCourse);
        }
        else
        {
            nearestGolfCourse = "";
            mapText.setText("No golf courses in your immediate area.");
        }

    }

    @Override
    protected void onPause()
    {
        super.onPause();
        finish();
    }

    @Override
    protected void onStop()
    {
        SharedPreferences.Editor edit = aSaveState.edit();
        int indexOfSwings = 1;
        for (Location aswing:swings)
        {
            Log.i("yep", aswing.toString());
            edit.putString("Swing" + indexOfSwings, aswing.toString());
            edit.putFloat("Swing" + indexOfSwings + "Lat", (float) aswing.getLatitude());
            edit.putFloat("Swing" + indexOfSwings + "Long", (float) aswing.getLongitude());
            indexOfSwings++;
        }
        edit.commit();
        super.onStop();
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap)
    {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        LatLng golfCourse = new LatLng(courseLat, courseLong);
        mMap.addMarker(new MarkerOptions().position(golfCourse).title("Jim Bob Memorial Golf Course"));

        mMap.addMarker(new MarkerOptions().position(new LatLng(userLat,userLong)).title("User Position"));
        mMap.addCircle(new CircleOptions().center(golfCourse).radius(courseRadius));

        switch (nearestGolfCourse)
        {
            case "Jim Bob Memorial Golf Course": mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(golfCourse, 15)); break;
            case "": mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(userLat, userLong), 10)); break;
            default: break;
        }
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(golfCourse, 15));

        int indexOfSwings = 1;
        for (Location aswing: swings)
        {
            mMap.addMarker(new MarkerOptions().position(new LatLng(aswing.getLatitude(), aswing.getLongitude())).title("Swing" + indexOfSwings));
            indexOfSwings++;
        }

    }
}
