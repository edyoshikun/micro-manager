/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acqj.internal.acqengj;

/**
 * Convenience class that encapsulates a single channel setting
 * 
 * @author henrypinkard
 */
public class ChannelSetting {
   
   public final String group_, config_;
   public final double exposure_, offset_;
   
   public ChannelSetting(String group, String config, 
           double exposure, double offset) {
      group_ = group;
      config_ = config;
      exposure_ = exposure;
      offset_ = offset;
   }
      
}
