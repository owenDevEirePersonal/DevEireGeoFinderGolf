package com.deveire.dev.deveiregeofindergolf;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks, LocationListener, DownloadCallback<String>
{

    private GoogleMap mMap;

    private Boolean isPlayingAGame;

    private float courseLat;
    private float courseLong;
    private Location courseLocation;
    private float userLat;
    private float userLong;
    private Location userLocation;
    private float courseRadius; //in meters

    private TextView mapText;
    private TextView currentLayerText;
    private Button swingButton;
    private Button statsButton;
    private Button nextLayerButton;
    private Button previousLayerButton;
    private Button spoofSwingButton;
    private EditText latEditText;
    private EditText longEditText;

    private Location fakeUserLocation;

    private String nearestGolfCourse;

    SharedPreferences aSaveState;
    private ArrayList<Location> swings;

    private ArrayList<GolfHole> allHoles;
    //BE CAREFUL when dealing with allholes and currentHole. Remember to write changes to the entry in all holes and not just to currenthole.

    private GolfHole nearestHole; //the hole the user is currently closest to
    private GolfHole currentHole; //the hole the user is believed to be currently playing
    private int intervalsSpentAtCurrentHole;
    private int intervalsSpentAtNearestHole;
    private boolean isPlayingAHole;
    private boolean hasTeedOff;
    private int strokeNumber;

    private int timeSinceStartedLastShot; //in ms
    //TODO: look into using geoFences to detect slow players.

    private Location usersLastLocation;
    private Location users2ndLastLocation;
    private Location users3rdLastLocation;
    private Location usersLastSwingLocation;

    private int UsersCurrentAction;
    private int UsersPreviousAction;

    private final int user_is_swinging = 1;
    private final int user_is_waiting = 2;
    private final int user_is_walking = 3;
    private final int user_is_walking_or_waiting = 4;
    private final int user_is_waiting_or_swinging = 5;

    private int currentMapLayer;
    private final int map_layer_all = 1;
    private final int map_layer_holes = 2;
    private final int map_layer_swings = 3;
    private final int map_layer_user = 4;
    private final int map_layer_current_hole_and_swings = 5;

    private ArrayList<MarkerOptions> holesMarkers;
    private ArrayList<MarkerOptions> teeMarkers;


    //[Network and periodic location update, Variables]
    private GoogleApiClient mGoogleApiClient;
    private Location locationReceivedFromLocationUpdates;
    private MapsActivity.AddressResultReceiver geoCoderServiceResultReciever;
    private int locationScanInterval;

    LocationRequest request;
    private final int SETTINGS_REQUEST_ID = 8888;
    private final String SAVED_LOCATION_KEY = "79";

    private boolean pingingServer;
    private String serverURL;
    private NetworkFragment aNetworkFragment;
    //[/Network and periodic location update, Variables]

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        isPlayingAGame = false;

        nearestGolfCourse = "";

        mapText = (TextView) findViewById(R.id.mapText);
        latEditText = (EditText) findViewById(R.id.latEditText);
        longEditText = (EditText) findViewById(R.id.longEditText);

        swingButton = (Button) findViewById(R.id.swingButton);
        swingButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if(latEditText.getText().toString() != "" && longEditText.getText().toString() != "")
                {
                    fakeUserLocation = new Location("fake");
                    fakeUserLocation.setLatitude(Double.parseDouble(latEditText.getText().toString()));
                    fakeUserLocation.setLongitude(Double.parseDouble(longEditText.getText().toString()));
                    onLocationChanged(fakeUserLocation);
                }
            }
        });

        statsButton = (Button) findViewById(R.id.statsButton);
        statsButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startStatsActivity();
            }
        });


        spoofSwingButton = (Button) findViewById(R.id.spoofSwingButton);
        spoofSwingButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                allHoles.get(0).setSwings(new ArrayList<Location>());
                currentHole = allHoles.get(0);
                Location aloc = new Location("spoof");
                aloc.setLatitude(currentHole.getTeeLocation().getLatitude() - 0.000050);
                aloc.setLongitude(currentHole.getTeeLocation().getLongitude() - 0.000050);
                allHoles.get(0).addToSwings(aloc);
                aloc = new Location("spoof2");
                aloc.setLatitude(currentHole.getTeeLocation().getLatitude() - 0.000100);
                aloc.setLongitude(currentHole.getTeeLocation().getLongitude() - 0.000100);
                allHoles.get(0).addToSwings(aloc);
                aloc = new Location("spoof3");
                aloc.setLatitude(currentHole.getTeeLocation().getLatitude() - 0.000140);
                aloc.setLongitude(currentHole.getTeeLocation().getLongitude() - 0.000080);
                allHoles.get(0).addToSwings(aloc);
                currentHole = allHoles.get(0);
            }
        });

        Intent inIntent = getIntent();
        courseLat = 52.671359f;
        courseLong = -8.546629f;
        courseLocation = new Location("Golf Course");
        courseLocation.setLatitude(courseLat);
        courseLocation.setLongitude(courseLong);
        courseRadius = 200;//in meters
        userLat = 52.671355f; /*inIntent.getFloatExtra("userLat", 0);*/ //input of lat and long disabled
        userLong = -8.546625f; /*inIntent.getFloatExtra("userLong", 0);*/
        userLocation = new Location("You are here");
        userLocation.setLatitude(userLat);
        userLocation.setLongitude(userLong);


        aSaveState = getSharedPreferences("GeoFinderGolf", Context.MODE_PRIVATE);

        isPlayingAGame = aSaveState.getBoolean("isPlayingAGame", false);
        getHoleData();//warning: method loads hardcoded placeholder data.
        getUserData();//warning: method loads hardcoded placeholder data.


        //[Check if within a golf course]
        nearestGolfCourse = "";
        /*if(userLocation.distanceTo(courseLocation) <= courseRadius)//in meters
        {
            nearestGolfCourse = "Jim Bob Memorial Golf Course";
            mapText.setText("You are in the " + nearestGolfCourse);
        }
        else
        {
            nearestGolfCourse = "";
            mapText.setText("No golf courses in your immediate area.");
        }*/
        //[/Check if within a golf course]


        /*
        nearestHole = findNearestHole(userLocation, allHoles);
        currentHole = nearestHole;
        Log.i("Hole Update", "Current Nearest Hole is: " + (nearestHole.getNameOfGolfCourse() + " Hole " + nearestHole.getHoleNumber()));
        */
        UsersCurrentAction = user_is_walking; //intialise as walking as the action will only begin to come into play once the user is walking away from the tee.
        UsersPreviousAction = user_is_walking;
        isPlayingAHole = false;
        int loadedCurrentHoleNumber = aSaveState.getInt("currentHoleNumber", 0);
        if(loadedCurrentHoleNumber != 0)
        {
            currentHole = allHoles.get(loadedCurrentHoleNumber - 1);
        }

        timeSinceStartedLastShot = 0;

        holesMarkers = new ArrayList<MarkerOptions>();
        teeMarkers = new ArrayList<MarkerOptions>();

        currentMapLayer = map_layer_all;
        currentLayerText = (TextView) findViewById(R.id.currentLayerText);
        nextLayerButton = (Button) findViewById(R.id.nextLayerButton);
        nextLayerButton.setOnClickListener(new onClickNextLayerButton());
        previousLayerButton = (Button) findViewById(R.id.previousLayerButton);
        previousLayerButton.setOnClickListener(new onClickPreviousLayerButton());

        setupServerUplinkAndLocationUpdater(savedInstanceState);
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
        /*SharedPreferences.Editor edit = aSaveState.edit();
        int indexOfSwings = 1;
        for (Location aswing : swings)
        {
            Log.i("Saving Swings", aswing.toString());
            edit.putString("Swing" + indexOfSwings, aswing.toString());
            edit.putFloat("Swing" + indexOfSwings + "Lat", (float) aswing.getLatitude());
            edit.putFloat("Swing" + indexOfSwings + "Long", (float) aswing.getLongitude());
            indexOfSwings++;
        }
        edit.commit();
        */
        SharedPreferences.Editor edit = aSaveState.edit();
        //edit.clear();
        if(currentHole != null)
        {
            edit.putInt("currentHoleNumber", currentHole.getHoleNumber());
        }
        edit.putBoolean("isPlayingAGame", isPlayingAGame);
        int i = 1;
        for (GolfHole ahole: allHoles)
        {
            Gson gson = new Gson();
            String json = gson.toJson(ahole);
            edit.putString("Hole" + ahole.getHoleNumber(), json);
            Log.i("SAVING", "Saving Hole " + ahole.getHoleNumber() + " to memory");
        }
        edit.commit();

        mGoogleApiClient.disconnect();
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
        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

        // Add a marker in Sydney and move the camera
        LatLng golfCourse = new LatLng(courseLat, courseLong);
        mMap.addMarker(new MarkerOptions().position(golfCourse).title("Jim Bob Memorial Golf Course").visible(false));

        mMap.addMarker(new MarkerOptions().position(new LatLng(userLat, userLong)).title("User Position"));
        mMap.addCircle(new CircleOptions().center(golfCourse).radius(courseRadius));

        switch (nearestGolfCourse)
        {
            case "Jim Bob Memorial Golf Course":
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(golfCourse, 15));
                break;
            case "":
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(userLat, userLong), 10));
                break;
            default:
                break;
        }

        /*int indexOfSwings = 1;
        for (Location aswing : swings)
        {
            mMap.addMarker(new MarkerOptions().position(new LatLng(aswing.getLatitude(), aswing.getLongitude())).title("Swing" + indexOfSwings));
            indexOfSwings++;
        }*/


        for (GolfHole aHole : allHoles)
        {
            holesMarkers.add(new MarkerOptions().position(new LatLng(aHole.getGreenLocation().getLatitude(), aHole.getGreenLocation().getLongitude())).title("Hole " + aHole.getHoleNumber()));
            teeMarkers.add(new MarkerOptions().position(new LatLng(aHole.getTeeLocation().getLatitude(), aHole.getTeeLocation().getLongitude())).title("Tee " + aHole.getHoleNumber()));
        }

        updateMapLayer();
    }

    private void addMarkerToMap(Location newMarkerLocation, String newMarkerTitle)
    {
        mMap.addMarker(new MarkerOptions().position(new LatLng(newMarkerLocation.getLatitude(), newMarkerLocation.getLongitude())).title(newMarkerTitle));
    }

    private GolfHole findNearestHole(Location userLocation, ArrayList<GolfHole> allLocations)
    {
        GolfHole nearestHole = null;
        Location nearestLocation = allLocations.get(0).getTeeLocation();
        for (int i = 0; i < allLocations.size(); i++)
        {
            if(userLocation.distanceTo(allLocations.get(i).getTeeLocation()) <= userLocation.distanceTo(nearestLocation))
            {
                nearestLocation = allLocations.get(i).getTeeLocation();
                nearestHole = allLocations.get(i);
            }
        }
        return nearestHole;
    }

    private void updateMap()
    {
        users3rdLastLocation = users2ndLastLocation;
        users2ndLastLocation = usersLastLocation;
        usersLastLocation = userLocation;
        userLocation = locationReceivedFromLocationUpdates;

        updateWhatHoleUserIsAt();
        if(currentHole != null && isPlayingAHole)
        {
            updateUsersAction();
            allHoles.get(currentHole.getHoleNumber() - 1).addIntervalTimeToHole();
            timeSinceStartedLastShot += GolfHole.getIntervalLength();
        }

        updateMapLayer();
    }


    //Detect when the user has change action between walking waiting and swinging
    private void updateUsersAction()
    {
        if(!hasTeedOff)
        {
            if(userLocation.distanceTo(currentHole.getTeeLocation()) > 5)
            {
                hasTeedOff = true;
            }
        }
        else //if the user is playing a hole and has teed off start tracking their actions.
        {
            UsersPreviousAction = UsersCurrentAction;

            switch (UsersPreviousAction)
            {
                case user_is_waiting_or_swinging:
                    if ((usersLastLocation.distanceTo(users2ndLastLocation) >= 10) && (userLocation.distanceTo(usersLastLocation) >= 10))//user spent last two intervals moving
                    {
                        UsersCurrentAction = user_is_walking;
                        Log.i("Action Update", "User action changed from waiting or swinging to walking");
                    }
                    break;

                case user_is_walking:
                    if ((usersLastLocation.distanceTo(users2ndLastLocation) < 10) && (userLocation.distanceTo(usersLastLocation) < 10))//user spend the last 2 intervals standing still
                    {
                        UsersCurrentAction = user_is_waiting_or_swinging;
                        if (isUserSwinging())
                        {
                            UsersCurrentAction = user_is_swinging;
                            allHoles.get(currentHole.getHoleNumber() - 1).addToSwings(userLocation);//TODO:Ensure that allHoles is ordered correctly.
                            currentHole = allHoles.get(currentHole.getHoleNumber() - 1);//refresh the copy of current hole to contain the changes in the above line.
                            strokeNumber++;

                            Log.i("Action Update", "User action changed from walking to swinging");
                        }
                        else
                        {
                            UsersCurrentAction = user_is_waiting;
                            Log.i("Action Update", "User action changed from walking to waiting");
                        }
                    }
                    break;

                case user_is_waiting:
                    if ((usersLastLocation.distanceTo(users2ndLastLocation) >= 10) && (userLocation.distanceTo(usersLastLocation) >= 10))//user spent the last 2 updates moving
                    {
                        UsersCurrentAction = user_is_walking;
                        Log.i("Action Update", "User action changed from waiting to walking");
                    }
                    break;

                case user_is_swinging:
                    if ((usersLastLocation.distanceTo(users2ndLastLocation) >= 10) && (userLocation.distanceTo(usersLastLocation) >= 10))//user spent the last 2 updates moving
                    {
                        UsersCurrentAction = user_is_walking;
                        allHoles.get(currentHole.getHoleNumber() - 1).addSwingTime(timeSinceStartedLastShot);
                        timeSinceStartedLastShot = 0;
                        Log.i("Action Update", "User action changed from swinging to walking");
                    }
                    break;

                default:
                    Log.e("Update User Action", "Warning: user's previous action does not match any known action");
                    break;
            }
        }
    }

    private boolean isUserSwinging()//or waiting
    {
        if(strokeNumber > currentHole.getAverageShotDistances().size())
        {
            Log.i("Action Update", "USER HAS EXCEEDED THE NORMAL NUMBER OF SHOTS");
            return false;
        }
        if(userLocation.distanceTo(usersLastSwingLocation) <= (currentHole.getAverageShotDistances().get(strokeNumber - 1) / 100) * 80)
        {
            return false;//user is waiting
        }
        else
        {
            return true;//user is swinging
        }
    }


    private void updateWhatHoleUserIsAt()
    {
        //TODO: Account for marshal intervention moving people on a hole.
        Log.i("Update", "updatingWhatholeUserIsAt");
        GolfHole newNearestHole = findNearestHole(userLocation, allHoles);

        if(!isPlayingAGame)//if the user haven't started playing yet
        {
            if(userLocation.distanceTo(allHoles.get(0).getTeeLocation()) <= 5 && usersLastLocation.distanceTo(newNearestHole.getTeeLocation()) <= 5)//and they get within 5 meters of the first hole's tee
            {
                currentHole = allHoles.get(0);
                usersLastSwingLocation = currentHole.getTeeLocation();
                isPlayingAHole = true;
                Log.i("Update Hole", "User has arrived at 1st hole, setting currentHole to hole 1");
                strokeNumber = 1;
                isPlayingAGame = true;
            }
        }
        else if(isPlayingAHole) //if user is playing a hole and they get to the green.
        {
            if(userLocation.distanceTo(currentHole.getGreenLocation()) <= currentHole.getGreenRadius())
            {
                isPlayingAHole = false;
                hasTeedOff = false;

                allHoles.get(currentHole.getHoleNumber() - 1).addSwingTime(timeSinceStartedLastShot);
                timeSinceStartedLastShot = 0;

                storeLastHoleData(allHoles.get(currentHole.getHoleNumber() - 1));//store the data of the previous hole given that this hole has been complete(i.e. user has reached green)

                Log.i("Update Hole", "User has arrived at the green of hole " + currentHole.getHoleNumber());

                if(currentHole.getHoleNumber() == 18)
                {
                    Log.i("Update Hole", "User has finished 18 hole, ending game");
                    isPlayingAGame = false;
                    currentHole = null;
                }
            }
        }
        else if(userLocation.distanceTo(newNearestHole.getTeeLocation()) <= 5 && usersLastLocation.distanceTo(newNearestHole.getTeeLocation()) <= 5) //if the user is not playing a hole(is between holes) and comes within 5 meters of a hole's tee then set them to playing that hole.
        {
            currentHole = newNearestHole;
            usersLastSwingLocation = currentHole.getTeeLocation();
            isPlayingAHole = true;
            Log.i("Update Hole", "User has arrived at the tee of hole " + currentHole.getHoleNumber());
            strokeNumber = 1;
            timeSinceStartedLastShot = 0;
        }
        /*if(nearestHole == newNearestHole)
        {

            intervalsSpentAtNearestHole++;
            if(intervalsSpentAtCurrentHole > 2)
            {
                storeLastHoleData(currentHole, intervalsSpentAtCurrentHole);
                currentHole = nearestHole;
                currentHole.setTimeSpentAtHole(3 * locationScanInterval * 1000);//set to 3 intervals as at this point the user would have already spend 3 intervals at this hole before the app realized that they had moved to this hole.
                /*!!!Warning!!!
                    TODO: FIX INTERVAL TIME INACCURACY FOR INTIALISING THE CURRENT HOLES TIME.
                    when the user is at a new currentHole, it sets the user's time at that hole to 3 intervals worth
                    BUT this only using the current interval length, if the interval lenght changes in those 3 intervals
                    then this intial time will be inaccurate.
                 /*
            }
            else
            {
                intervalsSpentAtCurrentHole++;
                currentHole.addIntervalTimeToHole();
            }
        }
        else
        {
            intervalsSpentAtCurrentHole++;
            currentHole.addIntervalTimeToHole();
            nearestHole = newNearestHole;
            intervalsSpentAtNearestHole = 0;
        }*/
    }

    private void getHoleData()
    {
        //TODO: retrieve stored hole data from a database
        //[Create golf holes(placeholder)]
        allHoles = new ArrayList<GolfHole>();
        Location aLoc;
        Location bLoc;

        Gson gson = new Gson();
        String json;
        int numberOfHoles = 4; //4 because i'm only using 4 for testing purposes.
        for(int i = 1; i <= numberOfHoles; i++)
        {
            json = aSaveState.getString("Hole" + i, "ERROR");
            if (json.matches("ERROR"))
            {
                Log.e("LOADING", "Erroring occured while loading Holes, at Hole " + i + ", installing defaults instead");
                switch (i)
                {
                    /*[Placeholder locations on an actual golfcourse]

                    case 1:
                        aLoc = new Location("1"); //-0.000100 lat from centre
                        aLoc.setLatitude(52.614557);
                        aLoc.setLongitude(-8.628891);
                        bLoc= new Location("1");
                        bLoc.setLatitude(52.612032);
                        bLoc.setLongitude(-8.629009);
                        allHoles.add(new GolfHole(1, "A Golf Course", aLoc, bLoc, 5));
                        storeLastHoleData(allHoles.get(allHoles.size() - 1));
                        break;

                    case 2:
                        aLoc = new Location("2");//+0.000100 lat from centre
                        aLoc.setLatitude(52.612352);
                        aLoc.setLongitude(-8.628403);
                        bLoc= new Location("2");
                        bLoc.setLatitude(52.614179);
                        bLoc.setLongitude(-8.626711);
                        allHoles.add(new GolfHole(2, "A Golf Course", aLoc, bLoc, 5));
                        storeLastHoleData(allHoles.get(allHoles.size() - 1));
                        break;

                    case 3:
                        aLoc = new Location("3");//-0.000100 lng from centre
                        aLoc.setLatitude(52.614261);
                        aLoc.setLongitude(-8.626179);
                        bLoc= new Location("3");
                        bLoc.setLatitude(52.612084);
                        bLoc.setLongitude(-8.628131);
                        storeLastHoleData(allHoles.get(allHoles.size() - 1));
                        allHoles.add(new GolfHole(3, "A Golf Course", aLoc, bLoc, 5));
                        break;

                    case 4:
                        aLoc = new Location("4");//+0.000100 lng from centre
                        aLoc.setLatitude(52.611902);
                        aLoc.setLongitude(-8.629600);
                        bLoc= new Location("4");
                        bLoc.setLatitude(52.615674);
                        bLoc.setLongitude(-8.629468);
                        storeLastHoleData(allHoles.get(allHoles.size() - 1));
                        allHoles.add(new GolfHole(4, "A Golf Course", aLoc, bLoc, 5));
                        break;
                    [/Placeholder Locations on an actual golfcourse]*/
                    case 1:
                        aLoc = new Location("1"); //-0.000100 lat from centre
                        aLoc.setLatitude(52.671259);
                        aLoc.setLongitude(-8.546629);
                        bLoc= new Location("1");
                        bLoc.setLatitude(52.671059);
                        bLoc.setLongitude(-8.546629);
                        allHoles.add(new GolfHole(1, "A Golf Course", aLoc, bLoc, 5));
                        storeLastHoleData(allHoles.get(allHoles.size() - 1));
                        break;

                    case 2:
                        aLoc = new Location("2");//+0.000100 lat from centre
                        aLoc.setLatitude(52.671459);
                        aLoc.setLongitude(-8.546629);
                        bLoc= new Location("2");
                        bLoc.setLatitude(52.671659);
                        bLoc.setLongitude(-8.546629);
                        allHoles.add(new GolfHole(2, "A Golf Course", aLoc, bLoc, 5));
                        storeLastHoleData(allHoles.get(allHoles.size() - 1));
                        break;

                    case 3:
                        aLoc = new Location("3");//-0.000100 lng from centre
                        aLoc.setLatitude(52.671359);
                        aLoc.setLongitude(-8.5465529);
                        bLoc= new Location("3");
                        bLoc.setLatitude(52.671359);
                        bLoc.setLongitude(-8.546129);
                        storeLastHoleData(allHoles.get(allHoles.size() - 1));
                        allHoles.add(new GolfHole(3, "A Golf Course", aLoc, bLoc, 5));
                        break;

                    case 4:
                        aLoc = new Location("4");//+0.000100 lng from centre
                        aLoc.setLatitude(52.671359);
                        aLoc.setLongitude(-8.546729);
                        bLoc= new Location("4");
                        bLoc.setLatitude(52.671459);
                        bLoc.setLongitude(-8.546929);
                        storeLastHoleData(allHoles.get(allHoles.size() - 1));
                        allHoles.add(new GolfHole(4, "A Golf Course", aLoc, bLoc, 5));
                        break;
                }
            }
            else
            {
                GolfHole loadedHole = gson.fromJson(json, GolfHole.class);
                if(aSaveState.getBoolean("IsPlayingAGame", false))
                {
                    currentHole = allHoles.get(aSaveState.getInt("currentHoleNumber", 0) + 1);
                }
                else
                {
                    loadedHole.setSwings(new ArrayList<Location>());//clears the swings that were previously stored to memory.
                }
                allHoles.add(loadedHole);
                Log.i("LOADING", "Loading Hole " + loadedHole.getHoleNumber() + " from memory");
            }
        }


        //[/Create golf holes(placeholder)]

    }

    //Retrives the user's data, for now, thats just their average shot distances on a hole
    private void getUserData()
    {
        /*int i = 1;
        for (GolfHole ahole: allHoles)
        {
            boolean hasNext = true;
            int j = 1;
            ArrayList<Integer> averageShots = new ArrayList<Integer>();
            while (hasNext)
            {
                int swingDistance = aSaveState.getInt("Hole" + i + "Swing" + j, 0);
                if (swingDistance == 0)
                {
                    hasNext = false;
                }
                else
                {
                    averageShots.add(swingDistance);
                    j++;
                }
            }
            ahole.setAverageShotDistances(averageShots);
            i++;
        }*/



        //TODO: retrieve stored user data from a database
        ArrayList<Integer> averageShots = new ArrayList<Integer>();
        averageShots.add(20);//shot 1 goes 20
        averageShots.add(25);//shot 2 goes 25
        averageShots.add(30);//shot 3 goes 30 and reaches the green
        allHoles.get(0).setAverageShotDistances(averageShots);
        averageShots = new ArrayList<Integer>();
        averageShots.add(20);
        averageShots.add(25);
        averageShots.add(30);
        allHoles.get(1).setAverageShotDistances(averageShots);
        averageShots = new ArrayList<Integer>();
        averageShots.add(20);
        averageShots.add(25);
        averageShots.add(30);
        allHoles.get(2).setAverageShotDistances(averageShots);
        averageShots = new ArrayList<Integer>();
        averageShots.add(20);
        averageShots.add(25);
        averageShots.add(30);
        allHoles.get(3).setAverageShotDistances(averageShots);
    }

    private void storeLastHoleData(GolfHole lastHole)
    {
        //TODO: SAVE DATA TO SHARED PREFERENCES.
        SharedPreferences.Editor edit = aSaveState.edit();
        int i = 1;
        for (int aswingDistance: lastHole.getAverageShotDistances())
        {
            if(i == 1)//for the first hole, get teh distance between this and the
            {
                edit.putInt("Hole" + lastHole.getHoleNumber() + "Swing" + 1, (int) (aswingDistance + lastHole.getSwings().get(0).distanceTo(lastHole.getTeeLocation())) / 2);
            }
            else if(i == lastHole.getSwings().size())//for the last shot, get the distance between it and the hole, then subtract the greens size
            {
                edit.putInt("Hole" + lastHole.getHoleNumber() + "Swing" + i, (int) (aswingDistance + (lastHole.getSwings().get(i - 1).distanceTo(lastHole.getGreenLocation()) - lastHole.getGreenRadius())) / 2);
            }
            else//for every other hole, get the distance between Swing i and the previous swing.
            {
                edit.putInt("Hole" + lastHole.getHoleNumber() + "Swing" + i, (int) (aswingDistance + lastHole.getSwings().get(i - 1).distanceTo(lastHole.getSwings().get(i - 2))) / 2);
            }
            i++;
        }
        edit.commit();
    }

    private void startStatsActivity()
    {
        Intent aIntent = new Intent(getApplicationContext(), StatsActivity.class);
        String sendToData;
        if(currentHole != null)
        {
            sendToData = "The User is currently at Hole " + currentHole.getHoleNumber() + " and is ";
        }
        else
        {
            sendToData = "The user is currently not playing and is ";
        }

        switch (UsersCurrentAction)
        {
            case user_is_swinging: sendToData += "taking a swing"; break;
            case user_is_waiting: sendToData += "waiting around, probablly for another golfer"; break;
            case user_is_walking: sendToData += "walking somewhere"; break;
        }
        sendToData += "\n\n";
        for (GolfHole ahole:allHoles)
        {
            sendToData += "Hole" + ahole.getHoleNumber() + "  \n  Tee Location(" + ahole.getTeeLocation().getLatitude() + "," + ahole.getTeeLocation().getLongitude() + ")" + "\n  Green Location(" + ahole.getGreenLocation().getLatitude() + "," + ahole.getGreenLocation().getLongitude() + ")" + "\n";
            int count = 1;
            for (int a:ahole.getAverageShotDistances())
            {
                sendToData += "\t Stroke " + count + " Average Distance: " + a + "\n";
                count++;
            }
            sendToData += "\n";
            int count2 = 1;
            for (Location b:ahole.getSwings())
            {
                sendToData += "\t Swing " + count2 + ": (" + b.getLatitude() + ", " + b.getLongitude() + ")" + "\n";
                count2++;
            }
        }
        aIntent.putExtra("data", sendToData);
        startActivity(aIntent);
    }

    private void updateMapLayer()
    {
        mMap.clear();
        switch (currentMapLayer)
        {
            case map_layer_all:
                for (MarkerOptions a: holesMarkers)
                {
                    mMap.addMarker(a);
                }
                for (MarkerOptions b: teeMarkers)
                {
                    mMap.addMarker(b);
                }
                for(GolfHole a: allHoles)
                {
                    int i = 1;
                    for (Location b: a.getSwings())
                    {
                        mMap.addMarker(new MarkerOptions().position(new LatLng(b.getLatitude(), b.getLongitude())).title("Hole " + a.getHoleNumber() + " Swing " + i));
                        i++;
                    }
                }
                mMap.addMarker(new MarkerOptions().position(new LatLng(userLocation.getLatitude(), userLocation.getLongitude())).title("User"));
                currentLayerText.setText("All");
                break;

            case map_layer_holes:
                for (MarkerOptions a: holesMarkers)
                {
                    mMap.addMarker(a);
                }
                for (MarkerOptions b: teeMarkers)
                {
                    mMap.addMarker(b);
                }
                currentLayerText.setText("Holes");
                break;

            case map_layer_swings:
                for(GolfHole a: allHoles)
                {
                    Log.i("Map", "Loading swing markers for hole " + a.getHoleNumber());
                    int i = 1;
                    for (Location b: a.getSwings())
                    {
                        Log.i("Map", "Loading swing " + i + " for hole " + a.getHoleNumber());
                        mMap.addMarker(new MarkerOptions().position(new LatLng(b.getLatitude(), b.getLongitude())).title("Hole " + a.getHoleNumber() + " Swing " + i));
                        i++;
                    }
                }
                currentLayerText.setText("Swings");
                break;

            case map_layer_user:
                mMap.addMarker(new MarkerOptions().position(new LatLng(userLocation.getLatitude(), userLocation.getLongitude())).title("User"));
                currentLayerText.setText("User");
                break;

            case map_layer_current_hole_and_swings:
                int i = 1;
                if(currentHole != null)
                {
                    mMap.addMarker(new MarkerOptions().position(new LatLng(currentHole.getTeeLocation().getLatitude(), currentHole.getTeeLocation().getLongitude())).title("Tee"));
                    mMap.addMarker(new MarkerOptions().position(new LatLng(currentHole.getGreenLocation().getLatitude(), currentHole.getGreenLocation().getLongitude())).title("Hole " + currentHole.getHoleNumber()));
                    for (Location b : currentHole.getSwings())
                    {
                        mMap.addMarker(new MarkerOptions().position(new LatLng(b.getLatitude(), b.getLongitude())).title("Swing " + i));
                        i++;
                    }
                }
                currentLayerText.setText("Current Hole and Swings");
                break;
        }
    }

    public class onClickNextLayerButton implements View.OnClickListener
    {
        @Override
        public void onClick(View v)
        {
            currentMapLayer++;
            if(currentMapLayer > map_layer_current_hole_and_swings)
            {
                currentMapLayer = map_layer_all;
            }
            updateMapLayer();
        }
    }

    public class onClickPreviousLayerButton implements View.OnClickListener
    {
        @Override
        public void onClick(View v)
        {
            currentMapLayer--;
            if(currentMapLayer < map_layer_all)
            {
                currentMapLayer = map_layer_current_hole_and_swings;
            }
            updateMapLayer();
        }
    }

