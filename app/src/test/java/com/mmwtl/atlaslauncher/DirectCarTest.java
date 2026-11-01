package com.mmwtl.atlaslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ecarx.xui.adaptapi.car.Car;
import com.ecarx.xui.adaptapi.car.ICar;
import com.ecarx.xui.adaptapi.car.base.ICarFunction;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class DirectCarTest {
    private static final int GLOBAL = Integer.MIN_VALUE;

    private final List<String> calls = new ArrayList<>();
    private final ICarFunction functions = new ICarFunction() {
        @Override
        public int getFunctionValue(int function, int zone) {
            return function + zone;
        }

        @Override
        public float getCustomizeFunctionValue(int function, int zone) {
            return 21.5f;
        }

        @Override
        public boolean setFunctionValue(int function, int zone, int value) {
            calls.add("int " + function + " " + zone + " " + value);
            return true;
        }

        @Override
        public boolean setCustomizeFunctionValue(int function, int zone, float value) {
            calls.add("float " + function + " " + zone + " " + value);
            return true;
        }
    };

    @After
    public void tearDown() {
        Car.instance = null;
    }

    private void carIsUp() {
        Car.instance = () -> functions;
    }

    @Test
    public void readsAndWritesThroughTheFunctionsInTheGivenZone() {
        carIsUp();
        DirectCar car = DirectCar.connect(null);
        assertEquals(Integer.valueOf(12), car.getInt(10, 2));
        assertEquals(Float.valueOf(21.5f), car.getFloat(10, GLOBAL));
        car.setInt(10, 2, 7);
        car.setFloat(10, GLOBAL, 22.5f);
        assertEquals(List.of("int 10 2 7", "float 10 " + GLOBAL + " 22.5"), calls);
    }

    @Test
    public void waitsForTheCarServiceToComeUp() {
        DirectCar car = DirectCar.connect(null);
        assertNull(car.getInt(10, 2));
        car.setInt(10, 2, 7);
        assertTrue(calls.isEmpty());
        carIsUp();
        assertEquals(Integer.valueOf(12), car.getInt(10, 2));
    }

    @Test
    public void reportsTheApiAsAvailableWhereItExists() {
        assertTrue(DirectCar.available());
    }
}
