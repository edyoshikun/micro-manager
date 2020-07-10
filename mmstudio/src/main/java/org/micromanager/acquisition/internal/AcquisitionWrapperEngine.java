
package org.micromanager.acquisition.internal;

import com.google.common.eventbus.Subscribe;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.StrVector;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Datastore;
import org.micromanager.data.Pipeline;
import org.micromanager.events.AcquisitionEndedEvent;
import org.micromanager.events.internal.DefaultAcquisitionEndedEvent;
import org.micromanager.events.internal.DefaultAcquisitionStartedEvent;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.interfaces.AcqSettingsListener;
import org.micromanager.internal.utils.AcqOrderMode;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;

import javax.swing.JOptionPane;
import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;


public final class AcquisitionWrapperEngine implements AcquisitionEngine {

   private CMMCore core_;
   protected Studio studio_;
   private PositionList posList_;
   private String zstage_;
   private SequenceSettings sequenceSettings;

   private IAcquisitionEngine2010 acquisitionEngine2010_;
   protected JSONObject summaryMetadata_;
   private ArrayList<AcqSettingsListener> settingsListeners_;
   private Datastore curStore_;
   private Pipeline curPipeline_;

   public AcquisitionWrapperEngine() {
      settingsListeners_ = new ArrayList<>();
      sequenceSettings = new SequenceSettings();
   }

   public SequenceSettings getSequenceSettings() { return sequenceSettings; }

   public void setSequenceSettings(SequenceSettings SequenceSettings) {
      sequenceSettings = SequenceSettings;
   }


   @Override
   public Datastore acquire() throws MMException {
      calculateSlices();
      return runAcquisition(sequenceSettings);
   }

   @Override
   public Datastore getAcquisitionDatastore() {
      return curStore_;
   }

   @Override
   public void addSettingsListener(AcqSettingsListener listener) {
       settingsListeners_.add(listener);
   }
   
   @Override
   public void removeSettingsListener(AcqSettingsListener listener) {
       settingsListeners_.remove(listener);
   }
   
   public void settingsChanged() {
       for (AcqSettingsListener listener:settingsListeners_) {
           listener.settingsChanged();
       }
   }
   
   protected IAcquisitionEngine2010 getAcquisitionEngine2010() {
      if (acquisitionEngine2010_ == null) {
         acquisitionEngine2010_ = ((MMStudio) studio_).getAcquisitionEngine2010();
      }
      return acquisitionEngine2010_;
   }

   private void calculateSlices() {
      // Slices
      sequenceSettings.slices.clear();
      if (sequenceSettings.useSlices) {
         double start = sequenceSettings.sliceZBottomUm;
         double stop = sequenceSettings.sliceZTopUm;
         double step = Math.abs(sequenceSettings.sliceZStepUm);
         if (step == 0.0) {
            throw new UnsupportedOperationException("zero Z step size");
         }
         int count = getNumSlices();
         if (start > stop) {
            step = -step;
         }
         for (int i = 0; i < count; i++) {
            sequenceSettings.slices.add(start + i * step);
         }
      }
   }

