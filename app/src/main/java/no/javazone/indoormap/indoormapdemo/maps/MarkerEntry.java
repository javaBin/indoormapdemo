package no.javazone.indoormap.indoormapdemo.maps;

import com.google.android.gms.maps.model.MarkerOptions;

public class MarkerEntry {

    public MarkerModel model;
    public MarkerOptions options;

    public MarkerEntry(MarkerModel model, MarkerOptions options) {
        this.model = model;
        this.options = options;
    }
}
