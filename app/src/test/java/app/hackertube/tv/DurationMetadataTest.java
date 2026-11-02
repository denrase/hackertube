package app.hackertube.tv;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DurationMetadataTest {
    @Test public void missingDurationIsNotInvented() { assertEquals("—", DurationMetadata.format(0)); assertEquals("—", DurationMetadata.format(-1)); }
    @Test public void shortVideo() { assertEquals("0:09", DurationMetadata.format(9)); }
    @Test public void minutes() { assertEquals("9:52", DurationMetadata.format(592)); }
    @Test public void hourBoundary() { assertEquals("1:00:00", DurationMetadata.format(3600)); }
    @Test public void longVideo() { assertEquals("1:01:20", DurationMetadata.format(3680)); }
}
