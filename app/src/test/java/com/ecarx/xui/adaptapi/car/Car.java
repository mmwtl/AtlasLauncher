package com.ecarx.xui.adaptapi.car;

import android.content.Context;

/** Test double of the head unit's API, with the signatures of its ecarx.adaptapi.jar. */
public final class Car {
    /** What {@link #create} returns: null while the car service is not up. */
    public static ICar instance;

    private Car() {
    }

    public static ICar create(Context context) {
        return instance;
    }
}
