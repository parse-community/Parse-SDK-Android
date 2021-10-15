package com.parse;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.database.sqlite.SQLiteException;
import com.parse.boltsinternal.Task;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class EventuallyPinTest {

    @Rule public final ExpectedException thrown = ExpectedException.none();

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
        when(offlineStore.findFromPinAsync(
                        eq(EventuallyPin.PIN_NAME),
                        any(ParseQuery.State.class),
                        nullable(ParseUser.class)))
                .thenReturn(Task.forError(new SQLiteException()));

        ParsePlugins plugins = mock(ParsePlugins.class);
        ParsePlugins.set(plugins);
        when(plugins.restClient()).thenReturn(ParseHttpClient.createClient(null));

        thrown.expect(SQLiteException.class);

        ParseTaskUtils.wait(EventuallyPin.findAllPinned());
    }
}
