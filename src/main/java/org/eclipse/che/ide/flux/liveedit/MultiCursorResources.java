package org.eclipse.che.ide.flux.liveedit;

import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;

public interface MultiCursorResources extends ClientBundle {

    @Source({"Multi-cursor.css", "org/eclipse/che/ide/api/ui/style.css"})
    CSS getCSS();

    interface CSS extends CssResource {

        String pairProgramminig();

    }
}
