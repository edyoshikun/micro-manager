///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data testing
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2006-2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.data.internal;

import com.google.common.io.Files;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Test;
import org.micromanager.MultiStagePosition;
import org.micromanager.PropertyMap;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;

/**
 * This class tests that we properly transfer data between versions
 * (1.4 -> 2.0). It relies on an acquisition that was run immediately after
 * startup using the 1.4.23 MDA interface with the following parameters:
 * - 64x64 DemoCamera
 * - 2 timepoints 5000ms apart
 * - 4 stage positions arranged in a 2x2 grid, 64 pix. apart and centered on
 *   the origin (so at -32, -32; 32, -32 -- just use Create Grid and make
 *   a centered 2x2 grid)
 * - Z start of 0, end of 5, step of 5
 * - Channel 1 is Cy5, exposure time 25ms
 * - Channel 2 is DAPI, exposure time 50ms
 * - Acquisition comments of "acqcom"
 * Due to the size of this acquisition (80MB) it can't be stored in the
 * repository.
 */
public class VersionCompatTest {
   /**
    * Path to the test data we use, which was generated by Micro-Manager
    * 1.4.23.
    * NOTE: to run this test, the files in this directory must be uncompressed
    * using gunzip!
    */
   private static final String FILE_PATH = System.getProperty("user.dir") + "/src/test/resources/org/micromanager/data/internal/VersionCompatTest";

