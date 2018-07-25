package com.parse;

import android.database.sqlite.SQLiteException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import bolts.Task;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = TestHelper.ROBOLECTRIC_SDK_VERSION)
public class EventuallyPinTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() {
        ParseObject.registerSubclass(EventuallyPin.class);
        ParseObject.registerSubclass(ParsePin.class);
    }

    @After
    public void tearDown() {
        ParseObject.unregisterSubclass(EventuallyPin.class);
        ParseObject.unregisterSubclass(ParsePin.class);
        Parse.setLocalDatastore(null);
        ParsePlugins.reset();
    }

    @Test
    public void testFailingFindAllPinned() throws Exception {
        OfflineStore offlineStore = mock(OfflineStore.class);
        Parse.setLocalDatastore(offlineStore);
        when(offlineStore.findFromPinAsync(eq(EventuallyPin.PIN_NAME),
                any(ParseQuery.State.class),
                any(ParseUser.class)))
                .thenReturn(Task.forError(new SQLiteException()));

        ParsePlugins plugins = mock(ParsePlugins.class);
        ParsePlugins.set(plugins);
        when(plugins.restClient()).thenReturn(ParseHttpClient.createClient(null));

        thrown.expect(SQLiteException.class);

        ParseTaskUtils.wait(EventuallyPin.findAllPinned());
    }
}