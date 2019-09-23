package org.micromanager.magellan.imagedisplaynew;

import org.micromanager.magellan.imagedisplaynew.events.ChannelAddedToDisplayEvent;
import org.micromanager.magellan.imagedisplaynew.events.ContrastUpdatedEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import ij.process.LUT;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import mmcorej.TaggedImage;

/**
 * This Class essentially replaces CompositeImage in ImageJ, and uses low level
 * classes to build a multicolor Image from pixels and contrast settings
 */
 class ImageMaker {

   public static final int EIGHTBIT = 0;
   public static final int SIXTEENBIT = 1;

   private final TreeMap<Integer, MagellanChannelDisplaySettings> channelDisplaySettings_ = new TreeMap<Integer, MagellanChannelDisplaySettings>();
   private final TreeMap<Integer, MagellanImageProcessor> channelProcessors_ = new TreeMap<Integer, MagellanImageProcessor>();

   private int imageWidth_, imageHeight_;
   private int[] rgbPixels_;
   private MagellanImageCache imageCache_;

   public ImageMaker(MagellanDisplayController c, MagellanImageCache data, int width, int height) {
      c.registerForEvents(this);
      //TODO
      imageWidth_ = 512;
      imageHeight_ = 512;
      rgbPixels_ = new int[imageWidth_ * imageHeight_];
      imageCache_ = data;
   }
   
   void close() {
      imageCache_ = null;
   }


   @Subscribe
   public void onChannelAddedToDisplay(ChannelAddedToDisplayEvent event) {
      channelDisplaySettings_.put(event.channelIndex, new MagellanChannelDisplaySettings());
      channelProcessors_.put(event.channelIndex, new MagellanImageProcessor(imageWidth_, imageHeight_));
   }

   @Subscribe
   public void onContrastUpdatedEvent(ContrastUpdatedEvent e) {
      //TODO: implement contrast
//      if (e.index != -1) {
//         //TODO: don't replace the object so you can do this from different threads
//         channelDisplaySettings_.put(e.index, e.channel);
//      } else if (e.displayMode != -1) {
//         displayMode_ = e.displayMode;
//      }
//      applyContrastSettings();
   }

   /**
    * Do neccesary calcualtion to get image for dipslay
    *
    * @return
    */
   public BufferedImage makeBufferedImage(MagellanDataViewCoords viewCoords) {
      //update pixels
      for (Integer c : viewCoords.channelIndices_) {
         TaggedImage imageForDisplay = imageCache_.getImageForDisplay(c, viewCoords);
         channelProcessors_.get(c).changePixels(imageForDisplay.pix);
      }
      
      //apply contrast settings
      for (Integer c : viewCoords.channelIndices_) {
         //only update one channel for speed
         LUT lut = makeLUT(channelDisplaySettings_.get(c).color, channelDisplaySettings_.get(c).gamma);
         channelProcessors_.get(c).setContrast(lut, channelDisplaySettings_.get(c).contrastMin, channelDisplaySettings_.get(c).contrastMax);
      }

      boolean firstActive = true;
      Arrays.fill(rgbPixels_, 0);
      int redValue, greenValue, blueValue;
      for (int c : viewCoords.channelIndices_) {
         if (channelDisplaySettings_.get(c).active) {
            //get the appropriate pixels from the data view

            //recompute 8 bit image
            channelProcessors_.get(c).recompute();
            byte[] bytes;
            bytes = channelProcessors_.get(c).eightBitImage;
            if (firstActive) {
               for (int p = 0; p < imageWidth_ * imageHeight_; p++) {
                  redValue = channelProcessors_.get(c).reds[bytes[p] & 0xff];
                  greenValue = channelProcessors_.get(c).greens[bytes[p] & 0xff];
                  blueValue = channelProcessors_.get(c).blues[bytes[p] & 0xff];
                  rgbPixels_[p] = redValue | greenValue | blueValue;
               }
               firstActive = false;
            } else {
               int pixel;
               for (int p = 0; p < imageWidth_ * imageHeight_; p++) {
                  pixel = rgbPixels_[p];
                  redValue = (pixel & 0x00ff0000) + channelProcessors_.get(p).reds[bytes[p] & 0xff];
                  greenValue = (pixel & 0x0000ff00) + channelProcessors_.get(p).greens[bytes[p] & 0xff];
                  blueValue = (pixel & 0x000000ff) + channelProcessors_.get(p).blues[bytes[p] & 0xff];
                  if (redValue > 16711680) {
                     redValue = 16711680;
                  }
                  if (greenValue > 65280) {
                     greenValue = 65280;
                  }
                  if (blueValue > 255) {
                     blueValue = 255;
                  }
                  rgbPixels_[p] = redValue | greenValue | blueValue;
               }
            }
         }

      }

//      Arrays.fill(rgbPixels_, 65535);
      DirectColorModel rgbCM = new DirectColorModel(24, 0xff0000, 0xff00, 0xff);
      WritableRaster wr = rgbCM.createCompatibleWritableRaster(1, 1);
      SampleModel sampleModel = wr.getSampleModel();
      sampleModel = sampleModel.createCompatibleSampleModel(imageWidth_, imageHeight_);
      DataBuffer dataBuffer = new DataBufferInt(rgbPixels_, imageWidth_ * imageHeight_, 0);
      WritableRaster rgbRaster = Raster.createWritableRaster(sampleModel, dataBuffer, null);
      return new BufferedImage(rgbCM, rgbRaster, false, null);
   }

   public static LUT makeLUT(Color color, double gamma) {
      int r = color.getRed();
      int g = color.getGreen();
      int b = color.getBlue();

      int size = 256;
      byte[] rs = new byte[size];
      byte[] gs = new byte[size];
      byte[] bs = new byte[size];

      double xn;
      double yn;
      for (int x = 0; x < size; ++x) {
         xn = x / (double) (size - 1);
         yn = Math.pow(xn, gamma);
         rs[x] = (byte) (yn * r);
         gs[x] = (byte) (yn * g);
         bs[x] = (byte) (yn * b);
      }
      return new LUT(8, size, rs, gs, bs);
   }

   private class MagellanImageProcessor {

      LUT lut;
      int min, max;
      Object pixels;
      int width, height;
      byte[] eightBitImage = null;
      int[] reds = null;
      int[] blues = null;
      int[] greens = null;

      public MagellanImageProcessor(int w, int h) {
         width = w;
         height = h;
      }

      public void changePixels(Object pix) {
         pixels = pix;
      }

      public void recompute() {
         create8BitImage();
      }

      public void setContrast(LUT l, int minn, int maxx) {
//         boolean reset = min != minn || max != maxx;
         //reset 8 bit monochrome image if min and max xhange
//         if (reset) {
//            create8BitImage();
//         }
         //reset LUT    
         min = minn;
         max = maxx;
         lut = l;
         splitLUTRGB();
      }

      /**
       * split LUT in RGB for fast lookup
       */
      private void splitLUTRGB() {
         IndexColorModel icm = (IndexColorModel) lut;
         int mapSize = icm.getMapSize();
         if (reds == null || reds.length != mapSize) {
            reds = new int[mapSize];
            greens = new int[mapSize];
            blues = new int[mapSize];
         }
         byte[] tmp = new byte[mapSize];
         icm.getReds(tmp);
         for (int i = 0; i < mapSize; i++) {
            reds[i] = (tmp[i] & 0xff) << 16;
         }
         icm.getGreens(tmp);
         for (int i = 0; i < mapSize; i++) {
            greens[i] = (tmp[i] & 0xff) << 8;
         }
         icm.getBlues(tmp);
         for (int i = 0; i < mapSize; i++) {
            blues[i] = tmp[i] & 0xff;
         }
      }

      //Create grayscale image with LUT min and max applied, but no color mapping
      private void create8BitImage() {
         int size = width * height;
         if (eightBitImage == null) {
            eightBitImage = new byte[size];
         }
         int value;
         double scale = 256.0 / (max - min + 1);
         for (int i = 0; i < size; i++) {
            if (pixels instanceof short[]) {
               value = (((short[]) pixels)[i] & 0xffff) - min;
            } else {
               value = (((byte[]) pixels)[i] & 0xffff) - min;
            }
            if (value < 0) {
               value = 0;
            }
            value = (int) (value * scale + 0.5);
            if (value > 255) {
               value = 255;
            }
            eightBitImage[i] = (byte) value;
         }
      }

   }

}
