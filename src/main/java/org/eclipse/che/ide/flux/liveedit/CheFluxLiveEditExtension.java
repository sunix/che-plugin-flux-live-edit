/*******************************************************************************
 * Copyright (c) 2015 Serli SAS.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Sun Seng David TAN <sunix@sunix.org> - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.flux.liveedit;


import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import org.eclipse.che.api.runner.dto.ApplicationProcessDescriptor;
import org.eclipse.che.ide.api.extension.Extension;
import org.eclipse.che.ide.ext.runner.client.models.Runner;
import org.eclipse.che.ide.ext.runner.client.runneractions.impl.launch.common.RunnerApplicationStatusEvent;
import org.eclipse.che.ide.ext.runner.client.runneractions.impl.launch.common.RunnerApplicationStatusEventHandler;
import org.eclipse.che.ide.jseditor.client.document.Document;
import org.eclipse.che.ide.jseditor.client.document.DocumentHandle;
import org.eclipse.che.ide.jseditor.client.events.DocumentChangeEvent;
import org.eclipse.che.ide.jseditor.client.events.DocumentChangeHandler;
import org.eclipse.che.ide.jseditor.client.events.DocumentReadyEvent;
import org.eclipse.che.ide.jseditor.client.events.DocumentReadyHandler;
import org.eclipse.che.ide.socketio.Consumer;
import org.eclipse.che.ide.socketio.SocketIOOverlay;
import org.eclipse.che.ide.socketio.SocketIOResources;
import org.eclipse.che.ide.socketio.SocketOverlay;
import org.eclipse.che.ide.util.loging.Log;

import com.google.gwt.core.client.JsonUtils;
import com.google.gwt.core.client.ScriptInjector;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

@Singleton
@Extension(title = "Che Flux extension", version = "1.0.0")
public class CheFluxLiveEditExtension {

    private Map<String, Document> liveDocuments   = new HashMap<String, Document>();

    private SocketOverlay         socket;

    private boolean               isUpdatingModel = false;


    @Inject
    public CheFluxLiveEditExtension(EventBus eventBus) {
        SocketIOResources ioresources = GWT.create(SocketIOResources.class);
        ScriptInjector.fromString(ioresources.socketIo().getText()).setWindow(ScriptInjector.TOP_WINDOW).inject();

        eventBus.addHandler(RunnerApplicationStatusEvent.TYPE, new RunnerApplicationStatusEventHandler() {
            @Override
            public void onRunnerStatusChanged(@Nonnull Runner runner) {
                ApplicationProcessDescriptor descriptor = runner.getDescriptor();
                if (descriptor == null) {
                    return;
                }
                String fluxPort = descriptor.getPortMapping().getPorts().get("3000");
                if (fluxPort == null) {
                    return;
                }
                String host = descriptor.getPortMapping().getHost();

                switch (descriptor.getStatus()) {
                    case RUNNING:
                        connectToFlux(descriptor, host, fluxPort);
                        break;
                    case FAILED:
                    case STOPPED:
                    case CANCELLED:
                        if (socket != null) {
                            // TODO implement socket disconnect
                            // socket.
                        }
                        break;
                    default:
                }
            }
        });


        eventBus.addHandler(DocumentReadyEvent.TYPE,
                            new DocumentReadyHandler() {
                                @Override
                                public void onDocumentReady(DocumentReadyEvent event) {
                                    Log.info(getClass(), "document ready !" + event.getDocument().getFile().getPath());
                                    liveDocuments.put(event.getDocument().getFile().getPath(), event.getDocument());

                                    final DocumentHandle documentHandle = event.getDocument().getDocumentHandle();

                                    documentHandle.getDocEventBus().addHandler(DocumentChangeEvent.TYPE, new DocumentChangeHandler() {
                                        @Override
                                        public void onDocumentChange(DocumentChangeEvent event) {
                                            if (socket != null) {
                                                // full path start with /, so substring
                                                String fullPath = event.getDocument().getDocument().getFile().getPath().substring(1);
                                                String project = fullPath.substring(0, fullPath.indexOf('/'));
                                                String resource = fullPath.substring(fullPath.indexOf('/') + 1);
                                                String text = JsonUtils.escapeValue(event.getText());
                                                Log.info(CheFluxLiveEditExtension.class, "text to emit " + text);
                                                String json = "{\"username\":\"USER\",\"project\":\"" + project + "\",\"resource\":\"" + resource + "\",\"offset\":" + event.getOffset() + ",\"removedCharCount\":" + event.getRemoveCharCount() + ",\"addedCharacters\":" + text + "}";
                                                Log.info(CheFluxLiveEditExtension.class, "emit liveResourceChanged if " + isUpdatingModel + " " + json);
                                                if (isUpdatingModel) {
                                                    return;
                                                }
                                                socket.emit("liveResourceChanged", JsonUtils.unsafeEval(json));
                                            }
                                        }
                                    });
                                }
                            });

    }

    protected void connectToFlux(ApplicationProcessDescriptor descriptor, final String host, final String fluxPort) {
        new Timer() {
            @Override
            public void run() {
                SocketIOOverlay io = getSocketIO();

                String url = "http://" + host + ":" + fluxPort;
                Log.info(getClass(), "connecting to " + url);

                socket = io.connect(url);

                new Timer() {
                    @Override
                    public void run() {

                        socket.emit("connectToChannel", JsonUtils.safeEval("{\"channel\" : \"USER\"}"));
                        // 5:1+::{"name":"connectToChannel","args":[{"channel":"USER"}]}


                        // TODO socket.emit("liveResourceStarted", JsonUtils.safeEval("{\"channel\" : \"USER\"}"));

                        socket.on("liveResourceChanged", new Consumer<FluxResourceChangedEventDataOverlay>() {
                            @Override
                            public void accept(FluxResourceChangedEventDataOverlay event) {
                                Log.info(getClass(), "live resource changed: " + event.getProject() + " resource: " + event.getResource() + " offset: " + event.getOffset() + " addedChars: " + event.getAddedCharacters() + " removed chars: " + event.getRemovedCharCount());

                                Document document = liveDocuments.get("/" + event.getProject() + "/" + event.getResource());
                                if (document == null) {
                                    Log.info(getClass(), "no live doc for " + event.getProject() + "/" + event.getResource());
                                    return;
                                }

                                // document.removeDocumentListener(documentListener);
                                isUpdatingModel = true;
                                document.replace(event.getOffset(), event.getRemovedCharCount(), event.getAddedCharacters());
                                // document.addDocumentListener(documentListener);
                                isUpdatingModel = false;
                            }

                        });

                    }
                }.schedule(1000);

            }
        }.schedule(5000);


    }

    public static native SocketIOOverlay getSocketIO()/*-{
                                                      return $wnd.io;
                                                      }-*/;

}
