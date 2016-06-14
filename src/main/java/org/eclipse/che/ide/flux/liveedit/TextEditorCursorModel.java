package org.eclipse.che.ide.flux.liveedit;


import org.eclipse.che.ide.api.editor.text.Position;
import org.eclipse.che.ide.api.editor.document.Document;
import org.eclipse.che.ide.api.editor.events.CursorActivityEvent;
import org.eclipse.che.ide.api.editor.events.CursorActivityHandler;
import org.eclipse.che.ide.api.editor.text.TextPosition;

import org.eclipse.che.ide.api.editor.texteditor.CursorModelWithHandler;
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

    public TextEditorCursorModel(final Document document) {
        this.document = document;
        this.document.addCursorHandler(this);
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

    private void dispatchCursorChange(final boolean isExplicitChange) {
        final TextPosition position = this.document.getCursorPosition();


        cursorHandlerManager.dispatch(new Dispatcher<CursorModelWithHandler.CursorHandler>() {
            @Override
            public void dispatch(CursorHandler listener) {
                listener.onCursorChange(position.getLine(), position.getCharacter(), isExplicitChange);
            }
        });
    }

    @Override
    public void onCursorActivity(final CursorActivityEvent event) {
        dispatchCursorChange(true);
    }
}
