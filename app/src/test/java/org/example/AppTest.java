package io.github.autocompletedemo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.github.autocompletedemo.App;

class AppTest {
    @Test void appRuns() {
        assertDoesNotThrow(() -> new App());
    }
}
