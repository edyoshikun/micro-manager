
package org.micromanager.pointandshootanalysis.algorithm;

import georegression.struct.line.LineSegment2D_I32;
import georegression.struct.point.Point2D_I32;
import georegression.struct.shapes.Rectangle2D_I32;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author NicoLocal
 */
public class ContourStats {
   
   /**
    * Finds the centroid of a given list of points
    * 
    * @param input List of points used to find the centroid
    * @return centroid
    */
   public static Point2D_I32 centroid(List<Point2D_I32> input) {
      Point2D_I32 c = new Point2D_I32();
         for (Point2D_I32 p : input) {
            c.x += p.x;
            c.y += p.y;
         }
      c.x = Math.round(c.x / input.size());
      c.y = Math.round(c.y / input.size());
      
      return c;
   }
   
   /**
    * Finds a rectangle enclosing the given list of points
    * 
    * @param input List of points to look through for bounding box
    * @return Rectangle that encloses the input list of points
    */
   public static Rectangle2D_I32 boundingBox(List<Point2D_I32> input) {
      int xMin = Integer.MAX_VALUE;
      int xMax = -1;
      int yMin = Integer.MAX_VALUE;
      int yMax = -1;
      for (Point2D_I32 p : input) {
         if (p.getX() < xMin) { xMin = p.getX(); }
         if (p.getY() < yMin) { yMin = p.getY(); }
         if (p.getX() > xMax) { xMax = p.getX(); }
         if (p.getY() > yMax) { yMax = p.getY(); }
      }
      
      return  new Rectangle2D_I32(xMin, xMax - xMin + 1, yMin, yMax - yMin + 1);
   }
   
   /**
    * Finds the nearest point in a list of points
    *
    * @param target POint that we want to match
    * @param source List of points to look through for near points
    * @return nearest point from the list (or null if source is empty)
   */
   public static Point2D_I32 nearestPoint(Point2D_I32 target, Collection<Point2D_I32> source) {
      Point2D_I32 cp = new Point2D_I32();
      double minDist = Double.MAX_VALUE;
      for (Point2D_I32 sourcePoint : source) {
         double newDistance = (new LineSegment2D_I32(target, sourcePoint)).getLength();
         if (newDistance < minDist) {
            minDist = newDistance;
            cp.x = sourcePoint.x;
            cp.y = sourcePoint.y;
         }
      }
      return cp;
   }
   
}
