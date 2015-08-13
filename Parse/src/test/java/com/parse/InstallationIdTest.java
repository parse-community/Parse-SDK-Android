/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class InstallationIdTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testGetGeneratesInstallationIdAndFile() throws Exception{
    File installationIdFile = new File(temporaryFolder.getRoot(), "installationId");
    InstallationId installationId = new InstallationId(installationIdFile);

    String installationIdString = installationId.get();
    assertNotNull(installationIdString);
    assertEquals(installationIdString,
        ParseFileUtils.readFileToString(installationIdFile, "UTF-8"));
  }

  @Test
  public void testGetReadsInstallationIdFromFile() throws Exception {
    File installationIdFile = new File(temporaryFolder.getRoot(), "installationId");
    InstallationId installationId = new InstallationId(installationIdFile);

    ParseFileUtils.writeStringToFile(installationIdFile, "test_installation_id", "UTF-8");
    assertEquals("test_installation_id", installationId.get());
  }

  @Test
  public void testSetWritesInstallationIdToFile() throws Exception {
    File installationIdFile = new File(temporaryFolder.getRoot(), "installationId");
    InstallationId installationId = new InstallationId(installationIdFile);

    installationId.set("test_installation_id");
    assertEquals("test_installation_id",
        ParseFileUtils.readFileToString(installationIdFile, "UTF-8"));
  }

  @Test
  public void testSetThenGet() {
    File installationIdFile = new File(temporaryFolder.getRoot(), "installationId");
    InstallationId installationId = new InstallationId(installationIdFile);

    installationId.set("test_installation_id");
    assertEquals("test_installation_id", installationId.get());
  }

  @Test
  public void testInstallationIdIsCachedInMemory() {
    File installationIdFile = new File(temporaryFolder.getRoot(), "installationId");
    InstallationId installationId = new InstallationId(installationIdFile);

    String installationIdString = installationId.get();
    ParseFileUtils.deleteQuietly(installationIdFile);
    assertEquals(installationIdString, installationId.get());
  }

  @Test
  public void testInstallationIdIsRandom() {
    File installationIdFile = new File(temporaryFolder.getRoot(), "installationId");

    String installationIdString = new InstallationId(installationIdFile).get();
    ParseFileUtils.deleteQuietly(installationIdFile);
    assertFalse(installationIdString.equals(new InstallationId(installationIdFile).get()));
  }

  @Test
  public void testSetSameDoesNotWriteToDisk() {
    File installationIdFile = new File(temporaryFolder.getRoot(), "installationId");
    InstallationId installationId = new InstallationId(installationIdFile);

    String installationIdString = installationId.get();
    ParseFileUtils.deleteQuietly(installationIdFile);
    installationId.set(installationIdString);
    assertFalse(installationIdFile.exists());
  }

  @Test
  public void testSetNullDoesNotPersist() {
    File installationIdFile = new File(temporaryFolder.getRoot(), "installationId");
    InstallationId installationId = new InstallationId(installationIdFile);

    String installationIdString = installationId.get();
    installationId.set(null);
    assertEquals(installationIdString, installationId.get());
  }

  @Test
  public void testSetEmptyStringDoesNotPersist() {
    File installationIdFile = new File(temporaryFolder.getRoot(), "installationId");
    InstallationId installationId = new InstallationId(installationIdFile);

    String installationIdString = installationId.get();
    installationId.set("");
    assertEquals(installationIdString, installationId.get());
  }
}
