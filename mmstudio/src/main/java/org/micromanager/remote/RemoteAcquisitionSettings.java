/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.remote;

import org.micromanager.acqj.internal.acqengj.MinimalAcquisitionSettings;

/**
 *
 * @author henrypinkard
 */
public class RemoteAcquisitionSettings extends MinimalAcquisitionSettings {
   
   boolean showViewer;
   String dataLocation;
   String name;
   
    public RemoteAcquisitionSettings() {
      
   }   
}
