///////////////////////////////////////////////////////////////////////////////
//FILE:          ContrastPanel.java
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
package org.micromanager.magellan.imagedisplaynew;

import com.google.common.eventbus.Subscribe;
import ij.CompositeImage;
import java.awt.BorderLayout;
import org.micromanager.magellan.imagedisplay.DisplayOverlayer;
import org.micromanager.magellan.imagedisplay.MMScaleBar;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.util.HashMap;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.micromanager.magellan.imagedisplaynew.MagellanDisplayController;
import org.micromanager.magellan.imagedisplaynew.events.ContrastUpdatedEvent;
import org.micromanager.magellan.imagedisplaynew.events.DisplayClosingEvent;
import org.micromanager.magellan.main.Magellan;
import org.micromanager.magellan.misc.Log;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 *
 * @author Henry Pinkard This class is a singleton instance of the component in
 * the contrast tab of the metadata panel. It has a single instance of the
 * controls on top, and changes which histograms are displayed based on the
 * frontmost window
 */
class ContrastPanel extends JPanel {

   private static final String PREF_AUTOSTRETCH = "stretch_contrast";
   private static final String PREF_REJECT_OUTLIERS = "reject_outliers";
   private static final String PREF_REJECT_FRACTION = "reject_fraction";
   private static final String PREF_LOG_HIST = "log_hist";
   private static final String PREF_SYNC_CHANNELS = "sync_channels";
   private static final String PREF_SLOW_HIST = "slow_hist";
   protected JScrollPane histDisplayScrollPane_;
   private JCheckBox compositeCheckBox_; //TODO use this to change between composite and channel modes
   private JCheckBox autostretchCheckBox_;
   private JCheckBox rejectOutliersCheckBox_;
   private JSpinner rejectPercentSpinner_;
   private JCheckBox logHistCheckBox_;
   private JCheckBox syncChannelsCheckBox_;
   private MutablePropertyMapView prefs_;
   protected MultiChannelHistograms histograms_;
   private HistogramControlsState histControlsState_;
   //volatile because accessed by overlayer creation thread
   private MagellanDisplayController display_;
   private JPanel contentPanel_;

   public ContrastPanel(MagellanDisplayController display) {
      //TODO: this isnt right is it?

      histograms_ = new MultiChannelHistograms(display, this);
      display_ = display;
      display_.registerForEvents(this);
      contentPanel_ = createGUI();
      this.setLayout(new BorderLayout());
      this.add(contentPanel_, BorderLayout.CENTER);
      prefs_ = Magellan.getStudio().profile().getSettings(ContrastPanel.class);
      histControlsState_ = createDefaultControlsState();
      initializeHistogramDisplayArea();
      showCurrentHistograms();
   }

   @Subscribe
   public void onDisplayClose(DisplayClosingEvent e) {
      display_.unregisterForEvents(this);
      display_ = null;
      this.remove(contentPanel_);
      contentPanel_ = null;
      histograms_ = null;
      histControlsState_ = null;
   }

   public void addContrastControls(int channelIndex, String channelName) {
      histograms_.addContrastControls(channelIndex, channelName);
   }

   public HistogramControlsState getHistogramControlsState() {
      return histControlsState_;
   }

   private void initializeHistogramDisplayArea() {
      histDisplayScrollPane_.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      histDisplayScrollPane_.getVerticalScrollBar().setUnitIncrement(8);
      showCurrentHistograms();
      configureControls();
   }

   private void showCurrentHistograms() {
      histDisplayScrollPane_.setViewportView(
              histograms_ != null ? (JPanel) histograms_ : new JPanel());
      if (histograms_ != null) {
         histDisplayScrollPane_.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
      } else {
         histDisplayScrollPane_.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
      }
      this.repaint();
   }

   public HistogramControlsState createDefaultControlsState() {
      HistogramControlsState state = new HistogramControlsState();
      state.autostretch = prefs_.getBoolean(PREF_AUTOSTRETCH, true);
      state.percentToIgnore = prefs_.getDouble(PREF_REJECT_FRACTION, 2);
      state.logHist = prefs_.getBoolean(PREF_LOG_HIST, false);
      state.ignoreOutliers = prefs_.getBoolean(PREF_REJECT_OUTLIERS, false);
      state.syncChannels = prefs_.getBoolean(PREF_SYNC_CHANNELS, false);
      return state;
   }

   private void configureControls() {
      loadControlsStates();
      logHistCheckBox_.setEnabled(true);
      syncChannelsCheckBox_.setEnabled(true);
   }

   private void loadControlsStates() {

      logHistCheckBox_.setSelected(histControlsState_.logHist);
      rejectPercentSpinner_.setValue(histControlsState_.percentToIgnore);
      autostretchCheckBox_.setSelected(histControlsState_.autostretch);
      compositeCheckBox_.setSelected(histControlsState_.composite);
      rejectOutliersCheckBox_.setSelected(histControlsState_.ignoreOutliers);
      syncChannelsCheckBox_.setSelected(histControlsState_.syncChannels);


   }

