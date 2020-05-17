/**
 * RTIntensitiesFrame.java
 * <p>
 * Based on ExampleFrame, shows RealTime intensity plots during live views or acquisitions.
 * <p>
 * Nico Stuurman, Carlos Mendioroz copyright UCSF, 2020
 * <p>
 * LICENSE: This file is distributed under the BSD license. License text is
 * included with the source distribution.
 * <p>
 * This file is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.
 * <p>
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */
package org.micromanager.plugins.rtintensities;

import com.google.common.eventbus.Subscribe;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.awt.List;
import java.awt.Paint;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.micromanager.Studio;
import org.micromanager.data.DataProviderHasNewImageEvent;
import org.micromanager.data.ImageOverwrittenEvent;
import org.micromanager.display.DisplayWindow;

import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import org.micromanager.internal.utils.WindowPositioning;

public class RTIntensitiesFrame extends JFrame {

   private final Studio studio_;
   private final SimpleDateFormat dateFormat_;
   private DisplayWindow window_ = null;
   private DataProvider dataProvider_;
   private JButton startButton_;
   private double lastElapsedTimeMs_ = 0.0;
   private Date firstImageDate_;
   // Ratio plot memory
   private boolean ratio_ = false;
   private double[] last_ = new double[100];
   // This is our local cache ...
   private int ROIs_ = 0;
   private Roi[] roi_ = new Roi[100];
   // of the manager kept ROIs
   private RoiManager manager_;
   private int imagesReceived_ = 0;
   static final long serialVersionUID = 1;
   // How many channels do we have ? do we plot ?
   private int channels_ = 0;
   private int plots_ = 0;
   private JLabel title;
   private XYSeriesCollection dataset;
   XYSeries[] data = new XYSeries[200];

   private static final String ABSOLUTE_FORMAT_STRING = "yyyy-MM-dd HH:mm:ss.SSS Z";

