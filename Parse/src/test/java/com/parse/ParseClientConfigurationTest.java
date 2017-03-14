/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.parse.http.ParseNetworkInterceptor;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ParseClientConfigurationTest {

  private final String packageName = "com.parse.example";
  private final String serverUrl = "http://example.com/parse";
  private final String appId = "MyAppId";
  private final String clientKey = "MyClientKey";
  private final String PARSE_SERVER_URL = "com.parse.SERVER_URL";
  private final String PARSE_APPLICATION_ID = "com.parse.APPLICATION_ID";
  private final String PARSE_CLIENT_KEY = "com.parse.CLIENT_KEY";

  @Test
  public void testBuilder() {
    Parse.Configuration.Builder builder = new Parse.Configuration.Builder(null);
    builder.applicationId("foo");
    builder.clientKey("bar");
    builder.enableLocalDataStore();
    Parse.Configuration configuration = builder.build();

    assertNull(configuration.context);
    assertEquals(configuration.applicationId, "foo");
    assertEquals(configuration.clientKey, "bar");
    assertEquals(configuration.localDataStoreEnabled, true);
  }

  @Test
  public void testBuilderServerURL() {
    Parse.Configuration.Builder builder = new Parse.Configuration.Builder(null);
    builder.server("http://myserver.com/parse/");
    Parse.Configuration configuration = builder.build();
    assertEquals(configuration.server, "http://myserver.com/parse/");
  }

  @Test
  public void testBuilderServerMissingSlashURL() {
    Parse.Configuration.Builder builder = new Parse.Configuration.Builder(null);
    builder.server("http://myserver.com/missingslash");
    Parse.Configuration configuration = builder.build();
    assertEquals(configuration.server, "http://myserver.com/missingslash/");
  }

  @Test
  public void testNetworkInterceptors() {
    ParseNetworkInterceptor interceptorA = mock(ParseNetworkInterceptor.class);
    ParseNetworkInterceptor interceptorB = mock(ParseNetworkInterceptor.class);

    Parse.Configuration.Builder builder = new Parse.Configuration.Builder(null);

    builder.addNetworkInterceptor(interceptorA);
    Parse.Configuration configurationA = builder.build();
    builder.addNetworkInterceptor(interceptorB);
    Parse.Configuration configurationB = builder.build();

    assertFalse(configurationA.interceptors.contains(interceptorB));
    assertTrue(configurationB.interceptors.contains(interceptorB));

    try {
      configurationA.interceptors.add(interceptorB);
      fail("Interceptors shouldn't be mutable.");
    } catch (UnsupportedOperationException ex) {
      // Expected
    }
  }

  @Test
  public void testSetNetworkInterceptors() {
    final ParseNetworkInterceptor interceptorA = mock(ParseNetworkInterceptor.class);
    final ParseNetworkInterceptor interceptorB = mock(ParseNetworkInterceptor.class);

    Collection<ParseNetworkInterceptor> collectionA = new ArrayList<ParseNetworkInterceptor>() {{
      add(interceptorA);
      add(interceptorB);
    }};

    Collection<ParseNetworkInterceptor> collectionB = new ArrayList<ParseNetworkInterceptor>() {{
      add(interceptorB);
      add(interceptorA);
    }};

    Parse.Configuration.Builder builder = new Parse.Configuration.Builder(null);

    builder.setNetworkInterceptors(collectionA);
    Parse.Configuration configurationA = builder.build();

    builder.setNetworkInterceptors(collectionB);
    Parse.Configuration configurationB = builder.build();

    assertTrue(collectionsEqual(configurationA.interceptors, collectionA));
    assertTrue(collectionsEqual(configurationB.interceptors, collectionB));
  }

  @Test
  public void testConfigureFromManifest() throws Exception {
    Context context = mock(Context.class);
    PackageManager packageManager = mock(PackageManager.class);
    ApplicationInfo appInfo = mock(ApplicationInfo.class);
    Bundle metaData = setupMockMetaData(context, packageManager, appInfo);
    when(metaData.getString(PARSE_SERVER_URL)).thenReturn(serverUrl);
    when(metaData.getString(PARSE_APPLICATION_ID)).thenReturn(appId);
    when(metaData.getString(PARSE_CLIENT_KEY)).thenReturn(clientKey);

    Parse.Configuration.Builder builder = new Parse.Configuration.Builder(context);
    Parse.Configuration config = builder.build();
    assertEquals(serverUrl + "/", config.server);
    assertEquals(appId, config.applicationId);
    assertEquals(clientKey, config.clientKey);

    verify(context).getApplicationContext();
    verify(context).getPackageManager();
    verify(context).getPackageName();
    verify(packageManager).getApplicationInfo(packageName, PackageManager.GET_META_DATA);
    verify(metaData).getString(PARSE_SERVER_URL);
    verify(metaData).getString(PARSE_APPLICATION_ID);
    verify(metaData).getString(PARSE_CLIENT_KEY);
  }

  @Test(expected = RuntimeException.class)
  public void testConfigureFromManifestWithoutServer() throws Exception {
    Context context = mock(Context.class);
    PackageManager packageManager = mock(PackageManager.class);
    ApplicationInfo appInfo = mock(ApplicationInfo.class);
    Bundle metaData = setupMockMetaData(context, packageManager, appInfo);
    when(metaData.getString(PARSE_SERVER_URL)).thenReturn(null);
    when(metaData.getString(PARSE_APPLICATION_ID)).thenReturn(appId);
    when(metaData.getString(PARSE_CLIENT_KEY)).thenReturn(clientKey);

    // RuntimeException due to serverUrl = null
    Parse.initialize(context);
  }

  @Test(expected = RuntimeException.class)
  public void testConfigureFromManifestWithoutAppId() throws Exception {
    Context context = mock(Context.class);
    PackageManager packageManager = mock(PackageManager.class);
    ApplicationInfo appInfo = mock(ApplicationInfo.class);
    Bundle metaData = setupMockMetaData(context, packageManager, appInfo);
    when(metaData.getString(PARSE_SERVER_URL)).thenReturn(serverUrl);
    when(metaData.getString(PARSE_APPLICATION_ID)).thenReturn(null);
    when(metaData.getString(PARSE_CLIENT_KEY)).thenReturn(clientKey);

    // RuntimeException due to applicationId = null
    Parse.initialize(context);
  }

  private Bundle setupMockMetaData(
      Context context,
      PackageManager packageManager,
      ApplicationInfo appInfo) throws Exception {
    Bundle metaData = mock(Bundle.class);
    when(context.getApplicationContext()).thenReturn(context);
    when(context.getPackageManager()).thenReturn(packageManager);
    when(context.getPackageName()).thenReturn(packageName);
    when(packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)).thenReturn(appInfo);
    appInfo.metaData = metaData;
    return metaData;
  }

  private static <T> boolean collectionsEqual(Collection<T> a, Collection<T> b) {
    if (a.size() != b.size()) {
      return false;
    }

    Iterator<T> iteratorA = a.iterator();
    Iterator<T> iteratorB = b.iterator();
    for (; iteratorA.hasNext() && iteratorB.hasNext();) {
      T objectA = iteratorA.next();
      T objectB = iteratorB.next();

      if (objectA == null || objectB == null) {
        if (objectA != objectB) {
          return false;
        }
        continue;
      }

      if (!objectA.equals(objectB)) {
        return false;
      }
    }

    if (iteratorA.hasNext() || iteratorB.hasNext()) {
      return false;
    }
    return true;
  }
}
