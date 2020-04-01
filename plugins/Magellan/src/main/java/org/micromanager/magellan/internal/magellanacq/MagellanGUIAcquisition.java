///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
//
package org.micromanager.magellan.internal.magellanacq;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.micromanager.acqj.api.AcquisitionEvent;
import org.micromanager.acqj.internal.acqengj.AcquisitionEventIterator;
import org.micromanager.acqj.api.AcqEngMetadata;
import org.micromanager.acqj.internal.acqengj.affineTransformUtils;
import org.micromanager.acqj.internal.acqengj.XYStagePosition;
import org.micromanager.acqj.api.mda.AcqEventModules;
import org.micromanager.acqj.api.DataSink;
import org.micromanager.acqj.api.FixedSettingsAcquisition;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.surfacesandregions.Point3d;

/**
 *
 * @author Henry
 */
public class MagellanGUIAcquisition extends FixedSettingsAcquisition implements MagellanAcquisition {

   private double zOrigin_, zStep_;
   private int minSliceIndex_, maxSliceIndex_;
   private int overlapX_, overlapY_;
   private List<XYStagePosition> positions_;

   /**
    * Acquisition with fixed XY positions (although they can potentially all be
    * translated between time points Supports time points Z stacks that can
    * change at positions between time points
    *
    * Acquisition engine manages a thread that reads events, fixed area
    * acquisition has another thread that generates events
    *
    * @param settings
    * @param acqGroup
    * @throws java.lang.Exception
    */
   public MagellanGUIAcquisition(MagellanGUIAcquisitionSettings settings) {
      super(settings, new MagellanDataManager(settings.dir_, true));
   }

   @Override
   public void addToSummaryMetadata(JSONObject summaryMetadata) {
      MagellanMD.setExploreAcq(summaryMetadata, false);
            
      zStep_ = ((MagellanGUIAcquisitionSettings) settings_).zStep_;
      
      overlapX_ = (int) (Magellan.getCore().getImageWidth() * 
              ((MagellanGUIAcquisitionSettings) settings_).tileOverlap_ / 100);
      overlapY_ = (int) (Magellan.getCore().getImageHeight() *
              ((MagellanGUIAcquisitionSettings) settings_).tileOverlap_ / 100);
      MagellanMD.setPixelOverlapX(summaryMetadata, overlapX_);
      MagellanMD.setPixelOverlapY(summaryMetadata, overlapY_);
      
      AcqEngMetadata.setZStepUm(summaryMetadata, zStep_);
      AcqEngMetadata.setZStepUm(summaryMetadata, zStep_);
      AcqEngMetadata.setIntervalMs(summaryMetadata, getTimeInterval_ms());
      createXYPositions();
      JSONArray initialPosList = createInitialPositionList();
      AcqEngMetadata.setInitialPositionList(summaryMetadata, initialPosList);
   }

   @Override
   public void addToImageMetadata(JSONObject tags) {

      //add metadata specific to magellan acquisition
      AcqEngMetadata.setIntervalMs(tags, ((MagellanGUIAcquisition) this).getTimeInterval_ms());

      //add data about surface
      //right now this only works for fixed distance from the surface
      if (getSpaceMode() == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         //add metadata about surface
         MagellanMD.setSurfacePoints(tags, getFixedSurfacePoints());
      }
   }