   public RTIntensitiesFrame(Studio studio) {
      super("Real time intensity GUI");
      studio_ = studio;
      dateFormat_ = new SimpleDateFormat(ABSOLUTE_FORMAT_STRING);
      super.setLocation(100, 100); // Default location
      WindowPositioning.setUpLocationMemory(this, RTIntensitiesFrame.class, "Main");

      super.setLayout(new MigLayout("fill, insets 2, gap 2, flowx"));

      // Tell user what to do, in case he does not know...
      title = new JLabel("                Prepare for instructions!            ");
      title.setFont(new Font("Arial", Font.BOLD, 14));
      super.add(title, "span, alignx center, wrap");

      // Shortcut to ROI Manager
      JButton managerButton_ = new JButton("ROI Manager");
      managerButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (manager_ == null) {
               manager_ = new RoiManager();
            }
            manager_.setVisible(true);
         }
      });
      super.add(managerButton_, "split, span");

      // Create a graph and start plotting, or tell user what's missing for so doing
      startButton_ = new JButton("Plot");
      startButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // Active window ?
            window_ = studio.displays().getCurrentWindow();
            if (window_ == null) {
               title.setText("You need an open window (Snap/Live).");
               return;
            }
            dataProvider_ = window_.getDataProvider();
            channels_ = dataProvider_.getAxisLength("channel");
            // At least one ROI defined ?
            if (manager_ == null) {
               title.setText("Please setup ROI(s).");
               return;
            }
            List labels = manager_.getList();
            Hashtable<String, Roi> table = (Hashtable<String, Roi>) manager_.getROIs();
            ROIs_ = labels.getItemCount();
            if (ROIs_ == 0) {
               title.setText("Please setup ROI(s).");
               return;
            }
            // Hard limit of 100 ROIs
            if (ROIs_ > 100) {
               ROIs_ = 100;
            }
            for (int i = 0; i < ROIs_; i++) {
               String label = labels.getItem(i);
               roi_[i] = table.get(label);
            }
         // Multiple channels ? Logic for 2 channels only ...
        	   if (channels_ > 1) {
               Object[] options = {"1", "1&2", "1/2"};
               int x = JOptionPane.showOptionDialog(null, "Please select:",
                       "Multi channel data options",
                       JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
               switch(x) {
               case 0:
               	plots_ = 1; 
               	break;
               case 1:
               	plots_ = 2; 
               	ratio_ = false;
               	break;
               case 2:
               	plots_ = 2;
               	ratio_ = true;
               	break;
               }
        	   } else {
        	   	plots_ = 1;
        	   }
            // We are all set ?
            imagesReceived_ = 0; // new plot
            lastElapsedTimeMs_ = 0;
            dataProvider_.registerForEvents(RTIntensitiesFrame.this);
            dataset = new XYSeriesCollection();
           	for (int i = 0; i < ROIs_; i++) {
               for (int c = 0; c < channels_; c++) {
            		data[i*channels_+c] = new XYSeries("" + (i + 1) + 
            				((channels_>1) ? ("/" + ( c + 1)) : ""));
            		dataset.addSeries(data[i*channels_+c]);
            	}
            }
            plotData("Intensities " + dataProvider_.getName(), dataset, "Time(ms)", "Value", channels_, 100, 100);
         }
      });
      startButton_.setEnabled(true);
      super.add(startButton_, "wrap");

      super.pack();

      // Registering this class for events means that its event handlers
      // (that is, methods with the @Subscribe annotation) will be invoked when
      // an event occurs. You need to call the right registerForEvents() method
      // to get events; this one is for the application-wide event bus, but
      // there's also Datastore.registerForEvents() for events specific to one
      // Datastore, and DisplayWindow.registerForEvents() for events specific
      // to one image display window.
      studio_.events().registerForEvents(this);
      title.setText("You need an active window and a ROI ...");
   }

   @Subscribe
   public void onNewImage(DataProviderHasNewImageEvent event) {
      processImage(event.getDataProvider(), event.getImage());
   }

   @Subscribe
   public void onOverWrittenImage(ImageOverwrittenEvent event) {
      processImage(event.getDatastore(), event.getNewImage());
   }

   private void processImage(DataProvider dp, Image image) {
      if (!dp.equals(dataProvider_)) {
         return;
      }
      Date imgTime;
      try {
         imgTime = dateFormat_.parse(image.getMetadata().getReceivedTime());
      } catch (ParseException pe) {
         studio_.logs().logError(pe);
         return;
      }
      if (imagesReceived_ == 0) {
         firstImageDate_ = imgTime;
         lastElapsedTimeMs_ = 0.0;
      }
      double elapsedTimeMs = imgTime.getTime() - firstImageDate_.getTime();
      // do not process images at more than 100 Hz
      if (elapsedTimeMs == lastElapsedTimeMs_ || elapsedTimeMs - lastElapsedTimeMs_ >= 10.0) {
         lastElapsedTimeMs_ = elapsedTimeMs;
         double v;
         int channel = image.getCoords().getChannel(); // 0..1
         if (channel > plots_) {
         	return;
         }
         title.setText("You should be seeing data on the plot.");

         ImageProcessor processor = studio_.data().ij().createProcessor(image);

		   if (plots_ > 1 && ratio_ && channel == 0) {
	   		//Remember values
			   for (int i = 0; i < ROIs_; i++) {
				   processor.setRoi(roi_[i]);
				   last_[i] = processor.getStats().mean;
			   }
	   	} else {
	   		int offset = 0;
	   		if (plots_ > 1 && channel == 1) {
	   			 offset = 1; // Paired plots
	   		}
	   		for (int i = 0; i < ROIs_; i++) {
	   			processor.setRoi(roi_[i]);
	   			v = processor.getStats().mean;
	   			if (ratio_) {
	   					v = last_[i] / (v + 0.000001); //Check!
	   			}
	   			data[offset + i * plots_].add(elapsedTimeMs, v, (i < ROIs_ - 1)?false:true);
	   		}
	   	}
      }
      imagesReceived_++;
   }

   /**
    * Create a frame with a plot of the data given in XYSeries
    * @param title Title of the plot
    * @param dataset Data to be plotted
    * @param xTitle Title of the x axis
    * @param yTitle Title of the y axis
    * @param xLocation Window location on the screen (x)
    * @param yLocation Window Location on the screen (y)
    */
   public static void plotData(String title, XYSeriesCollection dataset, String xTitle,
                               String yTitle, int channels, int xLocation, int yLocation) {
      // JFreeChart code
      JFreeChart chart = ChartFactory.createScatterPlot(title, // Title
              xTitle, // x-axis Label
              yTitle, // y-axis Label
              dataset, // Dataset
              PlotOrientation.VERTICAL, // Plot Orientation
              true, // Show Legend
              true, // Use tooltips
              false // Configure chart to generate URLs?
      );
      XYPlot plot = (XYPlot) chart.getPlot();
      int series = dataset.getSeriesCount();
      plot.setBackgroundPaint(Color.white);
      plot.setRangeGridlinePaint(Color.lightGray);
      XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
      renderer.setDefaultShapesVisible(true);
      Paint paint = plot.getDrawingSupplier().getNextPaint();
      renderer.setSeriesPaint(0, paint);
      renderer.setSeriesFillPaint(0, Color.white);
      renderer.setSeriesLinesVisible(0, true);
      Shape circle = new Ellipse2D.Float(-2.0f, -2.0f, 4.0f, 4.0f);
      Shape square = new Rectangle2D.Float(-2.0f, -2.0f, 4.0f, 4.0f);
      renderer.setSeriesShape(0, circle, false);
      renderer.setUseFillPaint(true);
      if (channels > 1) {
      	renderer.setSeriesShape(1, square, false);
      	renderer.setSeriesLinesVisible(1, true);
      	renderer.setSeriesPaint(1, paint);
      }
      for(int i=channels;i<series;i++){
      	paint = plot.getDrawingSupplier().getNextPaint();
         renderer.setSeriesShape(i, circle, false);
         renderer.setSeriesLinesVisible(i, true);
         renderer.setSeriesPaint(i, paint);
         if (channels > 1) {
         	renderer.setSeriesShape(++i, square, false);
         	renderer.setSeriesLinesVisible(i, true);
         	renderer.setSeriesPaint(i, paint);
         }
      }

      ChartFrame graphFrame = new ChartFrame(title, chart);
      graphFrame.getChartPanel().setMouseWheelEnabled(true);
      graphFrame.pack();
      graphFrame.setLocation(xLocation, yLocation);
      graphFrame.setSize(400, 300);
      WindowPositioning.setUpBoundsMemory(graphFrame, RTIntensities.class, "plot");
      WindowPositioning.cascade(graphFrame, RTIntensities.class);
      graphFrame.setVisible(true);
   }

}