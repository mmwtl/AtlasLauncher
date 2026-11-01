package com.ecarx.xui.adaptapi.car.base;

public interface ICarFunction {
    int getFunctionValue(int function, int zone);

    float getCustomizeFunctionValue(int function, int zone);

    boolean setFunctionValue(int function, int zone, int value);

    boolean setCustomizeFunctionValue(int function, int zone, float value);
}
