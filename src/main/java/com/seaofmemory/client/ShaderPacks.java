package com.seaofmemory.client;

import java.lang.reflect.Method;

import com.seaofmemory.SeaOfMemory;

/**
 * Whether an Iris shaderpack is active, through Iris' public API.
 * Looked up reflectively so the mod neither needs Iris to compile nor to run.
 */
final class ShaderPacks {
    private static final Method IS_IN_USE;
    private static final Object API;

    static {
        Object api = null;
        Method isInUse = null;
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = apiClass.getMethod("getInstance").invoke(null);
            isInUse = apiClass.getMethod("isShaderPackInUse");
        } catch (ClassNotFoundException e) {
            // No Iris: never a shaderpack.
        } catch (ReflectiveOperationException e) {
            SeaOfMemory.LOGGER.warn("Iris is present but its API could not be reached; assuming no shaderpack", e);
        }
        API = api;
        IS_IN_USE = isInUse;
    }

    private ShaderPacks() {
    }

    static boolean inUse() {
        if (IS_IN_USE == null) {
            return false;
        }
        try {
            return (boolean) IS_IN_USE.invoke(API);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
