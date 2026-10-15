package io.joshuasalcedo.webview4j.ffi;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NativeTest {

    @Test
    void loadsTheLibraryFromTheJarAndCallsIntoIt() {
        assertTrue(Native.version().matches("\\d+\\.\\d+\\.\\d+.*"), Native.version());
    }

    @Test
    void theJarCarriesTheLibraryForThisPlatform() {
        String resource = "/natives/" + NativeLibrary.platform() + "/" + System.mapLibraryName(NativeLibrary.NAME);
        assertNotNull(NativeLibrary.class.getResource(resource), resource);
    }
}