//**********[Location Update and server pinging Code]

    //called from onCreate()
    protected void setupServerUplinkAndLocationUpdater(Bundle savedInstanceState)
    {
        pingingServer = false;

        //aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), "https://192.168.1.188:8080/smrttrackerserver-1.0.0-SNAPSHOT/hello?isDoomed=yes");
        serverURL = "http://geo.dev.deveire.com/store/location?id=" + Settings.Secure.ANDROID_ID.toString() + "&lat=" + 0000 + "&lon=" + 0000;
        //0000,0000 is a location in the middle of the atlantic occean south of western africa and unlikely to contain a golf course.

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mGoogleApiClient.connect();

        locationScanInterval = 20;//in seconds
        GolfHole.setIntervalLength(locationScanInterval * 1000);

        request = new LocationRequest();
        request.setInterval(locationScanInterval * 1000);//in mileseconds
        request.setFastestInterval(5000);//caps how fast the locations are recieved, as other apps could be triggering updates faster than our app.
        request.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY); //accurate to 100 meters.

        LocationSettingsRequest.Builder requestBuilder = new LocationSettingsRequest.Builder().addLocationRequest(request);

        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                        requestBuilder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>()
        {
            @Override
            public void onResult(@NonNull LocationSettingsResult aResult)
            {
                final Status status = aResult.getStatus();
                final LocationSettingsStates states = aResult.getLocationSettingsStates();
                switch (status.getStatusCode())
                {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. The client can
                        // initialize location requests here.

                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.
                        try
                        {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(MapsActivity.this, SETTINGS_REQUEST_ID);
                        } catch (IntentSender.SendIntentException e)
                        {
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way
                        // to fix the settings so we won't show the dialog.
                        break;
                }
            }
        });

        geoCoderServiceResultReciever = new MapsActivity.AddressResultReceiver(new Handler());

        restoreSavedValues(savedInstanceState);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // An unresolvable error has occurred and a connection to Google APIs
        // could not be established. Display an error message, or handle
        // the failure silently

        // ...
    }

    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        {
            locationReceivedFromLocationUpdates = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, request, this);
            if(locationReceivedFromLocationUpdates != null)
            {
                //YES, lat and long are multi digit.
                if(Geocoder.isPresent())
                {
                    startIntentService();
                }
                else
                {
                    Log.e("ERROR:", "Geocoder is not avaiable");
                }
            }
            else
            {

            }


        }



    }

    @Override
    public void onConnectionSuspended(int i)
    {
        //put other stuff here
    }

    //update app based on the new location data, and then begin pinging servlet with the new location
    @Override
    public void onLocationChanged(Location location)
    {
        locationReceivedFromLocationUpdates = location;
        //locationReceivedFromLocationUpdates = fakeUserLocation;


        if(locationReceivedFromLocationUpdates != null)
        {
            serverURL = "http://geo.dev.deveire.com/store/location?id=" + Settings.Secure.ANDROID_ID.toString() + "&lat=" + locationReceivedFromLocationUpdates.getLatitude() + "&lon=" + locationReceivedFromLocationUpdates.getLongitude();
            //lat and long are doubles, will cause issue? nope
            Log.i("Network Update", "Attempting to start download from onLocationChanged." + serverURL);
            //aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);

            GolfHole.setIntervalLength(locationScanInterval);
            updateMap();

            //startDownload();
        }
        else
        {
            /*serverURL = "http://geo.dev.deveire.com/store/location?id=" + Settings.Secure.ANDROID_ID.toString() + "&lat=" + 52.67 + "&lon=" + -8.54;
            //lat and long are doubles, will cause issue? nope
            Log.i("Network Update", "Attempting to start download from onLocationChanged." + serverURL);
            aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);*/
            //startDownload();
            Log.e("ERROR", "Unable to send location to sevrver, current location = null");
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        //receive request changed.
    }

    @Override
    public void onSaveInstanceState(Bundle savedState)
    {
        savedState.putParcelable(SAVED_LOCATION_KEY, locationReceivedFromLocationUpdates);
        super.onSaveInstanceState(savedState);
    }

    private void restoreSavedValues(Bundle savedInstanceState)
    {
        if (savedInstanceState != null)
        {

            // Update the value of mCurrentLocation from the Bundle and update the
            // UI to show the correct latitude and longitude.
            if (savedInstanceState.keySet().contains(SAVED_LOCATION_KEY))
            {
                // Since LOCATION_KEY was found in the Bundle, we can be sure that
                // mCurrentLocationis not null.
                locationReceivedFromLocationUpdates = savedInstanceState.getParcelable(SAVED_LOCATION_KEY);
            }

        }
    }

    protected void startIntentService() {
        Intent intent = new Intent(this, geoCoderIntent.class);
        intent.putExtra(Constants.RECEIVER, geoCoderServiceResultReciever);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, locationReceivedFromLocationUpdates);
        startService(intent);
    }

    //Update activity based on the results sent back by the servlet.
    @Override
    public void updateFromDownload(String result) {
        //intervalTextView.setText("Interval: " + result);
        try
        {
            if(result != null)
            {
                JSONObject jsonResultFromServer = new JSONObject(result);
                //if the requested interval differs from the current interval, change the interval and send a locationrequest to change the settings.
                if (locationScanInterval != jsonResultFromServer.getInt("intervalRequest"))
                {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    {
                        //locationScanInterval = jsonResultFromServer.getInt("intervalRequest");
                        GolfHole.setIntervalLength(locationScanInterval * 1000);
                        request.setInterval(locationScanInterval * 1000);


                        LocationSettingsRequest.Builder requestBuilder = new LocationSettingsRequest.Builder().addLocationRequest(request);
                        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
                        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, request, this);
                        Log.i("Location Update", "Interval Changed, locationRequest changed.");
                    }
                    mapText.setText("" + locationScanInterval);
                }
            }
            else
            {
                mapText.setText("Error: network unavaiable");
            }

        }
        catch(JSONException e)
        {

        }



        Log.e("Download Output", "" + result);
        // Update your UI here based on result of download.
    }

    @Override
    public NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo;
    }

    @Override
    public void onProgressUpdate(int progressCode, int percentComplete) {
        switch(progressCode) {
            // You can add UI behavior for progress updates here.
            case Progress.ERROR:
                Log.e("Progress Error", "there was an error during a progress report at: " + percentComplete + "%");
                break;
            case Progress.CONNECT_SUCCESS:
                Log.i("Progress ", "connection successful during a progress report at: " + percentComplete + "%");
                break;
            case Progress.GET_INPUT_STREAM_SUCCESS:
                Log.i("Progress ", "input stream acquired during a progress report at: " + percentComplete + "%");
                break;
            case Progress.PROCESS_INPUT_STREAM_IN_PROGRESS:
                Log.i("Progress ", "input stream in progress during a progress report at: " + percentComplete + "%");
                break;
            case Progress.PROCESS_INPUT_STREAM_SUCCESS:
                Log.i("Progress ", "input stream processing successful during a progress report at: " + percentComplete + "%");
                break;
        }
    }

    @Override
    public void finishDownloading() {
        pingingServer = false;
        Log.i("Network Update", "finished Downloading");
        if (aNetworkFragment != null) {
            Log.e("Network Update", "network fragment not found, canceling download");
            aNetworkFragment.cancelDownload();
        }
    }

    class AddressResultReceiver extends ResultReceiver
    {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {

            // Display the address string
            // or an error message sent from the intent service.
            resultData.getString(Constants.RESULT_DATA_KEY);


            // Show a toast message if an address was found.
            if (resultCode == Constants.SUCCESS_RESULT)
            {
                Log.i("Success", "Address found");
            }
            else
            {
                Log.e("Network Error:", "in OnReceiveResult in AddressResultReceiver: " +  resultData.getString(Constants.RESULT_DATA_KEY));
            }

        }
    }
//**********[Location Update and server pinging Code]


}

/*


 */