   /**
    * Maps image coordinates to hashes of the pixel data expected to be found
    * there.
    * Hashes generated via the imagePixelHash.bsh script in our test/resources
    * directory.
    */
   private static final HashMap<Coords, Integer> IMAGE_HASHES = new HashMap<Coords, Integer>();
   // ms since start at which image was received.
   private static final HashMap<Coords, Double> IMAGE_ELAPSED_TIMES = new HashMap<Coords, Double>();
   // Datetime at which image was received.
   private static final HashMap<Coords, String> IMAGE_RECEIVED_TIMES = new HashMap<Coords, String>();
   // Unique ID per-image.
   private static final HashMap<Coords, UUID> IMAGE_UUIDS = new HashMap<Coords, UUID>();
   // Device property values. These are the same for all images.
   private static final HashMap<String, String> DEVICE_PROPERTIES = new HashMap<String, String>();
   static {
      // Set up the above hashes. All per-image values need to be in the same
      // order or else the coords array won't map up correctly!
      Coords[] coords = new Coords[] {
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=0,position=0"),
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=0,position=1"),
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=0,position=2"),
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=0,position=3"),
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=1,position=0"),
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=1,position=1"),
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=1,position=2"),
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=1,position=3"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=0,position=0"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=0,position=1"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=0,position=2"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=0,position=3"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=1,position=0"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=1,position=1"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=1,position=2"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=1,position=3"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=0,position=0"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=0,position=1"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=0,position=2"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=0,position=3"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=1,position=0"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=1,position=1"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=1,position=2"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=1,position=3"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=0,position=0"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=0,position=1"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=0,position=2"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=0,position=3"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=1,position=0"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=1,position=1"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=1,position=2"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=1,position=3")
      };

      int[] hashes = new int[] {
         68068096, 846458624, 68068096, 846458624, 652540416, -2145024512,
            652540416, -2145024512, 1346601280, -617443520, 1346601280,
            -617443520, -2008150080, 1984162752, -2008150080, 1984162752,
            68068096, 846458624, 68068096, 846458624, 652540416, -2145024512,
            652540416, -2145024512, 1346601280, -617443520, 1346601280,
            -617443520, -2008150080, 1984162752, -2008150080, 1984162752,
      };

      double[] times = new double[] {
         441, 1258, 2053, 2558, 810, 1779, 2309, 2808, 608, 1535, 2166, 2667,
            1007, 1933, 2439, 2932, 5315, 5757, 6212, 6670, 5544, 5989, 6448,
            6866, 5403, 5843, 6296, 6742, 5651, 6096, 6572, 6968
      };

      String[] dates = new String[] {
         "2016-02-11 09:00:16 -0800", "2016-02-11 09:00:17 -0800",
         "2016-02-11 09:00:18 -0800", "2016-02-11 09:00:18 -0800",
         "2016-02-11 09:00:16 -0800", "2016-02-11 09:00:17 -0800",
         "2016-02-11 09:00:18 -0800", "2016-02-11 09:00:18 -0800",
         "2016-02-11 09:00:16 -0800", "2016-02-11 09:00:17 -0800",
         "2016-02-11 09:00:18 -0800", "2016-02-11 09:00:18 -0800",
         "2016-02-11 09:00:16 -0800", "2016-02-11 09:00:17 -0800",
         "2016-02-11 09:00:18 -0800", "2016-02-11 09:00:18 -0800",
         "2016-02-11 09:00:21 -0800", "2016-02-11 09:00:21 -0800",
         "2016-02-11 09:00:22 -0800", "2016-02-11 09:00:22 -0800",
         "2016-02-11 09:00:21 -0800", "2016-02-11 09:00:21 -0800",
         "2016-02-11 09:00:22 -0800", "2016-02-11 09:00:22 -0800",
         "2016-02-11 09:00:21 -0800", "2016-02-11 09:00:21 -0800",
         "2016-02-11 09:00:22 -0800", "2016-02-11 09:00:22 -0800",
         "2016-02-11 09:00:21 -0800", "2016-02-11 09:00:22 -0800",
         "2016-02-11 09:00:22 -0800", "2016-02-11 09:00:22 -0800",
      };

      UUID[] uuids = new UUID[] {
         UUID.fromString("dc65000e-d108-48b6-a75f-85e260b246bf"),
         UUID.fromString("a72e6929-b6cb-4da6-8e89-4b35dc566227"),
         UUID.fromString("4638a388-c416-4e31-9a3c-eaee2ceda561"),
         UUID.fromString("497ed6b7-6651-4d32-a24e-83c2af37614b"),
         UUID.fromString("184cc92d-7d13-4acd-bf06-165d6da2c33a"),
         UUID.fromString("56dc552c-544e-4ca8-aba2-ac8cbd9bea45"),
         UUID.fromString("56c3357e-09b3-464d-bbd6-6512ab2274e4"),
         UUID.fromString("0e077ba7-ad1e-44a2-98fc-86094a2cf53d"),
         UUID.fromString("7b83c26b-4002-41a9-b3ec-fbfc149a00f5"),
         UUID.fromString("d06e5570-5468-4de0-9cdd-639146556e00"),
         UUID.fromString("280104a8-3206-46b3-b5eb-4d61743d3cd1"),
         UUID.fromString("c6d38783-bdd9-4626-968d-5194e3316334"),
         UUID.fromString("a1fcf347-ed26-4bed-a11c-ba0cdb102ca4"),
         UUID.fromString("6d407e07-3325-4a50-ac17-d016ceb9381e"),
         UUID.fromString("8eba4e78-cd5e-4eab-9ff9-26c500fed26b"),
         UUID.fromString("144f89b4-7676-46ef-a725-2df0911a64ad"),
         UUID.fromString("4b144c73-e03a-4aa2-acde-9db9f618ac2c"),
         UUID.fromString("fd87c0f8-6c5f-4d74-a09d-99b513a4ec55"),
         UUID.fromString("a693cf0d-5aa8-479c-a8f4-8a86a7e8dfb0"),
         UUID.fromString("221e3a09-1327-4daf-a7f8-c39e220a6045"),
         UUID.fromString("551c34e0-6f78-4cc7-a66d-8892297caeba"),
         UUID.fromString("01c15121-919d-4143-a367-9a90cbc5e0e5"),
         UUID.fromString("5b536d48-30c0-4a32-9b8b-6b05838c736c"),
         UUID.fromString("003829c0-65c0-4c98-b09b-4ba9c5e641d7"),
         UUID.fromString("be1b1212-12ff-4a9a-8a83-0ef68af98d84"),
         UUID.fromString("359f4caf-3c43-4378-99f2-2b0cac575210"),
         UUID.fromString("883a2d61-701d-40f1-bf07-ec7cfc6d8891"),
         UUID.fromString("3293bba3-a85e-4bb0-b27c-d7a892af44ee"),
         UUID.fromString("1fe603c4-1b4a-48db-95e1-71bd4cf5980e"),
         UUID.fromString("c235b2e7-b550-4850-90a9-50dadc1a4c84"),
         UUID.fromString("150b4af0-a4f0-4223-ad66-f0af21a5cd29"),
         UUID.fromString("fb027cfe-34c0-4efb-bd0a-e9734d305516"),
      };

      for (int i = 0; i < coords.length; ++i) {
         IMAGE_HASHES.put(coords[i], hashes[i]);
         IMAGE_ELAPSED_TIMES.put(coords[i], times[i]);
         IMAGE_RECEIVED_TIMES.put(coords[i], dates[i]);
         IMAGE_UUIDS.put(coords[i], uuids[i]);
      }

      DEVICE_PROPERTIES.put("Autofocus-Description", "Demo auto-focus adapter");
      DEVICE_PROPERTIES.put("Autofocus-HubID", "");
      DEVICE_PROPERTIES.put("Autofocus-Name", "DAutoFocus");
      DEVICE_PROPERTIES.put("Camera-Binning", "1");
      DEVICE_PROPERTIES.put("Camera-BitDepth", "16");
      DEVICE_PROPERTIES.put("Camera-CCDTemperature", "0.0000");
      DEVICE_PROPERTIES.put("Camera-CCDTemperature RO", "0.0000");
      DEVICE_PROPERTIES.put("Camera-CameraID", "V1.0");
      DEVICE_PROPERTIES.put("Camera-CameraName", "DemoCamera-MultiMode");
      DEVICE_PROPERTIES.put("Camera-Description", "Demo Camera Device Adapter");
      DEVICE_PROPERTIES.put("Camera-DisplayImageNumber", "0");
      DEVICE_PROPERTIES.put("Camera-DropPixels", "0");
      DEVICE_PROPERTIES.put("Camera-FastImage", "0");
      DEVICE_PROPERTIES.put("Camera-FractionOfPixelsToDropOrSaturate", "0.0020");
      DEVICE_PROPERTIES.put("Camera-Gain", "0");
      DEVICE_PROPERTIES.put("Camera-HubID", "");
      DEVICE_PROPERTIES.put("Camera-MaximumExposureMs", "10000.0000");
      DEVICE_PROPERTIES.put("Camera-Mode", "Artificial Waves");
      DEVICE_PROPERTIES.put("Camera-Name", "DCam");
      DEVICE_PROPERTIES.put("Camera-Offset", "0");
      DEVICE_PROPERTIES.put("Camera-OnCameraCCDXSize", "64");
      DEVICE_PROPERTIES.put("Camera-OnCameraCCDYSize", "64");
      DEVICE_PROPERTIES.put("Camera-PixelType", "16bit");
      DEVICE_PROPERTIES.put("Camera-ReadoutTime", "0.0000");
      DEVICE_PROPERTIES.put("Camera-RotateImages", "0");
      DEVICE_PROPERTIES.put("Camera-SaturatePixels", "0");
      DEVICE_PROPERTIES.put("Camera-ScanMode", "1");
      DEVICE_PROPERTIES.put("Camera-SimulateCrash", "");
      DEVICE_PROPERTIES.put("Camera-StripeWidth", "1.0000");
      DEVICE_PROPERTIES.put("Camera-TestProperty1", "0.0000");
      DEVICE_PROPERTIES.put("Camera-TestProperty2", "0.0000");
      DEVICE_PROPERTIES.put("Camera-TestProperty3", "0.0000");
      DEVICE_PROPERTIES.put("Camera-TestProperty4", "0.0000");
      DEVICE_PROPERTIES.put("Camera-TestProperty5", "0.0000");
      DEVICE_PROPERTIES.put("Camera-TestProperty6", "0.0000");
      DEVICE_PROPERTIES.put("Camera-TransposeCorrection", "0");
      DEVICE_PROPERTIES.put("Camera-TransposeMirrorX", "0");
      DEVICE_PROPERTIES.put("Camera-TransposeMirrorY", "0");
      DEVICE_PROPERTIES.put("Camera-TransposeXY", "0");
      DEVICE_PROPERTIES.put("Camera-TriggerDevice", "");
      DEVICE_PROPERTIES.put("Camera-UseExposureSequences", "No");
      DEVICE_PROPERTIES.put("Core-AutoFocus", "Autofocus");
      DEVICE_PROPERTIES.put("Core-AutoShutter", "1");
      DEVICE_PROPERTIES.put("Core-Camera", "Camera");
      DEVICE_PROPERTIES.put("Core-ChannelGroup", "Channel");
      DEVICE_PROPERTIES.put("Core-Focus", "Z");
      DEVICE_PROPERTIES.put("Core-Galvo", "");
      DEVICE_PROPERTIES.put("Core-ImageProcessor", "");
      DEVICE_PROPERTIES.put("Core-Initialize", "1");
      DEVICE_PROPERTIES.put("Core-SLM", "");
      DEVICE_PROPERTIES.put("Core-Shutter", "Shutter");
      DEVICE_PROPERTIES.put("Core-TimeoutMs", "5000");
      DEVICE_PROPERTIES.put("Core-XYStage", "XY");
      DEVICE_PROPERTIES.put("Dichroic-ClosedPosition", "0");
      DEVICE_PROPERTIES.put("Dichroic-Description", "Demo filter wheel driver");
      DEVICE_PROPERTIES.put("Dichroic-HubID", "");
      DEVICE_PROPERTIES.put("Dichroic-Label", "400DCLP");
      DEVICE_PROPERTIES.put("Dichroic-Name", "DWheel");
      DEVICE_PROPERTIES.put("Dichroic-State", "0");
      DEVICE_PROPERTIES.put("Emission-ClosedPosition", "0");
      DEVICE_PROPERTIES.put("Emission-Description", "Demo filter wheel driver");
      DEVICE_PROPERTIES.put("Emission-HubID", "");
      DEVICE_PROPERTIES.put("Emission-Label", "Chroma-HQ620");
      DEVICE_PROPERTIES.put("Emission-Label", "Chroma-HQ700");
      DEVICE_PROPERTIES.put("Emission-Name", "DWheel");
      DEVICE_PROPERTIES.put("Emission-State", "0");
      DEVICE_PROPERTIES.put("Excitation-ClosedPosition", "0");
      DEVICE_PROPERTIES.put("Excitation-Description", "Demo filter wheel driver");
      DEVICE_PROPERTIES.put("Excitation-HubID", "");
      DEVICE_PROPERTIES.put("Excitation-Label", "Chroma-D360");
      DEVICE_PROPERTIES.put("Excitation-Label", "Chroma-HQ570");
      DEVICE_PROPERTIES.put("Excitation-Name", "DWheel");
      DEVICE_PROPERTIES.put("Excitation-State", "0");
      DEVICE_PROPERTIES.put("Objective-Description", "Demo objective turret driver");
      DEVICE_PROPERTIES.put("Objective-HubID", "");
      DEVICE_PROPERTIES.put("Objective-Label", "Nikon 10X S Fluor");
      DEVICE_PROPERTIES.put("Objective-Name", "DObjective");
      DEVICE_PROPERTIES.put("Objective-State", "1");
      DEVICE_PROPERTIES.put("Objective-Trigger", "-");
      DEVICE_PROPERTIES.put("Path-Description", "Demo light-path driver");
      DEVICE_PROPERTIES.put("Path-HubID", "");
      DEVICE_PROPERTIES.put("Path-Label", "State-0");
      DEVICE_PROPERTIES.put("Path-Name", "DLightPath");
      DEVICE_PROPERTIES.put("Path-State", "0");
      DEVICE_PROPERTIES.put("Shutter-Description", "Demo shutter driver");
      DEVICE_PROPERTIES.put("Shutter-HubID", "");
      DEVICE_PROPERTIES.put("Shutter-Name", "DShutter");
      DEVICE_PROPERTIES.put("Shutter-State", "0");
      DEVICE_PROPERTIES.put("XY-Description", "Demo XY stage driver");
      DEVICE_PROPERTIES.put("XY-HubID", "");
      DEVICE_PROPERTIES.put("XY-Name", "DXYStage");
      DEVICE_PROPERTIES.put("XY-TransposeMirrorX", "0");
      DEVICE_PROPERTIES.put("XY-TransposeMirrorY", "0");
      DEVICE_PROPERTIES.put("Z-Description", "Demo stage driver");
      DEVICE_PROPERTIES.put("Z-HubID", "");
      DEVICE_PROPERTIES.put("Z-Name", "DStage");
      DEVICE_PROPERTIES.put("Z-Position", "0.0000");
      DEVICE_PROPERTIES.put("Z-UseSequences", "No");
   }