   protected Datastore runAcquisition(SequenceSettings acquisitionSettings) {
      //Make sure computer can write to selected location and that there is enough space to do so
      if (acquisitionSettings.save) {
         File root = new File(acquisitionSettings.root);
         if (!root.canWrite()) {
            int result = JOptionPane.showConfirmDialog(null, 
                    "The specified root directory\n" + root.getAbsolutePath() +
                    "\ndoes not exist. Create it?", "Directory not found.", 
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
               root.mkdirs();
               if (!root.canWrite()) {
                  ReportingUtils.showError(
                          "Unable to save data to selected location: check that location exists.\nAcquisition canceled.");
                  return null;
               }
            } else {
               ReportingUtils.showMessage("Acquisition canceled.");
               return null;
            }
         } else if (!this.enoughDiskSpace()) {
            ReportingUtils.showError(
                    "Not enough space on disk to save the requested image set; acquisition canceled.");
            return null;
         }
      }
      try {
         PositionList posListToUse = posList_;
         if (posList_ == null && sequenceSettings.usePositionList) {
            posListToUse = studio_.positions().getPositionList();
         }
         // Start up the acquisition engine
         BlockingQueue<TaggedImage> engineOutputQueue = getAcquisitionEngine2010().run(
                 acquisitionSettings, true, posListToUse,
                 studio_.getAutofocusManager().getAutofocusMethod());
         summaryMetadata_ = getAcquisitionEngine2010().getSummaryMetadata();

         boolean shouldShow = acquisitionSettings.shouldDisplayImages;
         MMAcquisition acq = new MMAcquisition(studio_, summaryMetadata_, this,
                 shouldShow);
         curStore_ = acq.getDatastore();
         curPipeline_ = acq.getPipeline();

         studio_.events().post(new DefaultAcquisitionStartedEvent(curStore_,
                  this, acquisitionSettings));

         // Start pumping images through the pipeline and into the datastore.
         DefaultTaggedImageSink sink = new DefaultTaggedImageSink(
                 engineOutputQueue, curPipeline_, curStore_, this, studio_.events());
         sink.start(new Runnable() {
            @Override
            public void run() {
               getAcquisitionEngine2010().stop();
            }
         });
        
         return curStore_;

      } catch (Throwable ex) {
         ReportingUtils.showError(ex);
         studio_.events().post(new DefaultAcquisitionEndedEvent(
                  curStore_, this));
         return null;
      }
   }

   private int getNumChannels() {
      int numChannels = 0;
      if (sequenceSettings.useChannels) {
         if (sequenceSettings.channels == null) {
            return 0;
         }
         for (ChannelSpec channel : sequenceSettings.channels) {
            if (channel.useChannel()) {
               ++numChannels;
            }
         }
      } else {
         numChannels = 1;
      }
      return numChannels;
   }

   public int getNumFrames() {
      int numFrames = sequenceSettings.numFrames;
      if (!sequenceSettings.useFrames) {
         numFrames = 1;
      }
      return numFrames;
   }

   private int getNumPositions() {
      int numPositions = Math.max(1, posList_.getNumberOfPositions());
      if (!sequenceSettings.usePositionList) {
         numPositions = 1;
      }
      return numPositions;
   }

   private int getNumSlices() {
      if (!sequenceSettings.useSlices) {
         return 1;
      }
      if (sequenceSettings.sliceZStepUm == 0) {
         // XXX How should this be handled?
         return Integer.MAX_VALUE;
      }
      return 1 + (int)Math.abs( (sequenceSettings.sliceZTopUm - sequenceSettings.sliceZBottomUm)
              / sequenceSettings.sliceZStepUm);
   }

   private int getTotalImages() {
      if (!sequenceSettings.useChannels) {
         return getNumFrames() * getNumSlices() * getNumChannels() * getNumPositions();
      }

      int nrImages = 0;
      for (ChannelSpec channel : sequenceSettings.channels) {
         if (channel.useChannel()) {
            for (int t = 0; t < getNumFrames(); t++) {
               boolean doTimePoint = true;
               if (channel.skipFactorFrame() > 0) {
                  if (t % (channel.skipFactorFrame() + 1) != 0 ) {
                     doTimePoint = false;
                  }
               }
               if (doTimePoint) {
                  if (channel.doZStack()) {
                     nrImages += getNumSlices();
                  } else {
                     nrImages++;
                  }
               }
             }
         }
      }
      return nrImages * getNumPositions();
   }

   public long getTotalMemory() {
      CMMCore core = studio_.core();
      return core.getImageWidth() * core.getImageHeight() *
              core.getBytesPerPixel() * ((long) getTotalImages());
   }

   private void updateChannelCameras() {
      ArrayList<ChannelSpec> camChannels = new ArrayList<>();
      ArrayList<ChannelSpec> channels = sequenceSettings.channels;
      for (int row = 0; row < channels.size(); row++) {
         camChannels.add(row,
                 channels.get(row).copyBuilder().camera(getSource(channels.get(row))).build());
      }
      sequenceSettings.channels = camChannels;
   }

   /*
    * Attach a runnable to the acquisition engine. Each index (f, p, c, s) can
    * be specified. Passing a value of -1 results in the runnable being attached
    * at all values of that index. For example, if the first argument is -1,
    * then the runnable will execute at every frame.
    */
   @Override
   public void attachRunnable(int frame, int position, int channel, int slice, Runnable runnable) {
      getAcquisitionEngine2010().attachRunnable(frame, position, channel, slice, runnable);
   }
   /*
    * Clear all attached runnables from the acquisition engine.
    */

