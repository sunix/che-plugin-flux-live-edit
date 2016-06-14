package org.eclipse.che.ide.flux.liveedit;

import org.eclipse.che.ide.api.editor.texteditor.HasTextMarkers;


public class CursorHandlerForPairProgramming {
    String user;
    HasTextMarkers.MarkerRegistration markerRegistration;

    protected void setMarkerRegistration(HasTextMarkers.MarkerRegistration markerRegistration){
        this.markerRegistration = markerRegistration;
    }

    protected void setUser(String user){
        this.user = user;
    }

    protected String getUser(){
        return  this.user;
    }

    protected HasTextMarkers.MarkerRegistration getMarkerRegistration(){
        return this.markerRegistration;
    }

    protected void clearMark(){
        this.markerRegistration.clearMark();
    }
}
