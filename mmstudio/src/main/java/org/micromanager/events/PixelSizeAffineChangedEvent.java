///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Events API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, 2019
//
// COPYRIGHT:    University of California, San Francisco, 2019
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

package org.micromanager.events;

import java.awt.geom.AffineTransform;

public class PixelSizeAffineChangedEvent {
   private final AffineTransform affine_;
   
   public PixelSizeAffineChangedEvent(AffineTransform affine) {
      affine_ = affine;
   }
   
   public AffineTransform getNewPixelSizeAffine() {
      return affine_;
   }
}