   @Override
   protected Iterator<AcquisitionEvent> buildAcqEventGenerator() {
      ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions
              = new ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>>();
      //Define where slice index 0 will be
      zOrigin_ = getZTopCoordinate(((MagellanGUIAcquisitionSettings) settings_).spaceMode_,
              ((MagellanGUIAcquisitionSettings) settings_), zStageHasLimits_,
              zStageLowerLimit_, zStageUpperLimit_, zStage_);

      acqFunctions.add(AcqEventModules.timelapse(((MagellanGUIAcquisitionSettings) settings_).numTimePoints_,
              (int) (((MagellanGUIAcquisitionSettings) settings_).timePointInterval_
              * (((MagellanGUIAcquisitionSettings) settings_).timeIntervalUnit_ == 1
                      ? 1000 : (((MagellanGUIAcquisitionSettings) settings_).timeIntervalUnit_ == 2 ? 60000 : 1)))));
      if (positions_ != null) {
         acqFunctions.add(AcqEventModules.positions(positions_));
      }
      if (((MagellanGUIAcquisitionSettings) settings_).spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D) {
         if (((MagellanGUIAcquisitionSettings) settings_).channels_.getNumChannels() != 0) {
            acqFunctions.add(AcqEventModules.channels(settings_.channels_));
         }
      } else if (((MagellanGUIAcquisitionSettings) settings_).spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D_SURFACE_GUIDED) {
         acqFunctions.add(surfaceGuided2D());
         if (settings_.channels_.getNumChannels() != 0) {

            acqFunctions.add(AcqEventModules.channels(settings_.channels_));
         }
      } else if (((MagellanGUIAcquisitionSettings) settings_).channelsAtEverySlice_) {
         acqFunctions.add(MagellanZStack());
         if (settings_.channels_.getNumChannels() != 0) {
            acqFunctions.add(AcqEventModules.channels(settings_.channels_));
         }
      } else {
         if (settings_.channels_.getNumChannels() != 0) {
            acqFunctions.add(AcqEventModules.channels(settings_.channels_));
         }
         acqFunctions.add(MagellanZStack());
      }
      AcquisitionEvent baseEvent = new AcquisitionEvent(this);
      baseEvent.setAxisPosition(MagellanMD.POSITION_AXIS, 0);
      return new AcquisitionEventIterator(baseEvent, acqFunctions, monitorSliceIndices());
   }

   @Override
   public String toString() {
      return settings_.toString();
   }

   public int getMinSliceIndex() {
      return minSliceIndex_;
   }

   public int getMaxSliceIndex() {
      return maxSliceIndex_;
   }

   public double getTimeInterval_ms() {
      return ((MagellanGUIAcquisitionSettings) settings_).timePointInterval_
              * (((MagellanGUIAcquisitionSettings) settings_).timeIntervalUnit_ == 1
                      ? 1000 : (((MagellanGUIAcquisitionSettings) settings_).timeIntervalUnit_ == 2 ? 60000 : 1));
   }

   private Function<AcquisitionEvent, AcquisitionEvent> monitorSliceIndices() {
      return (AcquisitionEvent event) -> {
         maxSliceIndex_ = Math.max(maxSliceIndex_, event.getZIndex());
         minSliceIndex_ = Math.min(minSliceIndex_, event.getZIndex());
         return event;
      };
   }

   @Override
   public double getZCoordOfNonnegativeZIndex(int displaySliceIndex) {
      displaySliceIndex += minSliceIndex_;
      return zOrigin_ + zStep_ * displaySliceIndex;
   }

   @Override
   public int getDisplaySliceIndexFromZCoordinate(double z) {
      return (int) Math.round((z - zOrigin_) / zStep_) - minSliceIndex_;
   }

