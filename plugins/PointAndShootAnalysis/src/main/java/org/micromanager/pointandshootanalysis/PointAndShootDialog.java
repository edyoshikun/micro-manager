///////////////////////////////////////////////////////////////////////////////
//FILE:          PointAndShootDialog.java
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     PointAndShoot plugin
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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.MMDialog;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 *
 * @author nico
 */
public class PointAndShootDialog extends MMDialog {
   
   private final static String LOCATIONSFILENAME = "LocationFileName";
   private final String[] FILESUFFIXES = {"txt", "tproj", ""};
   
   private final Studio studio_;
   private final MutablePropertyMapView profileSettings_;
   private boolean wasDisposed_;
   
   
   PointAndShootDialog(Studio studio) {
      studio_ = studio;
      profileSettings_ = 
              studio_.profile().getSettings(PointAndShootDialog.class);
      wasDisposed_ = false;
      final MMDialog ourDialog = this;
      
      super.setTitle("Point and Shoot Analysis");
      super.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      super.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            dispose();
         }
      }
      );
      final Font arialSmallFont = new Font("Arial", Font.PLAIN, 12);
      final Dimension buttonSize = new Dimension(70, 21);
      
      super.setLayout(new MigLayout());
      super.loadAndRestorePosition(100, 100);
      
      JLabel explanationLabel = new JLabel("Provide location of text file " +
             "containing timestamps and locations of Point and Shoot of the " +
              "open data set");
      super.add(explanationLabel, "span 2, wrap");
      
      final JTextField locationsField = new JTextField(50);
      locationsField.setFont(arialSmallFont);
      locationsField.setText(profileSettings_.getString(LOCATIONSFILENAME,
               profileSettings_.getString(LOCATIONSFILENAME, "")));
      locationsField.setHorizontalAlignment(JTextField.LEFT);
      super.add(locationsField);

      final JButton locationsFieldButton =  mcsButton(buttonSize, arialSmallFont);
      locationsFieldButton.setText("...");
      locationsFieldButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            File f = FileDialogs.openFile(ourDialog, "Locations File",
                    new FileDialogs.FileType("MMProjector", "Locations File",
                            locationsField.getText(), true, FILESUFFIXES));
            if (f != null) {
               locationsField.setText(f.getAbsolutePath());
            }
         }
      });
      super.add(locationsFieldButton, "wrap");
      
      JButton cancelButton = mcsButton(buttonSize, arialSmallFont);
      cancelButton.setText("Cancel");
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            ourDialog.dispose();
         }
      });
      super.add(cancelButton, "span2, split2, tag cancel");
      
      JButton okButton = mcsButton(buttonSize, arialSmallFont);
      okButton.setText("OK");
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            // TODO: start analysis
            String fileName = locationsField.getText();
            File f = new File(fileName);
            if (!f.exists()) {
               studio_.logs().showError("File " + f.getName() + " does not exist");
               return;
            }
            if (!f.canRead()) {
               studio_.logs().showError("File " + f.getName() + " is not readable");
               return;
            }
            profileSettings_.putString(LOCATIONSFILENAME, fileName);
            Thread analysisThread = new Thread(new PointAndShootAnalyzer(studio, fileName));
            analysisThread.start();
            ourDialog.dispose();
         }
      });
      super.add(okButton, "tag ok, wrap");
      
      DragDropListener dragDropListener = new DragDropListener(locationsField);
      new DropTarget(this, dragDropListener);
      new DropTarget(locationsField, dragDropListener);
      
      super.pack();
      super.setVisible(true);
      
   }
   
   boolean wasDisposed() { return wasDisposed_; }
   
   @Override
   public void dispose() {
      super.dispose();
      wasDisposed_ = true;
   }
   
   
   public final JButton mcsButton(Dimension buttonSize, Font font) {
      JButton button = new JButton();
      button.setPreferredSize(buttonSize);
      button.setMinimumSize(buttonSize);
      button.setFont(font);
      button.setMargin(new Insets(0, 0, 0, 0));
      
      return button;
   }
}
