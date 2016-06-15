package org.eclipse.che.ide.flux.liveedit;


import com.google.gwt.core.shared.GWT;
import com.google.inject.Inject;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.editor.EditorPartPresenter;
import org.eclipse.che.ide.api.editor.text.Position;
import org.eclipse.che.ide.api.editor.document.Document;
import org.eclipse.che.ide.api.editor.events.CursorActivityEvent;
import org.eclipse.che.ide.api.editor.events.CursorActivityHandler;
import org.eclipse.che.ide.api.editor.text.TextPosition;

import org.eclipse.che.ide.api.editor.text.TextRange;
import org.eclipse.che.ide.api.editor.texteditor.CursorModelWithHandler;
import org.eclipse.che.ide.api.editor.texteditor.TextEditorPresenter;
import org.eclipse.che.ide.resource.Path;
import org.eclipse.che.ide.util.ListenerManager;
import org.eclipse.che.ide.util.ListenerManager.Dispatcher;
import org.eclipse.che.ide.util.ListenerRegistrar.Remover;

/**
 * {@link CursorModelWithHandler} implementation for the text editors.
 *
 * @author "Mickaël Leduque"
 */
class TextEditorCursorModel implements CursorModelWithHandler, CursorActivityHandler {

    private final Document document;
    private final ListenerManager<CursorHandler> cursorHandlerManager = ListenerManager.create();
    private final CursorHandlerForPairProgramming cursorHandlerForPairProgramming;
    private EditorAgent editorAgent;
    private Path path;
    private TextEditorPresenter textEditor;
    private EditorPartPresenter openedEditor;

    public TextEditorCursorModel(final Document document, CursorHandlerForPairProgramming cursorHandlerForPairProgramming, EditorAgent editorAgent) {
        this.document = document;
        this.document.addCursorHandler(this);
        this.cursorHandlerForPairProgramming = cursorHandlerForPairProgramming;
        this.editorAgent = editorAgent;
    }

    @Override
    public void setCursorPosition(int offset) {
        TextPosition position = document.getPositionFromIndex(offset);
        document.setCursorPosition(position);
    }

    @Override
    public Position getCursorPosition() {
        TextPosition position = document.getCursorPosition();
        int offset = document.getIndexFromPosition(position);
        return new Position(offset);
    }

    @Override
    public Remover addCursorHandler(CursorHandler handler) {
        return this.cursorHandlerManager.add(handler);
    }

    private void addMultiCursors() {
        final TextPosition position = this.document.getCursorPosition();
        final MultiCursorResources RESOURCES = GWT.create(MultiCursorResources.class);
        TextRange textRange = new TextRange(position, position);
        path = new Path(document.getFile().getPath());
        openedEditor = editorAgent.getOpenedEditor(path);
        if (openedEditor instanceof TextEditorPresenter){
            textEditor  = (TextEditorPresenter)openedEditor;
        }
        String annotationStyle = RESOURCES.getCSS().pairProgramminig();
        cursorHandlerForPairProgramming.setMarkerRegistration(textEditor.getHasTextMarkers().addMarker(textRange,annotationStyle));
    }

    @Override
    public void onCursorActivity(final CursorActivityEvent event) {
        addMultiCursors();
    }
}