   /**
    * Fancy Z stack Magellan style
    */
   protected Function<AcquisitionEvent, Iterator<AcquisitionEvent>> MagellanZStack() {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {
            private int sliceIndex_ = (int) Math.round((getZTopCoordinate(
                    ((MagellanGUIAcquisitionSettings) settings_).spaceMode_,
                    ((MagellanGUIAcquisitionSettings) settings_),
                    zStageHasLimits_, zStageLowerLimit_, zStageUpperLimit_, zStage_) - zOrigin_) / zStep_);

            @Override
            public boolean hasNext() {
               double zPos = zOrigin_ + sliceIndex_ * zStep_;
               boolean undefined = isImagingVolumeUndefinedAtPosition(((MagellanGUIAcquisitionSettings) settings_).spaceMode_,
                       ((MagellanGUIAcquisitionSettings) settings_), event.getXY());
               //position is below z stack or limit of focus device, z stack finished
               boolean below = isZBelowImagingVolume(((MagellanGUIAcquisitionSettings) settings_).spaceMode_,
                       ((MagellanGUIAcquisitionSettings) settings_), event.getXY(), zPos, zOrigin_)
                       || (zStageHasLimits_ && zPos > zStageUpperLimit_);
               return (undefined || below) ? false : true;
            }

            @Override
            public AcquisitionEvent next() {
               double zPos = zOrigin_ + sliceIndex_ * zStep_;
               while (isZAboveImagingVolume(((MagellanGUIAcquisitionSettings) settings_).spaceMode_,
                       ((MagellanGUIAcquisitionSettings) settings_),
                       event.getXY(), zPos, zOrigin_) || (zStageHasLimits_ && zPos < zStageLowerLimit_)) {
                  sliceIndex_++;
                  zPos = zOrigin_ + sliceIndex_ * zStep_;
               }
               AcquisitionEvent sliceEvent = event.copy();
               //Do plus equals here in case z positions have been modified by another function (e.g. channel specific focal offsets)
               
               sliceEvent.setZ(sliceIndex_, (sliceEvent.getZPosition() == null
                       ? 0 : sliceEvent.getZPosition()) + zPos);
               sliceIndex_++;
               return sliceEvent;
            }
         };
      };

   }

   private Function<AcquisitionEvent, Iterator<AcquisitionEvent>> surfaceGuided2D() {
      return (AcquisitionEvent event) -> {
         //index all slcies as 0, even though they may nto be in the same plane
         double zPos;
         if (((MagellanGUIAcquisitionSettings) settings_).collectionPlane_ == null) {
            Log.log("Expected surface but didn't find one. Check acquisition settings");
            throw new RuntimeException("Expected surface but didn't find one. Check acquisition settings");
         }
         if (((MagellanGUIAcquisitionSettings) settings_).collectionPlane_.getCurentInterpolation().isInterpDefined(
                 event.getXY().getCenter().x, event.getXY().getCenter().y)) {
            zPos = ((MagellanGUIAcquisitionSettings) settings_).collectionPlane_.getCurentInterpolation().getInterpolatedValue(
                    event.getXY().getCenter().x, event.getXY().getCenter().y);
         } else {
            zPos = ((MagellanGUIAcquisitionSettings) settings_).collectionPlane_.getExtrapolatedValue(event.getXY().getCenter().x, event.getXY().getCenter().y);
         }
         event.setZ(0, event.getZPosition() + zPos);
         //Make z index all 0 for the purposes of the display even though they may be in very differnet locations
         return Stream.of(event).iterator();
      };

   }

   public static boolean isImagingVolumeUndefinedAtPosition(int spaceMode, MagellanGUIAcquisitionSettings settings, XYStagePosition position) {
      if (spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         return !settings.xyFootprint_.isDefinedAtPosition(position);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return !settings.topSurface_.isDefinedAtPosition(position)
                 && !settings.bottomSurface_.isDefinedAtPosition(position);
      }
      return false;
   }

   /**
    * This function and the one below determine which slices will be collected
    * for a given position
    *
    * @param position
    * @param zPos
    * @return
    */
   public static boolean isZAboveImagingVolume(int spaceMode, MagellanGUIAcquisitionSettings settings, XYStagePosition position, double zPos, double zOrigin) {
      if (spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         boolean extrapolate = settings.fixedSurface_ != settings.xyFootprint_;
         //extrapolate only if different surface used for XY positions than footprint
         return settings.fixedSurface_.isPositionCompletelyAboveSurface(position, settings.fixedSurface_, zPos + settings.distanceAboveFixedSurface_, extrapolate);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return settings.topSurface_.isPositionCompletelyAboveSurface(position, settings.topSurface_, zPos + settings.distanceAboveTopSurface_, false);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.CUBOID_Z_STACK) {
         return zPos < settings.zStart_;
      } else {
         //no zStack
         return zPos < zOrigin;
      }
   }

   public static boolean isZBelowImagingVolume(int spaceMode, MagellanGUIAcquisitionSettings settings, XYStagePosition position, double zPos, double zOrigin) {
      if (spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         boolean extrapolate = settings.fixedSurface_ != settings.xyFootprint_;
         //extrapolate only if different surface used for XY positions than footprint
         return settings.fixedSurface_.isPositionCompletelyBelowSurface(position, settings.fixedSurface_, zPos - settings.distanceBelowFixedSurface_, extrapolate);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return settings.bottomSurface_.isPositionCompletelyBelowSurface(position, settings.bottomSurface_, zPos - settings.distanceBelowBottomSurface_, false);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.CUBOID_Z_STACK) {
         return zPos > settings.zEnd_;
      } else {
         //no zStack
         return zPos > zOrigin;
      }
   }

