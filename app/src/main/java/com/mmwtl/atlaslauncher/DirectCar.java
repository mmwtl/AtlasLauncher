package com.mmwtl.atlaslauncher;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * The head unit's ECarX API ({@code com.ecarx.xui.adaptapi}) called without GInputBridge, the way
 * the bridge itself calls it: {@code Car.create(context)}, then {@code ICarFunction}. The API lives
 * in the head unit's framework, so it is reached by reflection and the app bundles nothing of it.
 * Same approach as Ecarx in AtlasClimateWidget. Calls may wait for the car service: keep them off
 * threads that must not block.
 */
final class DirectCar {
    private static final String TAG = "AtlasGestures";
    private static final String CAR = "com.ecarx.xui.adaptapi.car.Car";
    private static final String CAR_INTERFACE = "com.ecarx.xui.adaptapi.car.ICar";
    private static final String FUNCTION = "com.ecarx.xui.adaptapi.car.base.ICarFunction";

    private final Context context;
    private final Method create;
    private final Method getFunctions;
    private final Method getInt;
    private final Method getFloat;
    private final Method setInt;
    private final Method setFloat;
    private Object car;
    private Object functions;

    private DirectCar(Context context) throws ReflectiveOperationException {
        this.context = context;
        create = Class.forName(CAR).getMethod("create", Context.class);
        getFunctions = Class.forName(CAR_INTERFACE).getMethod("getICarFunction");
        Class<?> function = Class.forName(FUNCTION);
        getInt = function.getMethod("getFunctionValue", int.class, int.class);
        getFloat = function.getMethod("getCustomizeFunctionValue", int.class, int.class);
        setInt = function.getMethod("setFunctionValue", int.class, int.class, int.class);
        setFloat = function.getMethod("setCustomizeFunctionValue", int.class, int.class, float.class);
    }

    /** Null when this device has no such API. */
    static DirectCar connect(Context context) {
        try {
            return new DirectCar(context);
        } catch (ReflectiveOperationException | LinkageError error) {
            Log.w(TAG, "ECarX API unavailable: " + error);
            return null;
        }
    }

    static boolean available() {
        try {
            Class.forName(CAR);
            return true;
        } catch (ClassNotFoundException | LinkageError error) {
            return false;
        }
    }

    /** The car's value, or null while the car has not answered or lacks the function. */
    Integer getInt(int id, int area) {
        return (Integer) call(getInt, id, area);
    }

    Float getFloat(int id, int area) {
        return (Float) call(getFloat, id, area);
    }

    void setInt(int id, int area, int value) {
        call(setInt, id, area, value);
    }

    void setFloat(int id, int area, float value) {
        call(setFloat, id, area, value);
    }

    private Object call(Method method, Object... args) {
        try {
            // The car service may come up later, so every call asks for it until it does.
            if (car == null) car = create.invoke(null, context);
            if (functions == null && car != null) functions = getFunctions.invoke(car);
            return functions == null ? null : method.invoke(functions, args);
        } catch (InvocationTargetException error) {
            Log.w(TAG, "ECarX " + method.getName() + " failed: " + error.getCause());
            return null;
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w(TAG, "ECarX " + method.getName() + " failed: " + error);
            return null;
        }
    }
}
