package no.javazone.indoormap.indoormapdemo.beacon;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Nearable;
import com.estimote.sdk.Region;
import com.estimote.sdk.repackaged.gson_v2_3_1.com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import no.javazone.indoormap.indoormapdemo.BeaconMapsActivity;
import no.javazone.indoormap.indoormapdemo.model.JzBeaconRegion;
import no.javazone.indoormap.indoormapdemo.model.JzRegionList;
import no.javazone.indoormap.indoormapdemo.util.JsonUtils;

public class EstimoteBeaconManager {
    private BeaconManager mBeaconManager;
    private ArrayList<Region> mRegionList;
    private JzRegionList mJzRegionList;
    private Context mContext;
    private String scanId;
    private static final String TAG = "EstimoteBeaconManager";
    private BeaconQueue mBeaconQueue;
    private ScheduledExecutorService mScheduledExecutorService;
    public BeaconMapsActivity mMapActivity;

    public EstimoteBeaconManager(Context context, BeaconMapsActivity mapActivity) {
        mContext = context;
        mBeaconManager = new BeaconManager(context);
        mBeaconQueue = new BeaconQueue();
        mScheduledExecutorService = Executors.newScheduledThreadPool(1);
        mMapActivity = mapActivity;
        mRegionList = new ArrayList<>();
    }

    public void setRegionList(ArrayList<Region> regions) {
        mRegionList = regions;
    }

    /**
     * Return the text document at url, or null if not found
     *
     * @param url to fetch
     * @return null or content of file.
     */
    private static String downloadOnlineConfig(URL url) {

        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);
            connection.connect();

            if (connection.getResponseCode() != 200) {
                return null;
            }

            InputStream in = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "utf-8"), 8);
            StringBuilder builder = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }

            in.close();
            return builder.toString();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void initializeEstimoteBeaconManager(Context context) {
        if (mBeaconManager == null) {
            mBeaconManager = new BeaconManager(context);
        }
        // Should be invoked in #onCreate.
        mBeaconManager.setNearableListener(new BeaconManager.NearableListener() {
            @Override
            public void onNearablesDiscovered(List<Nearable> nearables) {
                Log.d(TAG, "Discovered nearables: " + nearables);
            }
        });
    }

    public void startEstimoteBeaconManager() {
        // Should be invoked in #onStart.
        mBeaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override
            public void onServiceReady() {
                scanId = mBeaconManager.startNearableDiscovery();
            }
        });

        if(mScheduledExecutorService.isShutdown()) {
            mScheduledExecutorService = Executors.newScheduledThreadPool(1);
        }

        mScheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if(!mBeaconQueue.isEmpty()) {
                    mBeaconQueue.clearBeacons();
                }
            }
        },0, 15, TimeUnit.SECONDS);
    }

    public void stopRanging() {
        // invoked in #onPause
        if(mRegionList != null) {
            for (Region region : mRegionList) {
                mBeaconManager.stopRanging(region);
            }
        }

        mScheduledExecutorService.shutdown();
    }

    public void stopEstimoteBeaconManager() {
        // Should be invoked in #onStop.
        mBeaconManager.stopNearableDiscovery(scanId);
    }

    public void destroyEstimoteBeaconManager() {
        // When no longer needed. Should be invoked in #onDestroy.
        mBeaconManager.disconnect();
    }

    public void startMonitorEstimoteBeacons(Context context) {
        // called onResume
        if (mBeaconManager == null) {
            mBeaconManager = new BeaconManager(context);
        }

        mBeaconManager.connect(new BeaconManager.ServiceReadyCallback() {

            @Override
            public void onServiceReady() {
                if(mRegionList != null) {
                    for (Region region : mRegionList) {
                        mBeaconManager.startMonitoring(region);
                        mBeaconManager.startRanging(region);
                    }
                }

                mBeaconManager.setMonitoringListener(new BeaconManager.MonitoringListener() {
                    @Override
                    public void onEnteredRegion(Region region, List<Beacon> beacons) {
                        if (!beacons.isEmpty()) {
                        }
                    }

                    @Override
                    public void onExitedRegion(Region region) {

                    }

                });

                mBeaconManager.setRangingListener(new BeaconManager.RangingListener() {
                    @Override
                    public void onBeaconsDiscovered(Region region, List<Beacon> list) {
                        if (!list.isEmpty()) {
                            JzBeaconRegion beaconRegion = getRegion(region);
                            mBeaconQueue.insertBeaconInformation(beaconRegion,list);
                            BeaconQueue.BeaconInformationItem item = mBeaconQueue.getHighestRssiBeacon();
                            Beacon highestRssiBeacon = item.getmBeacon();
                            JzBeaconRegion regionLocation = item.getmRegion();
                            mMapActivity.placeMarkerLocationOnCurrentRegion(regionLocation.getCoordinates());
                        }
                    }
                });
            }
        });
    }

    private JzBeaconRegion getRegion(Region region) {
        for(JzBeaconRegion beaconRegion : mJzRegionList.getRegions()) {
            if(beaconRegion.getName().equals(region.getIdentifier())) {
                return beaconRegion;
            }
        }

        return null;
    }



}