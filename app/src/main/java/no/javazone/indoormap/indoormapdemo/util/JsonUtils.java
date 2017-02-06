package no.javazone.indoormap.indoormapdemo.util;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.IOException;
import java.io.InputStream;

public class JsonUtils {
    public static String assetJSONFile (String filename, Context context) throws IOException {

        AssetManager manager = context.getAssets();
        InputStream file = manager.open(filename);
        byte[] formArray = new byte[file.available()];
        file.read(formArray);
        file.close();

        return new String(formArray);
    }
}
