package no.javazone.indoormap.indoormapdemo.util;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;

public class PermissionsUtils {

    /**
     * Determines if all {@code permissions} have already been granted.
     */
    public static boolean permissionsAlreadyGranted(@NonNull Context context,
                                                    @NonNull String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