//           
   public static double getZTopCoordinate(int spaceMode, MagellanGUIAcquisitionSettings settings,
           boolean zStageHasLimits, double zStageLowerLimit, double zStageUpperLimit, String zStage) {
      boolean towardsSampleIsPositive = true;
      if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK
              || spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         int dir = 0;
         try {
            dir = Magellan.getCore().getFocusDirection(Magellan.getCore().getFocusDevice());
         } catch (Exception ex) {
            Log.log("Couldnt get focus direction from  core");
            throw new RuntimeException();
         }
         if (dir > 0) {
            towardsSampleIsPositive = true;
         } else if (dir < 0) {
            towardsSampleIsPositive = false;
         } else {
            Log.log("Couldn't get focus direction of Z drive. Configre using \"Devices--Hardware Configuration Wizard\"");
            throw new RuntimeException("Focus direction undefined");
         }
      }

      if (spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         Point3d[] interpPoints = settings.fixedSurface_.getPoints();
         if (towardsSampleIsPositive) {
            double top = interpPoints[0].z - settings.distanceAboveFixedSurface_;
            return zStageHasLimits ? Math.max(zStageLowerLimit, top) : top;
         } else {
            double top = interpPoints[interpPoints.length - 1].z + settings.distanceAboveFixedSurface_;
            return zStageHasLimits ? Math.max(zStageUpperLimit, top) : top;
         }
      } else if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         if (towardsSampleIsPositive) {
            Point3d[] interpPoints = settings.topSurface_.getPoints();
            double top = interpPoints[0].z - settings.distanceAboveTopSurface_;
            return zStageHasLimits ? Math.max(zStageLowerLimit, top) : top;
         } else {
            Point3d[] interpPoints = settings.topSurface_.getPoints();
            double top = interpPoints[interpPoints.length - 1].z + settings.distanceAboveTopSurface_;
            return zStageHasLimits ? Math.max(zStageLowerLimit, top) : top;
         }
      } else if (spaceMode == MagellanGUIAcquisitionSettings.CUBOID_Z_STACK) {
         return settings.zStart_;
      } else {
         try {
            //region2D or no region
            return Magellan.getCore().getPosition(zStage);
         } catch (Exception ex) {
            Log.log("couldn't get z position", true);
            throw new RuntimeException();
         }
      }
   }

   //TODO: this could be generalized into a method to get metadata specific to any acwuisiton surface type
   public JSONArray getFixedSurfacePoints() {
      Point3d[] points = ((MagellanGUIAcquisitionSettings) settings_).fixedSurface_.getPoints();
      JSONArray pointArray = new JSONArray();
      for (Point3d p : points) {
         pointArray.put(p.x + "_" + p.y + "_" + p.z);
      }
      return pointArray;
   }

   public int getSpaceMode() {
      return ((MagellanGUIAcquisitionSettings) settings_).spaceMode_;
   }

   private void createXYPositions() {
      try {
         if (((MagellanGUIAcquisitionSettings) settings_).xyFootprint_ == null) { //Use current stage position
            positions_ = new ArrayList<XYStagePosition>();
            int fullTileWidth = (int) Magellan.getCore().getImageWidth();
            int fullTileHeight = (int) Magellan.getCore().getImageHeight();
            positions_.add(new XYStagePosition(new Point2D.Double(
                    Magellan.getCore().getXPosition(), Magellan.getCore().getYPosition()),
                    fullTileWidth, fullTileHeight, 
                    overlapX_, overlapY_, 0, 0, affineTransformUtils.getAffineTransform(
                            Magellan.getCore().getXPosition(), Magellan.getCore().getXPosition())));
         } else {
            positions_ = ((MagellanGUIAcquisitionSettings) settings_).xyFootprint_.getXYPositions(((MagellanGUIAcquisitionSettings) settings_).tileOverlap_);
         }

      } catch (Exception e) {
         e.printStackTrace();
         Log.log("Problem with Acquisition's XY positions. Check acquisition settings");
         throw new RuntimeException();
      }
   }

   protected JSONArray createInitialPositionList() {
      if (positions_ == null) {
         return null;
      }
      JSONArray pList = new JSONArray();
      for (XYStagePosition xyPos : positions_) {
         pList.put(xyPos.toJSON());
      }
      return pList;
   }

   @Override
   public int getOverlapX() {
      return overlapX_;
   }

   @Override
   public int getOverlapY() {
      return overlapY_;
   }

   @Override
   public double getZStep() {
      return zStep_;
   }


}