   @Override
   public void clearRunnables() {
      getAcquisitionEngine2010().clearRunnables();
   }

   private String getSource(ChannelSpec channel) {
      try {
         Configuration state = core_.getConfigState(core_.getChannelGroup(), channel.config());
         if (state.isPropertyIncluded("Core", "Camera")) {
            return state.getSetting("Core", "Camera").getPropertyValue();
         } else {
            return "";
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return "";
      }
   }

   /*
   public SequenceSettings getSequenceSettings() {
      return sequenceSettings;

      SequenceSettings acquisitionSettings = new SequenceSettings();

      updateChannelCameras();

      // Frames
      if (useFrames_) {
         acquisitionSettings.useCustomIntervals = useCustomIntervals_;
         if (useCustomIntervals_) {
            acquisitionSettings.customIntervalsMs = customTimeIntervalsMs_;
            acquisitionSettings.numFrames = acquisitionSettings.customIntervalsMs.size();
         } else {
            acquisitionSettings.numFrames = numFrames_;
            acquisitionSettings.intervalMs = interval_;
         }
      } else {
         acquisitionSettings.numFrames = 0;
      }

      // Slices
      if (useSlices_) {
         double start = sliceZBottomUm_;
         double stop = sliceZTopUm_;
         double step = Math.abs(sliceZStepUm_);
         if (step == 0.0) {
            throw new UnsupportedOperationException("zero Z step size");
         }
         int count = getNumSlices();
         if (start > stop) {
            step = -step;
         }
         for (int i = 0; i < count; i++) {
            acquisitionSettings.slices.add(start + i * step);
         }
      }

      acquisitionSettings.relativeZSlice = !this.absoluteZ_;
      try {
         String zdrive = core_.getFocusDevice();
         acquisitionSettings.zReference = (zdrive.length() > 0)
                 ? core_.getPosition(core_.getFocusDevice()) : 0.0;
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      // Channels

      if (this.useChannels_) {
         for (ChannelSpec channel : channels_) {
            if (channel.useChannel()) {
               acquisitionSettings.channels.add(channel);
            }
         }
      }
      //since we're just getting this from the core, it should be safe to get 
      //regardless of whether we're using any channels. This also makes the 
      //behavior more consisitent with the setting behavior.
      acquisitionSettings.channelGroup = getChannelGroup();

      //timeFirst = true means that time points are collected at each position
      acquisitionSettings.timeFirst = (acqOrderMode_ == AcqOrderMode.POS_TIME_CHANNEL_SLICE
              || acqOrderMode_ == AcqOrderMode.POS_TIME_SLICE_CHANNEL);
      acquisitionSettings.slicesFirst = (acqOrderMode_ == AcqOrderMode.POS_TIME_CHANNEL_SLICE
              || acqOrderMode_ == AcqOrderMode.TIME_POS_CHANNEL_SLICE);

      acquisitionSettings.useAutofocus = useAutoFocus_;
      acquisitionSettings.skipAutofocusCount = afSkipInterval_;

      acquisitionSettings.keepShutterOpenChannels = keepShutterOpenForChannels_;
      acquisitionSettings.keepShutterOpenSlices = keepShutterOpenForStack_;

      acquisitionSettings.save = saveFiles_;
      if (saveFiles_) {
         acquisitionSettings.root = rootName_;
         acquisitionSettings.prefix = dirName_;
      }
      acquisitionSettings.comment = comment_;
      acquisitionSettings.usePositionList = this.useMultiPosition_;
      acquisitionSettings.cameraTimeout = this.cameraTimeout_;
      acquisitionSettings.shouldDisplayImages = shouldDisplayImages_;
      return acquisitionSettings;


   }
   */

   /*
   public void setSequenceSettings(SequenceSettings ss) {
      sequenceSettings = new MDASequenceSettings(ss);
      updateChannelCameras();

      // Frames
      useFrames_ = true;
      useCustomIntervals_ = ss.useCustomIntervals;
      if (useCustomIntervals_) {
         customTimeIntervalsMs_ = ss.customIntervalsMs;
         numFrames_ = ss.customIntervalsMs.size();
      } else {
         numFrames_ = ss.numFrames;
         interval_ = ss.intervalMs;
      }

      // Slices
      useSlices_ = true;
      if (ss.slices.size() == 0)
         useSlices_ = false;
      else if (ss.slices.size() == 1) {
         sliceZBottomUm_ = ss.slices.get(0);
         sliceZTopUm_ = sliceZBottomUm_;
         sliceZStepUm_ = 0.0;
      } else {
         sliceZBottomUm_ = ss.slices.get(0);
         sliceZTopUm_ = ss.slices.get(ss.slices.size()-1);
         sliceZStepUm_ = ss.slices.get(1) - ss.slices.get(0);
         if (sliceZBottomUm_ > sliceZBottomUm_)
            sliceZStepUm_ = -sliceZStepUm_;
      }

      absoluteZ_ = !ss.relativeZSlice;
      // NOTE: there is no adequate setting for ss.zReference
      
      // Channels
      if (ss.channels.size() > 0)
         useChannels_ = true;
      else
         useChannels_ = false;
         
      channels_ = ss.channels;
      //should check somewhere that channels actually belong to channelGroup
      //currently it is possible to set channelGroup to a group other than that
      //which channels belong to.
      setChannelGroup(ss.channelGroup);

      //timeFirst = true means that time points are collected at each position      
      if (ss.timeFirst && ss.slicesFirst) {
         acqOrderMode_ = AcqOrderMode.POS_TIME_CHANNEL_SLICE;
      }
      
      if (ss.timeFirst && !ss.slicesFirst) {
         acqOrderMode_ = AcqOrderMode.POS_TIME_SLICE_CHANNEL;
      }
      
      if (!ss.timeFirst && ss.slicesFirst) {
         acqOrderMode_ = AcqOrderMode.TIME_POS_CHANNEL_SLICE;
      }

      if (!ss.timeFirst && !ss.slicesFirst) {
         acqOrderMode_ = AcqOrderMode.TIME_POS_SLICE_CHANNEL;
      }

      useAutoFocus_ = ss.useAutofocus;
      afSkipInterval_ = ss.skipAutofocusCount;

      keepShutterOpenForChannels_ = ss.keepShutterOpenChannels;
      keepShutterOpenForStack_ = ss.keepShutterOpenSlices;

      saveFiles_ = ss.save;
      rootName_ = ss.root;
      dirName_ = ss.prefix;
      comment_ = ss.comment;
      
      useMultiPosition_ = ss.usePositionList;
      cameraTimeout_ = ss.cameraTimeout;
      shouldDisplayImages_ = ss.shouldDisplayImages;


   }
 */

//////////////////// Actions ///////////////////////////////////////////
   @Override
   public void stop(boolean interrupted) {
      try {
         if (acquisitionEngine2010_ != null) {
            acquisitionEngine2010_.stop();
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex, "Acquisition engine stop request failed");
      }
   }

   @Override
   public boolean abortRequest() {
      if (isAcquisitionRunning()) {
         String[] options = { "Abort", "Cancel" };
         int result = JOptionPane.showOptionDialog(null,
                 "Abort current acquisition task?",
                 "Micro-Manager",
                 JOptionPane.DEFAULT_OPTION,
                 JOptionPane.QUESTION_MESSAGE, null,
                 options, options[1]);
         if (result == 0) {
            stop(true);
            return true;
         }
         else {
            return false;
         }
      }
      return true;
   }

   @Override
   public boolean abortRequested() {
      return acquisitionEngine2010_.stopHasBeenRequested();
   }

   @Override
   public void shutdown() {
      stop(true);
   }

   @Override
   public void setPause(boolean state) {
      if (state) {
         acquisitionEngine2010_.pause();
      } else {
         acquisitionEngine2010_.resume();
      }
   }

//// State Queries /////////////////////////////////////////////////////
   @Override
   public boolean isAcquisitionRunning() {
      // Even after the acquisition finishes, if the pipeline is still "live",
      // we should consider the acquisition to be running.
      if (acquisitionEngine2010_ != null) {
         return (acquisitionEngine2010_.isRunning() ||
               (curPipeline_ != null && !curPipeline_.isHalted()));
      } else {
         return false;
      }
   }

   @Override
   public boolean isFinished() {
      if (acquisitionEngine2010_ != null) {
         return acquisitionEngine2010_.isFinished();
      } else {
         return false;
      }
   }

   @Override
   public boolean isMultiFieldRunning() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public long getNextWakeTime() {
      return acquisitionEngine2010_.nextWakeTime();
   }


//////////////////// Setters and Getters ///////////////////////////////

   @Override
   public void setPositionList(PositionList posList) {
      posList_ = posList;
   }

   @Override
   public void setParentGUI(Studio parent) {
      studio_ = parent;
      core_ = studio_.core();
      studio_.events().registerForEvents(this);
   }

   @Override
   public void setZStageDevice(String stageLabel_) {
      zstage_ = stageLabel_;
   }

   @Override
   public void setUpdateLiveWindow(boolean b) {
      // do nothing
   }

   @Override
   public void setFinished() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public int getCurrentFrameCount() {
      return sequenceSettings.numFrames;
   }

   @Override
   public double getFrameIntervalMs() {
      return sequenceSettings.intervalMs;
   }

   @Override
   public boolean isZSliceSettingEnabled() {
      return sequenceSettings.useSlices;
   }


   /**
    * Add new channel if the current state of the hardware permits.
    *
    * @param config - configuration name
    * @param exp
    * @param doZStack
    * @param zOffset
    * @param c
    * @return - true if successful
    */
   public boolean addChannel(String config, double exp, Boolean doZStack,
                             double zOffset, int skip, Color c, boolean use) {
      if (isConfigAvailable(config)) {
         ChannelSpec.Builder cb = new ChannelSpec.Builder();
         cb.channelGroup(this.getChannelGroup()).config(config).useChannel(use).
                 exposure(exp).doZStack(doZStack).zOffset(zOffset).color(c).
                 skipFactorFrame(skip);
         sequenceSettings.channels.add(cb.build());
         return true;
      } else {
         ReportingUtils.logError("\"" + config + "\" is not found in the current Channel group.");
         return false;
      }
   }

   @Override
   public void setChannel(int row, ChannelSpec sp) {
      sequenceSettings.channels.add(row, sp);
   }
   @Override
   public void setChannels(ArrayList<ChannelSpec> channels) {
      sequenceSettings.channels = channels;
   }


   /**
    * Get first available config group
    */
   @Override
   public String getFirstConfigGroup() {
      if (core_ == null) {
         return "";
      }

      String[] groups = getAvailableGroups();

      if (groups == null || groups.length < 1) {
         return "";
      }

      return getAvailableGroups()[0];
   }

   /**
    * Find out which channels are currently available for the selected channel group.
    * @return - list of channel (preset) names
    */
   @Override
   public String[] getChannelConfigs() {
      if (core_ == null) {
         return new String[0];
      }
      return core_.getAvailableConfigs(core_.getChannelGroup()).toArray();
   }

   @Override
   public String getChannelGroup() {
      return core_.getChannelGroup();
   }

   @Override
   public boolean setChannelGroup(String group) {
      String curGroup = core_.getChannelGroup();
      if (!(group != null &&
            (curGroup == null || !curGroup.contentEquals(group)))) {
         // Don't make redundant changes.
         return false;
      }
      if (groupIsEligibleChannel(group)) {
         try {
            core_.setChannelGroup(group);
         } catch (Exception e) {
            try {
               core_.setChannelGroup("");
            } catch (Exception ex) {
                ReportingUtils.showError(ex);
            }
            return false;
         }
         return true;
      } else {
         return false;
      }
   }

   /**
    * Resets the engine.
    */
   @Override
   @Deprecated
   public void clear() {
      // unclear what the purpose is  Delete?
   }


   @Override
   public void setShouldDisplayImages(boolean shouldDisplay) {
      sequenceSettings.shouldDisplayImages = shouldDisplay;
   }

   protected boolean enoughDiskSpace() {
      File root = new File(sequenceSettings.root);
      //Need to find a file that exists to check space
      while (!root.exists()) {
         root = root.getParentFile();
         if (root == null) {
            return false;
         }
      }
      long usableMB = root.getUsableSpace();
      return (1.25 * getTotalMemory()) < usableMB;
   }

   @Override
   public String getVerboseSummary() {
      int numFrames = getNumFrames();
      int numSlices = getNumSlices();
      int numPositions = getNumPositions();
      int numChannels = getNumChannels();

      double exposurePerTimePointMs = 0.0;
      if (sequenceSettings.useChannels) {
         for (ChannelSpec channel : sequenceSettings.channels) {
            if (channel.useChannel()) {
               double channelExposure = channel.exposure();
               if (channel.doZStack()) {
                  channelExposure *= getNumSlices();
               }
               channelExposure *= getNumPositions();
               exposurePerTimePointMs += channelExposure;
            }
         }
      } else { // use the current settings for acquisition
         try {
            exposurePerTimePointMs = core_.getExposure() * getNumSlices() * getNumPositions();
         } catch (Exception ex) {
            studio_.logs().logError(ex, "Failed to get exposure time");
         }
      }

      int totalImages = getTotalImages();
      long totalMB = getTotalMemory() / (1024 * 1024);

      double totalDurationSec = 0;
      double interval = (sequenceSettings.intervalMs > exposurePerTimePointMs) ?
              sequenceSettings.intervalMs : exposurePerTimePointMs;
      if (!sequenceSettings.useCustomIntervals) {
         totalDurationSec = interval * (numFrames - 1) / 1000.0;
      } else {
         for (Double d : sequenceSettings.customIntervalsMs) {
            totalDurationSec += d / 1000.0;
         }
      }
      totalDurationSec += exposurePerTimePointMs / 1000;
      int hrs = (int) (totalDurationSec / 3600);
      double remainSec = totalDurationSec - hrs * 3600;
      int mins = (int) (remainSec / 60);
      remainSec = remainSec - mins * 60;

      String durationString = "\nMinimum duration: ";
      if (hrs > 0) {
         durationString += hrs + "h ";
      }
      if (mins > 0 || hrs > 0) {
         durationString += mins + "m ";
      }
      durationString += NumberUtils.doubleToDisplayString(remainSec) + "s";

      String txt =
              "Number of time points: " + (!sequenceSettings.useCustomIntervals
              ? numFrames : sequenceSettings.customIntervalsMs.size())
              + "\nNumber of positions: " + numPositions
              + "\nNumber of slices: " + numSlices
              + "\nNumber of channels: " + numChannels
              + "\nTotal images: " + totalImages
              + "\nTotal memory: " + (totalMB <= 1024 ? totalMB + " MB" : NumberUtils.doubleToDisplayString(totalMB/1024.0) + " GB")
              + durationString;

      if (sequenceSettings.useFrames || sequenceSettings.usePositionList ||
              sequenceSettings.useChannels || sequenceSettings.useSlices) {
         StringBuilder order = new StringBuilder("\nOrder: ");
         if (sequenceSettings.useFrames && sequenceSettings.usePositionList) {
            if (sequenceSettings.acqOrderMode == AcqOrderMode.TIME_POS_CHANNEL_SLICE
                    || sequenceSettings.acqOrderMode == AcqOrderMode.TIME_POS_SLICE_CHANNEL) {
               order.append("Time, Position");
            } else {
               order.append("Position, Time");
            }
         } else if (sequenceSettings.useFrames) {
            order.append("Time");
         } else if (sequenceSettings.usePositionList) {
            order.append("Position");
         }

         if ((sequenceSettings.useFrames || sequenceSettings.usePositionList) &&
                 (sequenceSettings.useChannels || sequenceSettings.useSlices)) {
            order.append(", ");
         }

         if (sequenceSettings.useChannels && sequenceSettings.useSlices) {
            if (sequenceSettings.acqOrderMode == AcqOrderMode.TIME_POS_CHANNEL_SLICE
                    || sequenceSettings.acqOrderMode == AcqOrderMode.POS_TIME_CHANNEL_SLICE) {
               order.append("Channel, Slice");
            } else {
               order.append("Slice, Channel");
            }
         } else if (sequenceSettings.useChannels) {
            order.append("Channel");
         } else if (sequenceSettings.useSlices) {
            order.append("Slice");
         }

         return txt + order.toString();
      } else {
         return txt;
      }
   }

   /**
    * Find out if the configuration is compatible with the current group.
    * This method should be used to verify if the acquisition protocol is consistent
    * with the current settings.
    * @param config Configuration to be tested
    * @return True if the parameter is in the current group
    */
   @Override
   public boolean isConfigAvailable(String config) {
      StrVector vcfgs = core_.getAvailableConfigs(core_.getChannelGroup());
      for (int i = 0; i < vcfgs.size(); i++) {
         if (config.compareTo(vcfgs.get(i)) == 0) {
            return true;
         }
      }
      return false;
   }

   @Override
   public String[] getAvailableGroups() {
      StrVector groups;
      try {
         groups = core_.getAllowedPropertyValues("Core", "ChannelGroup");
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return new String[0];
      }
      ArrayList<String> strGroups = new ArrayList<>();
      for (String group : groups) {
         if (groupIsEligibleChannel(group)) {
            strGroups.add(group);
         }
      }

      return strGroups.toArray(new String[0]);
   }

   @Override
   public double getCurrentZPos() {
      if (isFocusStageAvailable()) {
         double z = 0.0;
         try {
            //core_.waitForDevice(zstage_);
            // NS: make sure we work with the current Focus device
            z = core_.getPosition(core_.getFocusDevice());
         } catch (Exception e) {
            ReportingUtils.showError(e);
         }
         return z;
      }
      return 0;
   }

   @Override
   public boolean isPaused() {
      return acquisitionEngine2010_.isPaused();
   }

   protected boolean isFocusStageAvailable() {
      return zstage_ != null && zstage_.length() > 0;
   }

   private boolean groupIsEligibleChannel(String group) {
      StrVector cfgs = core_.getAvailableConfigs(group);
      if (cfgs.size() == 1) {
         Configuration presetData;
         try {
            presetData = core_.getConfigData(group, cfgs.get(0));
            if (presetData.size() == 1) {
               PropertySetting setting = presetData.getSetting(0);
               String devLabel = setting.getDeviceLabel();
               String propName = setting.getPropertyName();
               if (core_.hasPropertyLimits(devLabel, propName)) {
                  return false;
               }
            }
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
            return false;
         }

      }
      return true;
   }

   /*
   @Override
   public void setCustomTimeIntervals(double[] customTimeIntervals) {
      if (customTimeIntervals == null || customTimeIntervals.length == 0) {
         customTimeIntervalsMs_ = null;
         enableCustomTimeIntervals(false);
      } else {
         enableCustomTimeIntervals(true);
         customTimeIntervalsMs_ = new ArrayList<Double>();
         for (double d : customTimeIntervals) {
            customTimeIntervalsMs_.add(d);
         }
      }
   }


   @Override
   public double[] getCustomTimeIntervals() {
      if (customTimeIntervalsMs_ == null) {
         return null;
      }
      double[] intervals = new double[customTimeIntervalsMs_.size()];
      for (int i = 0; i < customTimeIntervalsMs_.size(); i++) {
         intervals[i] = customTimeIntervalsMs_.get(i);
      }
      return intervals;

   }

   @Override
   public void enableCustomTimeIntervals(boolean enable) {
      useCustomIntervals_ = enable;
   }

   @Override
   public boolean customTimeIntervalsEnabled() {
      return useCustomIntervals_;
   }
   */

   /*
    * Returns the summary metadata associated with the most recent acquisition.
    */
   @Override
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   @Override
   public String getComment() {
       return sequenceSettings.comment;
   }
   
   @Subscribe
   public void onAcquisitionEnded(AcquisitionEndedEvent event) {
      curStore_ = null;
      curPipeline_ = null;
   }

   @Subscribe
   public void onShutdownCommencing(InternalShutdownCommencingEvent event) {
      if (!event.getIsCancelled() && isAcquisitionRunning()) {
         int result = JOptionPane.showConfirmDialog(null,
               "Acquisition in progress. Are you sure you want to exit and discard all data?",
               "Micro-Manager", JOptionPane.YES_NO_OPTION,
               JOptionPane.INFORMATION_MESSAGE);

         if (result == JOptionPane.YES_OPTION) {
            getAcquisitionEngine2010().stop();
         }
         else {
            event.cancelShutdown();
         }
      }
   }
}
