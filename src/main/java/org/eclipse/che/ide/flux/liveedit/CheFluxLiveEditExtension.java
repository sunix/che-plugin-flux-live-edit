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
import java.util.List;
import java.util.Map;

import org.eclipse.che.api.machine.gwt.client.MachineManager;
import org.eclipse.che.api.machine.gwt.client.MachineServiceClient;
import org.eclipse.che.api.machine.shared.dto.MachineDto;
import org.eclipse.che.api.machine.shared.dto.MachineProcessDto;
import org.eclipse.che.api.machine.shared.dto.event.MachineProcessEvent;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.extension.Extension;
import org.eclipse.che.ide.extension.machine.client.command.CommandManager;
import org.eclipse.che.ide.jseditor.client.document.Document;
import org.eclipse.che.ide.jseditor.client.document.DocumentHandle;
import org.eclipse.che.ide.jseditor.client.events.DocumentChangeEvent;
import org.eclipse.che.ide.jseditor.client.events.DocumentChangeHandler;
import org.eclipse.che.ide.jseditor.client.events.DocumentReadyEvent;
import org.eclipse.che.ide.jseditor.client.events.DocumentReadyHandler;
import org.eclipse.che.ide.rest.DtoUnmarshallerFactory;
import org.eclipse.che.ide.socketio.Consumer;
import org.eclipse.che.ide.socketio.SocketIOOverlay;
import org.eclipse.che.ide.socketio.SocketIOResources;
import org.eclipse.che.ide.socketio.SocketOverlay;
import org.eclipse.che.ide.util.loging.Log;
import org.eclipse.che.ide.websocket.MessageBus;
import org.eclipse.che.ide.websocket.MessageBusProvider;
import org.eclipse.che.ide.websocket.WebSocketException;
import org.eclipse.che.ide.websocket.events.MessageHandler;
import org.eclipse.che.ide.websocket.rest.SubscriptionHandler;
import org.eclipse.che.ide.websocket.rest.Unmarshallable;

import com.google.gwt.core.client.JsonUtils;
import com.google.gwt.core.client.ScriptInjector;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

@Extension(title = "Che Flux extension", version = "1.0.0")
public class CheFluxLiveEditExtension {

    private Map<String, Document> liveDocuments   = new HashMap<String, Document>();

    private SocketOverlay         socket;

    private boolean               isUpdatingModel = false;

    private MessageBus            messageBus;

    @Inject
    public CheFluxLiveEditExtension(final MessageBusProvider messageBusProvider,
                                    final EventBus eventBus,
                                    final MachineManager machineManager,
                                    final MachineServiceClient machineServiceClient,
                                    final DtoUnmarshallerFactory dtoUnmarshallerFactory,
                                    final AppContext appContext,
                                    final CommandManager commandManager) {

        this.messageBus = messageBusProvider.getMessageBus();

        injectSocketIO();


        String machineId = appContext.getDevMachineId();
        Promise<List<MachineProcessDto>> processesPromise = machineServiceClient.getProcesses(machineId);
        processesPromise.then(new Operation<List<MachineProcessDto>>() {
            @Override
            public void apply(List<MachineProcessDto> descriptors) throws OperationException {

                if (descriptors.isEmpty()) {
                    return;
                }
                Log.info(getClass(), "machine processes");
                for (MachineProcessDto machineProcessDto : descriptors) {
                    Log.info(getClass(), " - " + machineProcessDto);
                    //if (connectIfFluxMicroservice()) {
                    //    break;
                    //}
                }
            }


        });
        final Unmarshallable<MachineProcessEvent> unmarshaller = dtoUnmarshallerFactory.newWSUnmarshaller(MachineProcessEvent.class);
        final String processStateChannel = "machine:process:" + machineId;
        final MessageHandler handler = new SubscriptionHandler<MachineProcessEvent>(unmarshaller) {
            @Override
            protected void onMessageReceived(MachineProcessEvent result) {
                Log.info(getClass(), "process event" + result);

                if (MachineProcessEvent.EventType.STARTED.equals(result.getEventType())) {
                    final int processId = result.getProcessId();
                    machineServiceClient.getProcesses(appContext.getDevMachineId()).then(new Operation<List<MachineProcessDto>>() {
                        @Override
                        public void apply(List<MachineProcessDto> descriptors) throws OperationException {
                            if (descriptors.isEmpty()) {
                                return;
                            }

                            for (MachineProcessDto machineProcessDto : descriptors) {
                                if (machineProcessDto.getPid() == processId) {
                                    Log.info(getClass(), "Started Process" + machineProcessDto);
                                    String stringToSubstitute = "http://${server.port.8080}/${current.project.relpath}";
                                    Log.info(getClass(), "substitude: " + commandManager.substituteProperties(stringToSubstitute));
                                    return;
                                }
                            }
                        }

                    });
                }
            }

            @Override
            protected void onErrorReceived(Throwable exception) {
                Log.error(getClass(), exception);
            }
        };
        wsSubscribe(processStateChannel, handler);


        eventBus.addHandler(DocumentReadyEvent.TYPE, new DocumentReadyHandler() {
            @Override
            public void onDocumentReady(DocumentReadyEvent event) {
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
                            String json =
                                          "{" + "\"username\":\"USER\"," + "\"project\":\"" + project + "\"," + "\"resource\":\""
                                              + resource + "\"," + "\"offset\":" + event.getOffset() + "," + "\"removedCharCount\":"
                                              + event.getRemoveCharCount() + "," + "\"addedCharacters\":" + text + "}";
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

    private void injectSocketIO() {
        SocketIOResources ioresources = GWT.create(SocketIOResources.class);
        ScriptInjector.fromString(ioresources.socketIo().getText()).setWindow(ScriptInjector.TOP_WINDOW).inject();
    }

    private boolean connectIfFluxMicroservice(MachineProcessEvent descriptor) {
        /*        if (descriptor == null) {
         return false;
         }
         String fluxPort;
         try {
         fluxPort = descriptor.getPortMapping().getPorts().get("3000");
         } catch (Exception e) {
         Log.error(getClass(), e);
         return false;
         }
         if (fluxPort == null) {
         return false;
         }
         String host = descriptor.getPortMapping().getHost();

         switch (descriptor.getStatus()) {
         case RUNNING:
         connectToFlux(descriptor, host, fluxPort);
         return true;
         case FAILED:
         case STOPPED:
         case CANCELLED:
         break;
         default:
         }
         */
        return false;
    }

    protected void connectToFlux(MachineProcessDto descriptor, final String host, final String fluxPort) {
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

                        socket.on("liveResourceChanged", new Consumer<FluxResourceChangedEventDataOverlay>() {
                            @Override
                            public void accept(FluxResourceChangedEventDataOverlay event) {
                                Document document = liveDocuments.get("/" + event.getProject() + "/" + event.getResource());
                                if (document == null) {
                                    return;
                                }
                                isUpdatingModel = true;
                                document.replace(event.getOffset(), event.getRemovedCharCount(), event.getAddedCharacters());
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

    private void wsSubscribe(String wsChannel, MessageHandler handler) {
        try {
            messageBus.subscribe(wsChannel, handler);
        } catch (WebSocketException e) {
            Log.error(getClass(), e);
        }
    }

}
