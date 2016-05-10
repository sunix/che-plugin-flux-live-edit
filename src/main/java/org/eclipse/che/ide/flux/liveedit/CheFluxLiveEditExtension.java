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


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.che.api.machine.gwt.client.MachineManager;
import org.eclipse.che.api.machine.gwt.client.MachineServiceClient;
import org.eclipse.che.api.machine.shared.dto.MachineProcessDto;
import org.eclipse.che.api.machine.shared.dto.event.MachineProcessEvent;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.extension.Extension;
import org.eclipse.che.ide.extension.machine.client.command.CommandManager;
import org.eclipse.che.ide.extension.machine.client.command.valueproviders.CommandPropertyValueProvider;
import org.eclipse.che.ide.extension.machine.client.command.valueproviders.CommandPropertyValueProviderRegistry;
import org.eclipse.che.ide.jseditor.client.document.Document;
import org.eclipse.che.ide.jseditor.client.document.DocumentHandle;
import org.eclipse.che.ide.jseditor.client.events.DocumentChangeEvent;
import org.eclipse.che.ide.jseditor.client.events.DocumentChangeHandler;
import org.eclipse.che.ide.jseditor.client.events.DocumentReadyEvent;
import org.eclipse.che.ide.jseditor.client.events.DocumentReadyHandler;
import org.eclipse.che.ide.jseditor.client.text.TextPosition;
import org.eclipse.che.ide.project.event.ProjectExplorerLoadedEvent;
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

    private Map<String, Document>                liveDocuments   = new HashMap<String, Document>();

    private SocketOverlay                        socket;

    private boolean                              isUpdatingModel = false;

    private MessageBus                           messageBus;

    private CommandManager                       commandManager;

    private CommandPropertyValueProviderRegistry commandPropertyValueProviderRegistry;

    private EventBus                             eventBus;

    private AppContext                           appContext;

    private MachineServiceClient                 machineServiceClient;

    private DtoUnmarshallerFactory               dtoUnmarshallerFactory;

    @Inject
    public CheFluxLiveEditExtension(final MessageBusProvider messageBusProvider,
                                    final EventBus eventBus,
                                    final MachineManager machineManager,
                                    final MachineServiceClient machineServiceClient,
                                    final DtoUnmarshallerFactory dtoUnmarshallerFactory,
                                    final AppContext appContext,
                                    final CommandManager commandManager,
                                    final CommandPropertyValueProviderRegistry commandPropertyValueProviderRegistry) {
        this.dtoUnmarshallerFactory = dtoUnmarshallerFactory;
        this.commandManager = commandManager;
        this.messageBus = messageBusProvider.getMessageBus();
        this.commandPropertyValueProviderRegistry = commandPropertyValueProviderRegistry;
        this.eventBus = eventBus;
        this.appContext = appContext;
        this.machineServiceClient = machineServiceClient;

        injectSocketIO();

        connectToFluxOnProjectLoaded();

        connectToFluxOnFluxProcessStarted();

        sendFluxMessageOnDocumentModelChanged();
    }


    private void injectSocketIO() {
        SocketIOResources ioresources = GWT.create(SocketIOResources.class);
        ScriptInjector.fromString(ioresources.socketIo().getText()).setWindow(ScriptInjector.TOP_WINDOW).inject();
    }

    private void connectToFluxOnProjectLoaded() {
        eventBus.addHandler(ProjectExplorerLoadedEvent.getType(), new ProjectExplorerLoadedEvent.ProjectExplorerLoadedHandler() {
            @Override
            public void onProjectsLoaded(ProjectExplorerLoadedEvent event) {
                String machineId = appContext.getDevMachine().getId();
                Promise<List<MachineProcessDto>> processesPromise = machineServiceClient.getProcesses(machineId);
                processesPromise.then(new Operation<List<MachineProcessDto>>() {
                    @Override
                    public void apply(final List<MachineProcessDto> descriptors) throws OperationException {
                        if (descriptors.isEmpty()) {
                            return;
                        }
                        for (MachineProcessDto machineProcessDto : descriptors) {
                            if (connectIfFluxMicroservice(machineProcessDto)) {
                                break;
                            }
                        }
                    }
                });
            }
        });
    }

    private boolean connectIfFluxMicroservice(MachineProcessDto descriptor) {
        if (descriptor == null) {
            return false;
        }
        if ("flux".equals(descriptor.getName())) {
            String urlToSubstitute = "http://${server.port.3000/tcp}";
            if (commandPropertyValueProviderRegistry == null) {
                return false;
            }
            substituteAndConnect(urlToSubstitute);
            return true;
        }
        return false;
    }

    int trySubstitude = 10;

    public void substituteAndConnect(final String commandLine) {
        try {
            String cmdLine = commandLine;
            List<CommandPropertyValueProvider> providers = commandPropertyValueProviderRegistry.getProviders();
            for (CommandPropertyValueProvider provider : providers) {
                cmdLine = cmdLine.replace(provider.getKey(), provider.getValue());
            }
            connectToFlux(cmdLine);
            return;
        } catch (Exception e) {
            if (trySubstitude > 0) {
                new Timer() {
                    @Override
                    public void run() {
                        substituteAndConnect(commandLine);
                    }
                }.schedule(1000);
                return;
            }
            throw e;
        }
    }

    int retryConnectToFlux = 5;

    protected void connectToFlux(final String url) {

        final SocketIOOverlay io = getSocketIO();
        Log.info(getClass(), "connecting to " + url);

        socket = io.connect(url);
        socket.on("error", new Runnable() {
            @Override
            public void run() {
                Log.info(getClass(), "error connecting to " + url);
            }
        });

        socket.on("liveResourceChanged", new Consumer<FluxResourceChangedEventDataOverlay>() {
            @Override
            public void accept(FluxResourceChangedEventDataOverlay event) {
                Document document = liveDocuments.get("/" + event.getProject() + "/" + event.getResource());
                if (document == null) {
                    return;
                }
                String addedCharacters = event.getAddedCharacters();
                isUpdatingModel = true;
                TextPosition cursorPosition = document.getCursorPosition();
                document.replace(event.getOffset(), event.getRemovedCharCount(), addedCharacters);
                document.setCursorPosition(cursorPosition);
                isUpdatingModel = false;
            }
        });

        socket.emit("connectToChannel", JsonUtils.safeEval("{\"channel\" : \"USER\"}"));
    }

    public static native SocketIOOverlay getSocketIO()/*-{
                                                      return $wnd.io;
                                                      }-*/;

    private void connectToFluxOnFluxProcessStarted() {
        String machineId = appContext.getDevMachine().getId();
        final Unmarshallable<MachineProcessEvent> unmarshaller = dtoUnmarshallerFactory.newWSUnmarshaller(MachineProcessEvent.class);
        final String processStateChannel = "machine:process:" + machineId;
        final MessageHandler handler = new SubscriptionHandler<MachineProcessEvent>(unmarshaller) {
            @Override
            protected void onMessageReceived(MachineProcessEvent result) {
                if (MachineProcessEvent.EventType.STARTED.equals(result.getEventType())) {
                    final int processId = result.getProcessId();
                    machineServiceClient.getProcesses(appContext.getDevMachine().getId()).then(new Operation<List<MachineProcessDto>>() {
                        @Override
                        public void apply(List<MachineProcessDto> descriptors) throws OperationException {
                            if (descriptors.isEmpty()) {
                                return;
                            }

                            for (final MachineProcessDto machineProcessDto : descriptors) {
                                if (machineProcessDto.getPid() == processId) {
                                     new Timer() {
                                        @Override
                                        public void run() {
                                            if (connectIfFluxMicroservice(machineProcessDto)) {
                                                // break;
                                            }
                                        }
                                    }.schedule(8000);
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
    }

    private void wsSubscribe(String wsChannel, MessageHandler handler) {
        try {
            messageBus.subscribe(wsChannel, handler);
        } catch (WebSocketException e) {
            Log.error(getClass(), e);
        }
    }

    private void sendFluxMessageOnDocumentModelChanged() {
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
                            String json = "{"//
                                          + "\"username\":\"USER\","//
                                          + "\"project\":\"" + project + "\","//
                                          + "\"resource\":\"" + resource + "\"," //
                                          + "\"offset\":" + event.getOffset() + "," //
                                          + "\"removedCharCount\":" + event.getRemoveCharCount() + "," //
                                          + "\"addedCharacters\": " + text //
                                          + "}";
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

}