   private void saveCheckBoxStates() {
      prefs_.putBoolean(PREF_AUTOSTRETCH, autostretchCheckBox_.isSelected());
      prefs_.putBoolean(PREF_LOG_HIST, logHistCheckBox_.isSelected());
      prefs_.putDouble(PREF_REJECT_FRACTION, (Double) rejectPercentSpinner_.getValue());
      prefs_.putBoolean(PREF_REJECT_OUTLIERS, rejectOutliersCheckBox_.isSelected());
      prefs_.putBoolean(PREF_SYNC_CHANNELS, syncChannelsCheckBox_.isSelected());

      if (display_ == null) {
         return;
      }
      histControlsState_.autostretch = autostretchCheckBox_.isSelected();
      histControlsState_.ignoreOutliers = rejectOutliersCheckBox_.isSelected();
      histControlsState_.logHist = logHistCheckBox_.isSelected();
      histControlsState_.percentToIgnore = (Double) rejectPercentSpinner_.getValue();
      histControlsState_.syncChannels = syncChannelsCheckBox_.isSelected();
   }

   private JPanel createGUI() {
      JPanel controlPanel = new JPanel();
      compositeCheckBox_ = new JCheckBox("Show all");
      autostretchCheckBox_ = new JCheckBox();
      rejectOutliersCheckBox_ = new JCheckBox();
      rejectPercentSpinner_ = new JSpinner();
      logHistCheckBox_ = new JCheckBox();

      histDisplayScrollPane_ = new JScrollPane();

      this.setPreferredSize(new Dimension(400, 594));

      autostretchCheckBox_.setText("Autostretch");
      autostretchCheckBox_.addChangeListener(new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent evt) {
            autostretchCheckBoxStateChanged();
         }
      });
      rejectOutliersCheckBox_.setText("ignore %");
      rejectOutliersCheckBox_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            rejectOutliersCheckBoxAction();
         }
      });

      rejectPercentSpinner_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      rejectPercentSpinner_.addChangeListener(new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent evt) {
            rejectPercentageChanged();
         }
      });
      rejectPercentSpinner_.addKeyListener(new java.awt.event.KeyAdapter() {

         @Override
         public void keyPressed(java.awt.event.KeyEvent evt) {
            rejectPercentageChanged();
         }
      });
      rejectPercentSpinner_.setModel(new SpinnerNumberModel(0.02, 0., 100., 0.1));

      logHistCheckBox_.setText("Log hist");
      logHistCheckBox_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            logScaleCheckBoxActionPerformed();
         }
      });

      syncChannelsCheckBox_ = new JCheckBox("Sync channels");
      syncChannelsCheckBox_.addChangeListener(new ChangeListener() {

         @Override
         public void stateChanged(ChangeEvent e) {
            syncChannelsCheckboxAction();
         }
      });
      JPanel outerPanel = new JPanel(new BorderLayout());
      
      controlPanel.setLayout(new FlowLayout(FlowLayout.LEADING));
      controlPanel.add(compositeCheckBox_);
      controlPanel.add(autostretchCheckBox_);
      controlPanel.add(syncChannelsCheckBox_);
      controlPanel.add(rejectOutliersCheckBox_);
      controlPanel.add(rejectPercentSpinner_);
      controlPanel.add(logHistCheckBox_);
      
      outerPanel.add(controlPanel, BorderLayout.PAGE_START);
      outerPanel.add(histDisplayScrollPane_, BorderLayout.CENTER);
      
      return outerPanel;
   }

   private void syncChannelsCheckboxAction() {
      if (!syncChannelsCheckBox_.isEnabled()) {
         return;
      }
      boolean synced = syncChannelsCheckBox_.isSelected();
      if (synced) {
         autostretchCheckBox_.setSelected(false);
         autostretchCheckBox_.setEnabled(false);
         if (histograms_ != null) {
            ((MultiChannelHistograms) histograms_).setChannelContrastFromFirst();
            ((MultiChannelHistograms) histograms_).setChannelDisplayModeFromFirst();
         }
      } else {
         autostretchCheckBox_.setEnabled(true);
      }
      saveCheckBoxStates();
   }

   private void autostretchCheckBoxStateChanged() {
      rejectOutliersCheckBox_.setEnabled(autostretchCheckBox_.isSelected());
      boolean rejectem = rejectOutliersCheckBox_.isSelected() && autostretchCheckBox_.isSelected();
      rejectPercentSpinner_.setEnabled(rejectem);
      saveCheckBoxStates();
      if (autostretchCheckBox_.isSelected()) {
         if (histograms_ != null) {
            histograms_.autoscaleAllChannels();
         }
      } else {
         rejectOutliersCheckBox_.setSelected(false);
      }
   }

   private void rejectOutliersCheckBoxAction() {
      saveCheckBoxStates();
      rejectPercentSpinner_.setEnabled(rejectOutliersCheckBox_.isSelected());
      if (histograms_ != null) {
         histograms_.rejectOutliersChangeAction();
      }
   }

   private void rejectPercentageChanged() {
      saveCheckBoxStates();
      if (histograms_ != null) {
         histograms_.rejectOutliersChangeAction();
      }
   }

   private void logScaleCheckBoxActionPerformed() {
      display_.postEvent(new ContrastUpdatedEvent(-1));
      saveCheckBoxStates();
   }

   public void autostretch() {
      if (histograms_ != null) {
         histograms_.autostretch();
      }
   }

   public void disableAutostretch() {
      autostretchCheckBox_.setSelected(false);
      saveCheckBoxStates();
   }

   void updateHistogramData(HashMap<Integer, int[]> hists) {
      histograms_.updateHistogramData(hists);
   }
}
