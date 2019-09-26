package org.micromanager.magellan.imagedisplaynew;

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import org.micromanager.magellan.imagedisplaynew.events.CanvasResizeEvent;
import org.micromanager.magellan.imagedisplaynew.events.DisplayClosingEvent;

class MagellanCanvas {

   
   private volatile Image currentImage_;
   private int width_, height_;
   private double scale_;
   private MagellanDisplayController display_;
   private JPanel canvas_;

   public MagellanCanvas(MagellanDisplayController display) {
      canvas_ = createCanvas();
      //TODO: update size on 
      display.registerForEvents(this);
      display_ = display;

      //For recreating/resizing compositie image on window size change
      canvas_.addComponentListener(new ComponentAdapter() {
         @Override
         public void componentResized(ComponentEvent e) {
            display_.postEvent(new CanvasResizeEvent(canvas_.getWidth(), canvas_.getHeight()));
         }
      });
   }

   @Subscribe
   public void onDisplayClose(DisplayClosingEvent e) {
      for (ComponentListener l : canvas_.getComponentListeners()) {
         canvas_.removeComponentListener(l);
      }
      canvas_ = null;
      display_.unregisterForEvents(this);
      display_ = null;
   }

//   private void computeScale() {
//      if (currentImage_ == null) {
//         return;
//      }
//      double wScale = width_ / (double) currentImage_.getWidth();
//      double hScale = height_ / (double) currentImage_.getHeight();
//      //do the bigger scaling so image fills the whole canvas
//      scale_ = Math.max(wScale, hScale);
//   }

   /**
    * Set the size of the image displayed on screen, which is not neccesarily
    * the same as the image pixels read to create it
    *
    * @param w
    * @param h
    */
   @Subscribe
   public void onCanvasResize(final CanvasResizeEvent e) {
      width_ = e.w;
      height_ = e.h;
//      computeScale();
   }

   void updateDisplayImage(Image img, double scale) {
      currentImage_ = img;
      scale_ = scale;
   }

   public JPanel getCanvas() {
      return canvas_;
   }

   private JPanel createCanvas() {
      return new JPanel() {
         @Override
         public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
//            double scale = canvas_.getWidth() / (double) currentImage_.getWidth(canvas_);
            AffineTransform af = new AffineTransform(scale_, 0, 0, scale_, 0, 0);
            g2.drawImage(currentImage_, af, canvas_);
//            g2.drawImage(currentImage_, 0, 0, canvas_);
         
         }

         public void update(Graphics g) {
            paint(g);
         }
      };
   }

}
