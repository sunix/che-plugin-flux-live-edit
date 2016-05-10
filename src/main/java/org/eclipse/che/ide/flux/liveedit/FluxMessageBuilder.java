/*******************************************************************************
 * Copyright (c) 2016 Serli SAS.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Sun Seng David TAN <sunix@sunix.org> - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.flux.liveedit;

import org.eclipse.che.ide.jseditor.client.document.Document;
import org.eclipse.che.ide.jseditor.client.events.DocumentChangeEvent;
import org.eclipse.che.ide.socketio.Message;

import com.google.gwt.core.client.JsonUtils;

public class FluxMessageBuilder {
    private String fullPath;
    private String addedCharacters;
    private int    offset;
    private int    removeCharCount;
    private String project;
    private String resource;

    public FluxMessageBuilder with(Document document) {
        fullPath = document.getFile().getPath().substring(1);
        project = fullPath.substring(0, fullPath.indexOf('/'));
        resource = fullPath.substring(fullPath.indexOf('/') + 1);
        return this;
    }


    public FluxMessageBuilder with(DocumentChangeEvent event) {
        return this.with(event.getDocument().getDocument()) //
                   .withAddedCharacters(event.getText()) //
                   .withOffset(event.getOffset()) //
                   .withRemovedCharCount(event.getRemoveCharCount());
    }

    private FluxMessageBuilder withRemovedCharCount(int removeCharCount) {
        this.removeCharCount = removeCharCount;
        return this;
    }

    public FluxMessageBuilder withOffset(int offset) {
        this.offset = offset;
        return this;
    }

    public FluxMessageBuilder withAddedCharacters(String addedCharacters) {
        this.addedCharacters = JsonUtils.escapeValue(addedCharacters);
        return this;
    }

    public Message buildResourceRequestMessage() {
        String json = "{"//
                      + "\"username\":\"USER\","//
                      + "\"project\":\"" + project + "\","//
                      + "\"resource\":\"" + resource + "\"" //
                      + "}";

        return new Message().withType("getResourceRequest")//
                                .withJsonContent(JsonUtils.unsafeEval(json));
    }

    public Message buildLiveResourceChangeMessage() {
        String json = "{"//
                      + "\"username\":\"USER\","//
                      + "\"project\":\"" + project + "\","//
                      + "\"resource\":\"" + resource + "\"," //
                      + "\"offset\":" + offset + "," //
                      + "\"removedCharCount\":" + removeCharCount + "," //
                      + "\"addedCharacters\": " + addedCharacters //
                      + "}";
        return new Message().withType("liveResourceChanged")//
                                .withJsonContent(JsonUtils.unsafeEval(json));
    }

}
