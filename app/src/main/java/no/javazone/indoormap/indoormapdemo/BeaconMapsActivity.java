package no.javazone.indoormap.indoormapdemo;

import android.*;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.Region;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.IndoorBuilding;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.ui.IconGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import no.javazone.indoormap.indoormapdemo.beacon.EstimoteBeaconManager;
import no.javazone.indoormap.indoormapdemo.maps.MarkerEntry;
import no.javazone.indoormap.indoormapdemo.maps.MarkerModel;
import no.javazone.indoormap.indoormapdemo.model.Coordinates;
import no.javazone.indoormap.indoormapdemo.model.JzBeaconRegion;
import no.javazone.indoormap.indoormapdemo.model.JzRegionList;
import no.javazone.indoormap.indoormapdemo.util.MapUtils;
import no.javazone.indoormap.indoormapdemo.util.NetworkUtils;
import no.javazone.indoormap.indoormapdemo.util.PermissionsUtils;

import static no.javazone.indoormap.indoormapdemo.R.attr.icon;
import static no.javazone.indoormap.indoormapdemo.SlideableInfoFragment.*;

public class BeaconMapsActivity extends FragmentActivity
        implements Callback,
        GoogleMap.OnMarkerClickListener,
        GoogleMap.OnIndoorStateChangeListener, GoogleMap.OnMapClickListener, OnMapReadyCallback {

    private static final LatLng OSOLOSPEKTRUM = new LatLng(59.9130, 10.7547);
    private static final int REQUEST_LOCATION_PERMISSION = 1;

    public static final String[] PERMISSIONS =
            new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION};

    // Initial camera zoom
    private static final float CAMERA_ZOOM = 18;
    private static final float DETAILED_MAP_ZOOM_THRESHOLD = 17;
    private static final float CAMERA_BEARING = -27;
    private static Marker mCurrentLocationMarker = null;
    private static final int INVALID_FLOOR = Integer.MIN_VALUE;
    private static final int INITIAL_FLOOR_COUNT = 3;
    private static final int OSLOSPEKTRUM_DEFAULT_LEVEL_INDEX = 1;
    private boolean mMyLocationEnabled = false;
    private EstimoteBeaconManager mEstimoteBeaconManager;
    private ArrayList<JzBeaconRegion> mBeaconRegionList;
    private ArrayList<Region> mRegionList;
    DatabaseReference myRef;
    DatabaseReference childRef;

    private static final String TAG = "BeaconMapsActivity";

    // Markers stored by id
    private HashMap<String, MarkerModel> mMarkers = new HashMap<>();
    private ArrayList<GroundOverlay> mGroundOverlays = new ArrayList<>();
    // Markers stored by floor
    private SparseArray<ArrayList<Marker>> mFloorMarkerMapping =
            new SparseArray<>(INITIAL_FLOOR_COUNT);


    private IndoorBuilding mOsloSpektrumBuilding = null;

    // currently displayed floor
    private int mFloor = INVALID_FLOOR;

    private Marker mActiveMarker = null;
    private BitmapDescriptor ICON_ACTIVE;
    private ArrayList<BitmapDescriptor> mFloorIcons = new ArrayList<>();

    private boolean mAtOsloSpektrum = false;
    private Marker mOsloSpektrumMarker = null;
    private SupportMapFragment mMapFragment;
    private MapInfoFragment mInfoFragment;
    private GoogleMap mMap;
    private Rect mMapInsets = new Rect();

    private String mHighlightedRoomName = null;
    private MarkerModel mHighlightedRoom = null;
    private final static int mNumberFloors = 3;

    private int mInitialFloor = OSLOSPEKTRUM_DEFAULT_LEVEL_INDEX;

    public static final int DEFAULT_BUTTON_COLOR = R.color.jz_yellow;
    public static final int DEFAULT_BUTTON_SELECTED_COLOR = android.R.color.darker_gray;

    private static final int TOKEN_LOADER_MARKERS = 0x1;
    //For Analytics tracking
    public static final String SCREEN_LABEL = "Map";

    public Button mFloor1Button;
    public Button mFloor2Button;
    public Button mFloor3Button;
    public Button mFloorAllButton;

    @Override
    public void onInfoSizeChanged(int left, int top, int right, int bottom) {
        if (mInfoFragment instanceof SlideableInfoFragment) {
            // SlideableInfoFragment is only shown on phone layouts at the bottom of the screen,
            // but only up to 50% of the total height of the screen
            if ((float) top / (float) bottom > MAX_PANEL_PADDING_FACTOR) {

                setMapInsets(0, 0, 0, bottom - top);
            }
            final int bottomPadding = Math.min(bottom - top,
                    Math.round(bottom * MAX_PANEL_PADDING_FACTOR));
            setMapInsets(0, 0, 0, bottomPadding);
        }
    }


    public interface Callbacks {

        void onInfoHide();

        void onInfoShowOsloSpektrum();

        void onInfoShowTitle(String label, int roomType);

        void onInfoShowSessionlist(String roomId, String roomTitle, int roomType);

        void onInfoShowFirstSessionTitle(String roomId, String roomTitle, int roomType);
    }

    private Callbacks sDummyCallbacks = new Callbacks() {

        @Override
        public void onInfoHide() {
            if (mMap != null) {
                mInfoFragment.hide();
            }
        }

        @Override
        public void onInfoShowOsloSpektrum() {
            if (mInfoFragment != null) {
                mInfoFragment.showOsloSpektrum();
            }
        }

        @Override
        public void onInfoShowTitle(String label, int roomType) {
            if (mInfoFragment != null) {
                mInfoFragment.showTitleOnly(icon, label);
            }
        }

        @Override
        public void onInfoShowSessionlist(String roomId, String roomTitle, int roomType) {
            if (mInfoFragment != null) {
                mInfoFragment.showFirstSessionTitle(roomId, roomTitle, roomType);
            }
        }

        @Override
        public void onInfoShowFirstSessionTitle(String roomId, String roomTitle, int roomType) {
            if (mInfoFragment != null) {
                mInfoFragment.showFirstSessionTitle(roomId, roomTitle, roomType);
            }
        }

    };

    private Callbacks mCallbacks = sDummyCallbacks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beacon_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mMapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        if (mInfoFragment == null) {
            mInfoFragment = MapInfoFragment.newInstance(this);
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container_map_info, mInfoFragment, "mapsheet")
                    .commit();
        }

        ICON_ACTIVE = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
        mEstimoteBeaconManager = new EstimoteBeaconManager(
                this, this);
        mEstimoteBeaconManager.initializeEstimoteBeaconManager(this);

        mFloorAllButton = (Button) findViewById(R.id.allfloors_button);
        mFloor1Button = (Button) findViewById(R.id.floor1_button);
        mFloor2Button = (Button) findViewById(R.id.floor2_button);
        mFloor3Button = (Button) findViewById(R.id.floor3_button);

        mRegionList = new ArrayList<Region>();
        mBeaconRegionList = new ArrayList<>();



        mFloorAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAllFloors(true);
                mFloorAllButton.setBackgroundColor(ContextCompat.getColor(BeaconMapsActivity.this, DEFAULT_BUTTON_SELECTED_COLOR));
                ResetColorButton(mFloor3Button);
                ResetColorButton(mFloor2Button);
                ResetColorButton(mFloor1Button);
            }
        });

        mFloor1Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAllFloors(false);
                showMarkersForSpecificFloor(0);
                mFloor1Button.setBackgroundColor(ContextCompat.getColor(BeaconMapsActivity.this, DEFAULT_BUTTON_SELECTED_COLOR));
                ResetColorButton(mFloorAllButton);
                ResetColorButton(mFloor3Button);
                ResetColorButton(mFloor2Button);
            }
        });

        mFloor2Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAllFloors(false);
                showMarkersForSpecificFloor(1);
                mFloor2Button.setBackgroundColor(ContextCompat.getColor(BeaconMapsActivity.this, DEFAULT_BUTTON_SELECTED_COLOR));
                ResetColorButton(mFloorAllButton);
                ResetColorButton(mFloor3Button);
                ResetColorButton(mFloor1Button);

            }
        });

        mFloor3Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAllFloors(false);
                showMarkersForSpecificFloor(2);
                mFloor3Button.setBackgroundColor(ContextCompat.getColor(BeaconMapsActivity.this, DEFAULT_BUTTON_SELECTED_COLOR));
                ResetColorButton(mFloorAllButton);
                ResetColorButton(mFloor2Button);
                ResetColorButton(mFloor1Button);
            }
        });

        if (mFloorIcons.isEmpty()) {
            for (int i = 0; i < mNumberFloors; i++) {
                mFloorIcons.add(BitmapDescriptorFactory
                        .defaultMarker(MapUtils.createFloorColor(i)));
            }
        }

        mMapFragment.getMapAsync(this);
    }

    private void ResetColorButton(Button button) {
        button.setBackgroundColor(ContextCompat.getColor(BeaconMapsActivity.this, DEFAULT_BUTTON_COLOR));
    }


    @Override
    public void onStart() {
        super.onStart();
        mEstimoteBeaconManager.startEstimoteBeaconManager();
        if (!NetworkUtils.isGpsOn(BeaconMapsActivity.this)) {
            createGpsDialog().show();
        }
        if (!NetworkUtils.isBluetoothOn(BeaconMapsActivity.this)) {
            createBluetoothDialog().show();
        } else {
            mEstimoteBeaconManager.startMonitorEstimoteBeacons(BeaconMapsActivity.this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    public void placeMarkerLocationOnCurrentRegion(Coordinates coordinates) {
        LatLng beaconRegionLatLng = new LatLng(coordinates.getLatitude(), coordinates.getLongitude());
        if (mCurrentLocationMarker != null) {
            mCurrentLocationMarker.remove();
            mCurrentLocationMarker = null;
        }

        MarkerOptions markerOptions
                = MapUtils.createCurrentLocationMarker("Your current location", beaconRegionLatLng);
        mCurrentLocationMarker = mMap.addMarker(markerOptions);
    }

    private AlertDialog.Builder createBluetoothDialog() {
        AlertDialog.Builder buildAlertDialog = new AlertDialog.Builder(BeaconMapsActivity.this,
                R.style.Dialog_Theme);
        buildAlertDialog.setTitle("Bluetooth disabled");
        buildAlertDialog.setMessage("Please turn on Bluetooth to be able to use indoor maps");
        buildAlertDialog.setPositiveButton("Enable", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                NetworkUtils.enableBluetooth(BeaconMapsActivity.this);
                mEstimoteBeaconManager.startMonitorEstimoteBeacons(BeaconMapsActivity.this);
            }
        });
        buildAlertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        });

        return buildAlertDialog;
    }

    private AlertDialog.Builder createGpsDialog() {
        AlertDialog.Builder buildAlertDialog = new AlertDialog.Builder(this,
                R.style.Dialog_Theme);
        buildAlertDialog.setTitle("Location disabled");
        buildAlertDialog.setMessage("Please turn on Location to be able to use indoor maps");
        buildAlertDialog.setPositiveButton("Enable", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                NetworkUtils.enableGPS(BeaconMapsActivity.this);
                mEstimoteBeaconManager.startMonitorEstimoteBeacons(BeaconMapsActivity.this);
            }
        });
        buildAlertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        });

        return buildAlertDialog;
    }

    @Override
    public void onPause() {
        super.onPause();
        mEstimoteBeaconManager.stopRanging();
        mEstimoteBeaconManager.destroyEstimoteBeaconManager();
    }

    /**
     * Toggles the 'my location' button. Note that the location permission <b>must</b> have already
     * been granted when this call is made.
     *
     * @param setEnabled
     */
    public void setMyLocationEnabled(final boolean setEnabled) {
        mMyLocationEnabled = setEnabled;

        if (mMap == null) {
            return;
        }
        //noinspection MissingPermission
        mMap.setMyLocationEnabled(mMyLocationEnabled);
    }


    public void setMapInsets(int left, int top, int right, int bottom) {
        mMapInsets.set(left, top, right, bottom);
        if (mMap != null) {
            mMap.setPadding(mMapInsets.left, mMapInsets.top, mMapInsets.right, mMapInsets.bottom);
        }
    }

    public void setMapInsets(Rect insets) {
        mMapInsets.set(insets.left, insets.top, insets.right, insets.bottom);
        if (mMap != null) {
            mMap.setPadding(mMapInsets.left, mMapInsets.top, mMapInsets.right, mMapInsets.bottom);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mEstimoteBeaconManager.stopEstimoteBeaconManager();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        clearMap();
        setMyLocationEnabled(false);
    }


    /**
     * Clears the map and initialises all map variables that hold markers and overlays.
     */
    private void clearMap() {
        if (mMap != null) {
            mMap.clear();
        }

        mMarkers.clear();
        mFloorMarkerMapping.clear();

        mFloor = INVALID_FLOOR;
    }

    private void hideMarkersWhenSwitchingFloors() {
        setFloorElementsVisible(mFloor, false);
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setIndoorEnabled(true);
        mMap.setOnMarkerClickListener(this);
        mMap.setOnIndoorStateChangeListener(this);
        mMap.setOnMapClickListener(this);

        drawOverlays();

        mMap.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
            @Override
            public void onCameraIdle() {
                if (mMap.getCameraPosition().zoom <= DETAILED_MAP_ZOOM_THRESHOLD) {
                    showMarkersForAllFloors(false);
                    mOsloSpektrumMarker.setVisible(true);
                } else {
                    setFloorElementsVisible(mFloor, true);
                    mOsloSpektrumMarker.setVisible(false);
                }
            }
        });

        UiSettings mapUiSettings = mMap.getUiSettings();
        mapUiSettings.setZoomControlsEnabled(false);
        mapUiSettings.setMapToolbarEnabled(false);

        attemptEnableMyLocation();

        // Connect to the Firebase database
        FirebaseDatabase database = FirebaseDatabase.getInstance();

        myRef = database.getReference();
        childRef = myRef.child("regions");

        myRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                JzRegionList value = dataSnapshot.getValue(JzRegionList.class);
                Log.v("TAG:", "Value of database: " + value.toString());

                if (value != null) {
                    for (int i = 0; i < value.getRegions().size(); i++) {
                        JzBeaconRegion beaconRegion = value.getRegions().get(i);
                        Region region = new Region(beaconRegion.getName(),
                                UUID.fromString(value.getUUID()),
                                beaconRegion.getMajor(),
                                null);

                        mRegionList.add(region);
                        mBeaconRegionList.add(beaconRegion);
                    }

                    mEstimoteBeaconManager.setRegionList(mRegionList);

                }

                ArrayList<MarkerEntry> list = storeAndLoadMarkerEntrys(mBeaconRegionList);
                for (MarkerEntry entry : list) {
                    if (entry.options == null || entry.model == null) {
                        break;
                    }

                    Marker m = mMap.addMarker(entry.options);
                    MarkerModel model = entry.model;
                    model.marker = m;

                    ArrayList<Marker> markerList = mFloorMarkerMapping.get(model.floor);
                    if (markerList == null) {
                        markerList = new ArrayList<>();
                        mFloorMarkerMapping.put(model.floor, markerList);
                    }
                    markerList.add(m);
                    mMarkers.put(model.id, model);

                    if (mFloor > INVALID_FLOOR) {
                        setFloorElementsVisible(mFloor, true);
                    }

                    onFocusHighlightedRoom();

                    if (mHighlightedRoomName == null) {
                        mFloorAllButton.callOnClick();
                    }

                }

                enableMapElements();

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        childRef.addChildEventListener(new ChildEventListener() {

            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
            }

            public void onChildRemoved(DataSnapshot dataSnapshot) {
                String value = dataSnapshot.getValue(String.class);
            }

            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {
            }

            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.w("TAG:", "Failed to read value.", error.toException());
            }
        });

        setupMap(true);
    }

    private void drawOverlays() {
        if(mGroundOverlays.isEmpty()) {

            LatLngBounds osloSpektrumLatLngBounds = new LatLngBounds(
                    new LatLng(59.91245775721807, 10.753627974205074),       // South west corner
                    new LatLng(59.91353895983281, 10.755966466270479));      // North east corner

            GroundOverlayOptions osloSpektrumLevel0 = new GroundOverlayOptions()
                    .image(BitmapDescriptorFactory.fromResource(R.drawable.oslospektrum_level0))
                    .positionFromBounds(osloSpektrumLatLngBounds);

            GroundOverlayOptions osloSpektrumLevel1 = new GroundOverlayOptions()
                    .image(BitmapDescriptorFactory.fromResource(R.drawable.oslospektrum_level1))
                    .positionFromBounds(osloSpektrumLatLngBounds);

            GroundOverlayOptions osloSpektrumLevel2 = new GroundOverlayOptions()
                    .image(BitmapDescriptorFactory.fromResource(R.drawable.oslospektrum_level2))
                    .positionFromBounds(osloSpektrumLatLngBounds);


            mGroundOverlays.add(mMap.addGroundOverlay(osloSpektrumLevel0));
            mGroundOverlays.add(mMap.addGroundOverlay(osloSpektrumLevel1));
            mGroundOverlays.add(mMap.addGroundOverlay(osloSpektrumLevel2));

            mGroundOverlays.get(0).setVisible(true);
            for (int i = 1; i < mGroundOverlays.size(); i++) {
                mGroundOverlays.get(i).setVisible(false);
            }
        }
    }

    public ArrayList<MarkerEntry> storeAndLoadMarkerEntrys(ArrayList<JzBeaconRegion> beaconRegions) {
        ArrayList<MarkerEntry> list = null;
        final int count = beaconRegions.size();
        list = new ArrayList<>(count);

        for (JzBeaconRegion region : beaconRegions) {
            final IconGenerator labelIconGenerator = MapUtils.getLabelIconGenerator(this);

            final String id = region.getName();
            final int floor = region.getLevel();
            final double lat = region.getCoordinates().getLatitude();
            final double lon = region.getCoordinates().getLongitude();
            final int type =
                    MapUtils.detectMarkerType(region.getType());
            final String label = region.getDescription();

            final LatLng position = new LatLng(lat, lon);
            MarkerOptions marker = null;
            if (type == MarkerModel.TYPE_LABEL) {
                // Label markers contain the label as its icon
                marker = MapUtils.createLabelMarker(labelIconGenerator, id, position, label);
            } else if (type != MarkerModel.TYPE_INACTIVE) {
                // All other markers (that are not inactive) contain a pin icon
                marker = MapUtils.createFloorMarkers(id, floor, position);
            }

            MarkerModel model = new MarkerModel(id, floor, type, label, null);
            MarkerEntry entry = new MarkerEntry(model, marker);

            list.add(entry);
        }

        return list;
    }

    public void showMarkersForAllFloors(boolean visible) {
        for (int i = 0; i < mNumberFloors; i++) {
            final ArrayList<Marker> markers = mFloorMarkerMapping.get(i);
            if (markers != null) {
                for (Marker m : markers) {
                    m.setVisible(visible);
                }
            }
        }
    }

    private void setupMap(boolean resetCamera) {
        showMarkersForAllFloors(true);
        mOsloSpektrumMarker = mMap
                .addMarker(MapUtils.createOsloSpektrumMarker(OSOLOSPEKTRUM).visible(false));

        if (resetCamera) {
            centerOnOsloSpektrum(false);
        }
    }

    private void centerOnOsloSpektrum(boolean animate) {
        CameraUpdate camera = CameraUpdateFactory.newCameraPosition(
                new CameraPosition.Builder().bearing(CAMERA_BEARING).target(OSOLOSPEKTRUM)
                        .zoom(CAMERA_ZOOM).tilt(0f).build());
        if (animate) {
            mMap.animateCamera(camera);
        } else {
            mMap.moveCamera(camera);
        }
    }

    private void showFloorElementsIndex(int floor) {
        // Hide previous floor elements if the floor has changed
        if (mFloor != floor) {
            setFloorElementsVisible(mFloor, false);
        }

        mFloor = floor;

        if (mAtOsloSpektrum) {
            // Always hide the Oslo Spektrum marker if a floor is shown
            mOsloSpektrumMarker.setVisible(false);
            setFloorElementsVisible(mFloor, true);
        } else {
            // Show Oslo Spektrum marker if not at Oslo Spektrum or at an invalid floor
            mOsloSpektrumMarker.setVisible(true);
        }
    }


    /**
     * Change the visibility of all Markers and TileOverlays for a floor.
     */
    private void setFloorElementsVisible(int floor, boolean visible) {
        if (mFloor == INVALID_FLOOR) {
            showMarkersForAllFloors(true);
            return;
        }

        final ArrayList<Marker> markers = mFloorMarkerMapping.get(floor);
        if (markers != null) {
            for (Marker m : markers) {
                m.setVisible(visible);
            }
        }
    }

    private boolean isValidFloor(int floor) {
        return floor < mOsloSpektrumBuilding.getLevels().size();
    }

    private void enableMapElements() {
        if (mOsloSpektrumBuilding != null && mAtOsloSpektrum) {
            onIndoorLevelActivated(mOsloSpektrumBuilding);
        }
    }

    private void onDefocusOsloSpektrum() {
        // Hide all markers and tile overlays
        deselectActiveMarker();
        showFloorElementsIndex(INVALID_FLOOR);
        mCallbacks.onInfoShowOsloSpektrum();
    }

    private void onFocusHighlightedRoom() {
        // Highlight a room if argument is set and it exists, otherwise show the default floor
        if (mHighlightedRoomName != null && mMarkers.containsKey(mHighlightedRoomName)) {
            highlightRoom(mHighlightedRoomName);
            showMarkersForAllFloors(false);
            setFloorElementsVisible(mHighlightedRoom.floor, true);
            selectActiveMarker(mHighlightedRoom.marker);
            mCallbacks.onInfoShowFirstSessionTitle(mHighlightedRoom.id,
                    mHighlightedRoom.label,
                    mHighlightedRoom.type);
            // Reset highlighted room because it has just been displayed.
            mHighlightedRoomName = null;
        }
    }

    @Override
    public void onIndoorBuildingFocused() {
        IndoorBuilding building = mMap.getFocusedBuilding();

        if (building != null && mOsloSpektrumBuilding == null
                && mMap.getProjection().getVisibleRegion().latLngBounds.contains(OSOLOSPEKTRUM)) {
            // Store the first active building. This will always be Oslo Spektrum
            mOsloSpektrumBuilding = building;
        }

        if (!mAtOsloSpektrum && building != null && building.equals(mOsloSpektrumBuilding)) {
            // Map is focused on Oslo Spektrum Center
            mAtOsloSpektrum = true;
            onFocusHighlightedRoom();
        } else if (mAtOsloSpektrum && mOsloSpektrumBuilding != null && !mOsloSpektrumBuilding.equals(building)) {
            // Map is no longer focused on Oslo Spektrum Center
            mAtOsloSpektrum = false;
            onDefocusOsloSpektrum();
        }
        onIndoorLevelActivated(building);
    }

    @Override
    public void onIndoorLevelActivated(IndoorBuilding indoorBuilding) {
        if (indoorBuilding != null && indoorBuilding.equals(mOsloSpektrumBuilding)) {
            onOsloSpektrumFloorActivated(indoorBuilding.getActiveLevelIndex());
        }
    }

    /**
     * Called when an indoor floor level in the Oslo Spektrum building has been activated.
     * If a room is to be highlighted, the map is centered and its marker is activated.
     */
    private void onOsloSpektrumFloorActivated(int activeLevelIndex) {
        if (mHighlightedRoom != null && mFloor == mHighlightedRoom.floor) {
            // A room highlight is pending. Highlight the marker and display info details.
            onMarkerClick(mHighlightedRoom.marker);
            centerMap(mHighlightedRoom.marker.getPosition());

            // Remove the highlight room flag, because the room has just been highlighted.
            mHighlightedRoom = null;
            mHighlightedRoomName = null;
        } else if (mFloor != activeLevelIndex) {
            // Deselect and hide the info details.
            deselectActiveMarker();
            mCallbacks.onInfoHide();
        }

        // Show map elements for this floor
        showFloorElementsIndex(activeLevelIndex);
    }

    @Override
    public void onMapClick(LatLng latLng) {
        deselectActiveMarker();
        mCallbacks.onInfoHide();
    }

    private void deselectActiveMarker() {
        if (mActiveMarker != null) {
            mActiveMarker.setVisible(false);
        }
    }

    private void selectActiveMarker(Marker marker) {
        if (marker != null) {
            mActiveMarker = mMap.addMarker(MapUtils.createFloorMarkers("selected", mFloor, marker.getPosition()).visible(true));
            mActiveMarker.setIcon(ICON_ACTIVE);
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        final String title = marker.getTitle();
        final MarkerModel model = mMarkers.get(title);

        deselectActiveMarker();

        if (marker.equals(mCurrentLocationMarker)) {
            mCallbacks.onInfoShowTitle(marker.getTitle(), MarkerModel.TYPE_SESSION);
        }

        // The Oslo Spektrum marker can be compared directly.
        // For all other markers the model needs to be looked up first.
        if (marker.equals(mOsloSpektrumMarker)) {
            // Return camera to Oslo Spektrum
            centerOnOsloSpektrum(true);

        } else if (model != null && MapUtils.hasInfoTitleOnly(model.type)) {
            // Show a basic info window with a title only
            mCallbacks.onInfoShowTitle(model.label, model.type);
            selectActiveMarker(marker);

        } else if (model != null && MapUtils.hasInfoSessionList(model.type)) {
            // Type has sessions to display
            mCallbacks.onInfoShowSessionlist(model.id, model.label, model.type);
            selectActiveMarker(marker);

        }

        else {
            // Hide the bottom sheet for unknown markers
            mCallbacks.onInfoHide();
        }

        return true;
    }

    private void centerMap(LatLng position) {
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, CAMERA_ZOOM));
    }

    private void highlightRoom(String roomId) {
        MarkerModel m = mMarkers.get(roomId);
        if (m != null) {
            mHighlightedRoom = m;
        }
    }

    public void showAllFloors(boolean visible) {
        mFloor = INVALID_FLOOR;
        showMarkersForAllFloors(visible);
    }

    public void showMarkersForSpecificFloor(int floorLevel) {
        if (mFloor == floorLevel) {
            return;
        }

        mFloor = floorLevel;

        if (mMap != null) {
            hideMarkersWhenSwitchingFloors();
        }

        mCallbacks.onInfoHide();
        deselectActiveMarker();
        setFloorElementsVisible(mFloor, true);

        showGroundOverlay(floorLevel);
    }

    public void showGroundOverlay(int floorLevel) {
        for (int i = 0; i < mGroundOverlays.size(); i++) {
            if (i == floorLevel) {
                mGroundOverlays.get(i).setVisible(true);
                continue;
            }

            mGroundOverlays.get(i).setVisible(false);
        }
    }

    /**
     * Enables the 'My Location' feature on the map fragment if the location permission has been
     * granted. If the permission is not available yet, it is requested.
     */
    public void attemptEnableMyLocation() {
        // Check if the permission has already been granted.
        if (PermissionsUtils.permissionsAlreadyGranted(this, PERMISSIONS)) {
            // Permission has been granted.
            if ( mMap != null) {
                mMap.setMyLocationEnabled(true);
                return;
            }
        }

        // The permissions have not been granted yet. Request them.
        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_LOCATION_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {

        if (requestCode != REQUEST_LOCATION_PERMISSION) {
            return;
        }

        if (permissions.length == PERMISSIONS.length &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission has been granted.
            if (mMap != null) {
                mMap.setMyLocationEnabled(true);
            }
        } else {

        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}

