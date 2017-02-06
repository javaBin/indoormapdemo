package no.javazone.indoormap.indoormapdemo.maps;

import com.google.android.gms.maps.model.Marker;

public class MarkerModel {

    // Marker types
    public static final int TYPE_INACTIVE = 0;
    public static final int TYPE_SESSION = 1;
    public static final int TYPE_PLAIN = 2;
    public static final int TYPE_LABEL = 3;
    public static final int TYPE_CODELAB = 4;
    public static final int TYPE_SANDBOX = 5;
    public static final int TYPE_OFFICEHOURS = 6;
    public static final int TYPE_MISC = 7;
    public static final int TYPE_OSLOSPEKTRUM = 8
            ;
    public static final int TYPE_BOOTH = 9;
    public static final int TYPE_LOCATION = 10;

    public String id;
    public int floor;
    public int type;
    public String label;
    public Marker marker;

    public MarkerModel(String id, int floor, int type, String label, Marker marker) {
        this.id = id;
        this.floor = floor;
        this.type = type;
        this.label = label;
        this.marker = marker;
    }
}
