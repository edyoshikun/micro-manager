///////////////////////////////////////////////////////////////////////////////
//FILE:          PointAndShootAnalyzer.java
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     PointAndShootAnalyzer plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2018
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

package org.micromanager.pointandshootanalysis;

import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Metadata;
import org.micromanager.display.DataViewer;

/**
 *
 * @author nico
 */
public class PointAndShootAnalyzer implements Runnable {
   final private Studio studio_;
   final private String fileName_;
   final private Map<String, Point> coordinates_;
   final private Map<Instant, Point> datedCoordinates_;
   final private Map<Integer, Instant> frameTimeStamps_;

   public PointAndShootAnalyzer(Studio studio, String fileName)
   {
      studio_ = studio;
      fileName_ = fileName;
      coordinates_ = new HashMap<String, Point>();
      datedCoordinates_ = new TreeMap<Instant, Point>();
      frameTimeStamps_ = new TreeMap<Integer, Instant>();
   }

   @Override
   public void run() {
      
      File f = new File(fileName_);
      if (!f.exists()) {
         studio_.logs().showError("File " + f.getName() + " does not exist");
         return;
      }
      if (!f.canRead()) {
         studio_.logs().showError("File " + f.getName() + " is not readable");
         return;
      }
      
      try {
         Consumer<String> action = new pointAndShootParser();
         Stream<String> fileLines = Files.lines(f.toPath());
         fileLines.forEach(action);
      } catch (IOException ex) {
         studio_.logs().showError("Error while parsing file: " + f.getName());
         return;
      }

      for (Map.Entry<String, Point> entry : coordinates_.entrySet()) {
         datedCoordinates_.put(dateStringToInstant(entry.getKey()),
                 entry.getValue());
      }
     
      DataViewer activeDataViewer = studio_.displays().getActiveDataViewer();
      if (activeDataViewer == null) {
         studio_.logs().showError("Please open image data first");
         return;
      }
      DataProvider dataProvider = activeDataViewer.getDataProvider();
      Iterable<Coords> unorderedImageCoords = dataProvider.getUnorderedImageCoords();
      for (Coords c : unorderedImageCoords) {
         int frame = c.getT();
         try {
            Metadata metadata = dataProvider.getImage(c).getMetadata();
            PropertyMap userData = metadata.getUserData();
            // Note that at the moment, the Map "UserData" contains a Map "Userdata"
            // This looks like a bug to me, but for now just work with it.           

            if (userData.containsPropertyMap("UserData")) {
               PropertyMap userPropertyMap = userData.getPropertyMap("UserData", null);
               if (userPropertyMap.containsString("TimeInCore")) {
                  String timeStampString = userPropertyMap.getString("TimeInCore", null);
                  if (timeStampString != null) {
                     frameTimeStamps_.put(frame, dateStringToInstant(timeStampString));
                  }
               }
               
            }
         } catch (IOException ex) {
            studio_.logs().logError(ex);
         }
      }
      
      // for each Point and Shoot timeStamp, find the frame where it happened
      for (Map.Entry<Instant, Point> entry : datedCoordinates_.entrySet()) {
         Instant entryInstant = entry.getKey();
         Iterator<Map.Entry<Integer, Instant>> it = frameTimeStamps_.entrySet().iterator();
         boolean found = false;
         Map.Entry<Integer, Instant> frameNrAndTime = null;
         while (it.hasNext() && ! found) {
            frameNrAndTime = it.next();
            if (entryInstant.isBefore(frameNrAndTime.getValue())) {
               found = true;
            }
         }
         if (found) {
            int frameNr = frameNrAndTime.getKey();
            Point pasPoint = entry.getValue();
            
            
         }
      }
                
      
   }
   
   public static Instant dateStringToInstant(String timeStampString) {
      String subSeconds = timeStampString.substring(timeStampString.lastIndexOf(".") + 1);
      timeStampString = timeStampString.substring(0, timeStampString.lastIndexOf("."));
      timeStampString = timeStampString.replace(" ", "T");
      LocalDateTime ldt = LocalDateTime.parse(timeStampString);
      ZonedDateTime zdt = ldt.atZone(ZoneId.systemDefault());
      Instant instant = zdt.toInstant();
      if (subSeconds.length() == 3) {
         instant = instant.plusMillis(Integer.parseInt(subSeconds));
      } else if (subSeconds.length() == 6) {
         instant = instant.plusNanos(1000 * Integer.parseInt(subSeconds));
      }
      return instant;
   }
   
   private class pointAndShootParser implements Consumer<String> {

      @Override
      public void accept(String t) {
         String[] parts = t.split("\t");
         if (parts.length ==3) {
            coordinates_.put(
                    parts[0], 
                    new Point( 
                        Integer.parseInt(parts[1]), 
                        Integer.parseInt(parts[2]) ) );
         }
      }
   }
   
}
