/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.imagedisplay;

import ij.ImagePlus;
import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONObject;
import org.micromanager.magellan.imagedisplaynew.MagellanImageCache;

/**
 *
 * @author henrypinkard
 */
public class MetadataPanel extends javax.swing.JPanel {

   private final MetadataTableModel imageMetadataModel_;
   private final MetadataTableModel summaryMetadataModel_;
   private volatile Timer updateTimer_;
   private JSONObject summaryMetadata_;

   /**
    * Creates new form MetadataPanelNew
    */
   public MetadataPanel() {
      imageMetadataModel_ = new MetadataTableModel();
      summaryMetadataModel_ = new MetadataTableModel();
      initComponents();
   }

   public void prepareForClose() {
      if (updateTimer_ != null) {
         updateTimer_.cancel();
      }
      updateTimer_ = null;
   }

   public void setSummaryMetadata(JSONObject metadata) {
      summaryMetadata_ = metadata;
      summaryMetadataModel_.setMetadata(summaryMetadata_);
   }

   public void updateImageMetadata(JSONObject md) {
      imageMetadataModel_.setMetadata(md);
   }

   /**
    * This method is called from within the constructor to initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is always
    * regenerated by the Form Editor.
    */
   @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      acqImageTabbedPane_ = new javax.swing.JTabbedPane();
      summaryMDPanel_ = new javax.swing.JPanel();
      jScrollPane2 = new javax.swing.JScrollPane();
      jTable1 = new javax.swing.JTable();
      imageMDPanel_ = new javax.swing.JPanel();
      jScrollPane1 = new javax.swing.JScrollPane();
      jTable2 = new javax.swing.JTable();

      jTable1.setModel(summaryMetadataModel_);
      jScrollPane2.setViewportView(jTable1);

      javax.swing.GroupLayout summaryMDPanel_Layout = new javax.swing.GroupLayout(summaryMDPanel_);
      summaryMDPanel_.setLayout(summaryMDPanel_Layout);
      summaryMDPanel_Layout.setHorizontalGroup(
         summaryMDPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 373, Short.MAX_VALUE)
      );
      summaryMDPanel_Layout.setVerticalGroup(
         summaryMDPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 283, Short.MAX_VALUE)
      );

      acqImageTabbedPane_.addTab("Acquisition summary metadata", summaryMDPanel_);

      jTable2.setModel(imageMetadataModel_);
      jScrollPane1.setViewportView(jTable2);

      javax.swing.GroupLayout imageMDPanel_Layout = new javax.swing.GroupLayout(imageMDPanel_);
      imageMDPanel_.setLayout(imageMDPanel_Layout);
      imageMDPanel_Layout.setHorizontalGroup(
         imageMDPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 373, Short.MAX_VALUE)
      );
      imageMDPanel_Layout.setVerticalGroup(
         imageMDPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 283, Short.MAX_VALUE)
      );

      acqImageTabbedPane_.addTab("Image metadata", imageMDPanel_);

      javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
      this.setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
            .addComponent(acqImageTabbedPane_)
            .addContainerGap())
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addComponent(acqImageTabbedPane_)
      );

      acqImageTabbedPane_.getAccessibleContext().setAccessibleName("");
   }// </editor-fold>//GEN-END:initComponents


   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JTabbedPane acqImageTabbedPane_;
   private javax.swing.JPanel imageMDPanel_;
   private javax.swing.JScrollPane jScrollPane1;
   private javax.swing.JScrollPane jScrollPane2;
   private javax.swing.JTable jTable1;
   private javax.swing.JTable jTable2;
   private javax.swing.JPanel summaryMDPanel_;
   // End of variables declaration//GEN-END:variables
}