   /**
    * Load the 1.4 dataset and verify that it matches expectations. Then save
    * it to disk using the 2.0 format.
    */
   @Test
   public void test14Load() throws IOException, JSONException {
      DefaultDataManager manager = new DefaultDataManager();
      // If you get an IOException at this point, you probably didn't
      // uncompress the files at FILE_PATH.
      Datastore store = manager.loadData(FILE_PATH, true);
      testStore(store);

      // Now save to disk and then re-load, and verify that this doesn't
      // actually change our data any.
      File tempDir = Files.createTempDir();
      store.save(Datastore.SaveMode.MULTIPAGE_TIFF, tempDir.getPath());
      store.setSavePath(tempDir.toString());
      store = manager.loadData(tempDir.getAbsolutePath(), true);
      testStore(store);
   }

   private void testStore(Datastore store) throws JSONException {
      // Test summary metadata.
      // TODO: our test does not include the "name" (unsure if this is set
      // anywhere in our code), profileName (not relevant in 1.4), channelGroup
      // (part of per-image metadata in 1.4), axisOrder (not set in 1.4),
      // startDate (not set in 1.4),
      SummaryMetadata summary = store.getSummaryMetadata();
      Assert.assertArrayEquals("Channel names", summary.getChannelNames(),
            new String[] {"Cy5", "DAPI"});
      Assert.assertEquals("Z step", summary.getZStepUm(),
            5.0, .00001);
      Assert.assertEquals("Prefix", "1.4compatTest_3", summary.getPrefix());
      Assert.assertEquals("UserName", "chriswei", summary.getUserName());
      Assert.assertEquals("MicroManagerVersion", "1.4.23-20160209", summary.getMicroManagerVersion());
      Assert.assertEquals("MetadataVersion",
            "10", summary.getMetadataVersion());
      Assert.assertEquals("ComputerName",
            "Chriss-MacBook-Pro-2.local", summary.getComputerName());
      Assert.assertEquals("Directory",
            "/Users/chriswei/proj/vale/data/testData", summary.getDirectory());
      Assert.assertEquals("WaitInterval", summary.getWaitInterval(),
              5000.0, .00001);
      Assert.assertArrayEquals("customIntervalsMs",
              summary.getCustomIntervalsMs(), new Double[]{});
      Assert.assertEquals("intendedDimensions",
              summary.getIntendedDimensions(),
              DefaultCoords.fromNormalizedString("t=2,z=2,p=4,c=2"));

      // Construct position list from JSON copied from 1.4 dump.
      JSONArray json = new JSONArray("[{\"DeviceCoordinatesUm\":{\"XY\":[-32,-32],\"Z\":[-0]},\"GridColumnIndex\":0,\"Label\":\"1-Pos_000_000\",\"GridRowIndex\":0},{\"DeviceCoordinatesUm\":{\"XY\":[32,-32],\"Z\":[0]},\"GridColumnIndex\":1,\"Label\":\"1-Pos_001_000\",\"GridRowIndex\":0},{\"DeviceCoordinatesUm\":{\"XY\":[32,32],\"Z\":[0]},\"GridColumnIndex\":1,\"Label\":\"1-Pos_001_001\",\"GridRowIndex\":1},{\"DeviceCoordinatesUm\":{\"XY\":[-32,32],\"Z\":[0]},\"GridColumnIndex\":0,\"Label\":\"1-Pos_000_001\",\"GridRowIndex\":1}]");
      /*
      MultiStagePosition[] positions = new MultiStagePosition[json.length()];
      for (int i = 0; i < json.length(); ++i) {
         positions[i] = DefaultSummaryMetadata.MultiStagePositionFromJSON(json.getJSONObject(i));
      }
      Assert.assertArrayEquals("stagePositions", summary.getStagePositions(),
            positions);
       */

      // Test image metadata and pixel hashes.
      // TODO: our test file does not test the sequence number
      // (ImageNumber), keepShutterOpen[Channels|Slices], pixelAspect,
      // ijType (not set in 1.4)
      for (Coords coords : store.getUnorderedImageCoords()) {
         try {
            Image image = store.getImage(coords);
            Assert.assertEquals("Pixel hash for " + coords,
                    (int) IMAGE_HASHES.get(coords),
                    HelperImageInfo.hashPixels(image));
            Metadata metadata = image.getMetadata();

            int position = coords.getStagePosition();
            int column = (position == 0 | position == 3) ? 0 : 1;
            int row = position < 2 ? 0 : 1;
            Assert.assertEquals("X position for " + coords,
                    column * 64 - 32.0, metadata.getXPositionUm(), .00001);
            Assert.assertEquals("Y position for " + coords,
                    row * 64 - 32.0, metadata.getYPositionUm(), .00001);
            Assert.assertEquals("Z position for " + coords,
                    metadata.getZPositionUm(),
                    coords.getZ() == 0 ? 0.0 : 5.0, .00001);

            Assert.assertEquals("Binning for " + coords, 1, (int) metadata.getBinning());
            Assert.assertEquals("Bitdepth for " + coords, 16, (int) metadata.getBitDepth());
            Assert.assertEquals("Camera for " + coords, "", metadata.getCamera());
            Assert.assertEquals("elapsedTimeMs for " + coords,
                    metadata.getElapsedTimeMs(0.0), IMAGE_ELAPSED_TIMES.get(coords));
            Assert.assertEquals("Exposure time for " + coords,
                    coords.getChannel() == 0 ? 25.0 : 50.0,
                    metadata.getExposureMs(), .00001);
            Assert.assertEquals("pixelSizeUm for " + coords,
                    metadata.getPixelSizeUm(), 1.0, .00001);
            Assert.assertEquals("positionName for " + coords,
                    metadata.getPositionName(""),
                    String.format("1-Pos_%03d_%03d", column, row));
            Assert.assertEquals("receivedTime for " + coords,
                    metadata.getReceivedTime(), IMAGE_RECEIVED_TIMES.get(coords));
            Assert.assertEquals("ROI for " + coords, metadata.getROI(),
                    new Rectangle(0, 0, 64, 64));
            Assert.assertEquals("uuid for " + coords, metadata.getUUID(),
                    IMAGE_UUIDS.get(coords));

            // Note: for backwards-compatibility, all unknown fields in old
            // datasets are imported into the userData structure, even though as
            // it happens these fields would all normally go into the scopeData
            // structure.
            PropertyMap userData = metadata.getUserData();
            Assert.assertNotNull("Non-null user data", userData);
            for (String key : userData.getKeys()) {
               Assert.assertEquals(key + " for " + coords,
                       userData.getString(key), DEVICE_PROPERTIES.get(key));
            }
         } catch (IOException io) {
            Assert.fail("Failed to open image from datastore");
         }
      }
   }
}
