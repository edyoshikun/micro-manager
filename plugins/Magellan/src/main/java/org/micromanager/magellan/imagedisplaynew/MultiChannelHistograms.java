package org.micromanager.magellan.imagedisplaynew;

import org.micromanager.magellan.imagedisplaynew.ChannelControlPanel;
import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.HashMap;
import javax.swing.JPanel;
import org.micromanager.magellan.imagedisplaynew.MagellanDisplayController;
import org.micromanager.magellan.imagedisplaynew.events.DisplayClosingEvent;

///////////////////////////////////////////////////////////////////////////////
//FILE:          MultiChannelHistograms.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com, 2012
//
// COPYRIGHT:    University of California, San Francisco, 2012
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
final class MultiChannelHistograms extends JPanel {

   private HashMap<Integer, ChannelControlPanel> ccpList_;
   private MagellanDisplayController display_;
   private boolean updatingCombos_ = false;
   private HistogramControlsState hcs_;
   private ContrastPanel contrastPanel_;
   private DisplaySettings dispSettings_;

   public MultiChannelHistograms(MagellanDisplayController disp, ContrastPanel contrastPanel) {
      super();
      display_ = disp;
//      display_.registerForEvents(this);
      dispSettings_ = display_.getDisplaySettings();
      hcs_ = contrastPanel.getHistogramControlsState();

      this.setLayout(new GridLayout(1, 1));
      contrastPanel_ = contrastPanel;
      ccpList_ = new HashMap<Integer, ChannelControlPanel>();
//      setupChannelControls();
   }
   
   public void displaySettingsChanged() {
      for (ChannelControlPanel c : ccpList_.values()) {
         c.updateActiveCheckbox(dispSettings_.isActive(c.getChannelName()));
      }
   }
   
   @Subscribe
   public void onDisplayClose(DisplayClosingEvent e) {
      display_.unregisterForEvents(this);
      display_ = null;

      ccpList_ = null;
      hcs_ = null;
      contrastPanel_ = null;
      dispSettings_ = null;
   }

   public void addContrastControls(int channelIndex, String channelName) {
      //TODO: bring back RGB if you want...
//      boolean rgb;
//      try {
//         rgb = display_.isRGB();
//      } catch (Exception ex) {
//         Log.log(ex);
//         rgb = false;
//      }
//      if (rgb) {
//         nChannels *= 3;
//      }

      Color color;
      try {
         color = dispSettings_.getColor(channelName);
      } catch (Exception ex) {
         ex.printStackTrace();
         color = Color.white;
      }
      int bitDepth = 16;
      try {
         bitDepth = dispSettings_.getBitDepth(channelName);
      } catch (Exception ex) {
         ex.printStackTrace();
         bitDepth = 16;
      }

      //create new channel control panels as needed
      ChannelControlPanel ccp = new ChannelControlPanel(channelIndex, this, display_, contrastPanel_, channelName, color, bitDepth);
      ccpList_.put(channelIndex, ccp);
      this.add(ccpList_.get(channelIndex));

      ((GridLayout) this.getLayout()).setRows(ccpList_.keySet().size());

      Dimension dim = new Dimension(ChannelControlPanel.MINIMUM_SIZE.width,
              ccpList_.keySet().size() * ChannelControlPanel.MINIMUM_SIZE.height);
      this.setMinimumSize(dim);
      this.setSize(dim);
      this.setPreferredSize(dim);
      //Dunno if this is even needed
      contrastPanel_.revalidate();
   }

   public void updateOtherDisplayCombos(int selectedIndex) {
      if (updatingCombos_) {
         return;
      }
      updatingCombos_ = true;
      for (int i = 0; i < ccpList_.size(); i++) {
         ccpList_.get(i).setDisplayComboIndex(selectedIndex);
      }
      updatingCombos_ = false;
   }

   public void setChannelDisplayModeFromFirst() {
      if (ccpList_ == null || ccpList_.size() <= 1) {
         return;
      }
      int displayIndex = ccpList_.get(0).getDisplayComboIndex();
      //automatically syncs other channels
      ccpList_.get(0).setDisplayComboIndex(displayIndex);
   }

   public void setChannelHistogramDisplayMax(int channelIndex, int histMax) {
      if (ccpList_ == null || ccpList_.size() <= channelIndex) {
         return;
      }
      int index = (int) (histMax == -1 ? 0 : Math.ceil(Math.log(histMax) / Math.log(2)) - 3);
      ccpList_.get(channelIndex).setDisplayComboIndex(index);
   }

   public void autoscaleAllChannels() {
      if (ccpList_ != null && ccpList_.size() > 0) {
         for (ChannelControlPanel c : ccpList_.values()) {
            c.autoButtonAction();
         }
      }
   }

   public void rejectOutliersChangeAction() {
      if (ccpList_ != null && ccpList_.size() > 0) {
         for (ChannelControlPanel c : ccpList_.values()) {
            c.autoButtonAction();
         }
      }
   }

   public int getNumberOfChannels() {
      return ccpList_.size();
   }

   void updateHistogramData(HashMap<Integer, int[]> hists, HashMap<Integer, Integer> mins, HashMap<Integer, Integer> maxs) {
      for (Integer i : hists.keySet()) {
         ccpList_.get(i).updateHistogram(hists.get(i), mins.get(i), maxs.get(i));             
      }
   }
}
