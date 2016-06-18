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

import org.eclipse.che.api.machine.shared.dto.MachineProcessDto;
import org.eclipse.che.api.machine.shared.dto.event.MachineProcessEvent;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.editor.EditorPartPresenter;
import org.eclipse.che.ide.api.editor.document.Document;
import org.eclipse.che.ide.api.editor.document.DocumentHandle;
import org.eclipse.che.ide.api.editor.events.*;
import org.eclipse.che.ide.api.editor.text.Position;
import org.eclipse.che.ide.api.editor.text.TextPosition;
import org.eclipse.che.ide.api.editor.text.TextRange;
import org.eclipse.che.ide.api.editor.texteditor.CursorModelWithHandler;
import org.eclipse.che.ide.api.editor.texteditor.TextEditorPresenter;
import org.eclipse.che.ide.api.extension.Extension;
import org.eclipse.che.ide.api.machine.MachineServiceClient;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.notification.StatusNotification;
import org.eclipse.che.ide.extension.machine.client.command.CommandManager;
import org.eclipse.che.ide.extension.machine.client.command.valueproviders.CommandPropertyValueProviderRegistry;
import org.eclipse.che.ide.project.event.ProjectExplorerLoadedEvent;
import org.eclipse.che.ide.rest.DtoUnmarshallerFactory;
import org.eclipse.che.ide.socketio.Consumer;
import org.eclipse.che.ide.socketio.Message;
import org.eclipse.che.ide.socketio.SocketIOOverlay;
import org.eclipse.che.ide.socketio.SocketIOResources;
import org.eclipse.che.ide.socketio.SocketOverlay;
import org.eclipse.che.ide.util.ListenerManager;
import org.eclipse.che.ide.util.ListenerRegistrar;
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

import org.eclipse.che.ide.resource.Path;

import static org.eclipse.che.ide.api.notification.ReadState.READ;
import static org.eclipse.che.ide.api.notification.StatusNotification.Status.SUCCESS;
import static org.eclipse.che.ide.api.notification.StatusNotification.DisplayMode.FLOAT_MODE;


@Extension(title = "Che Flux extension", version = "1.0.0")
public class CheFluxLiveEditExtension implements CursorModelWithHandler, CursorActivityHandler {

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

    private EditorAgent editorAgent;
    private TextEditorPresenter textEditor;
    private EditorPartPresenter openedEditor;
    private Path path;
    private Document documentMain;
    private final ListenerManager<CursorHandler> cursorHandlerManager = ListenerManager.create();
    private CursorHandlerForPairProgramming cursorHandlerForPairProgramming;
    private boolean isDocumentChanged = false;
    private NotificationManager notificationManager;
    private static Map<String,CursorHandlerForPairProgramming> cursorHandlers = new HashMap<String,CursorHandlerForPairProgramming>();
    private static int userCount = 0;
    private static final String channelName = "USER";
    private String userId;


    @Inject
    public CheFluxLiveEditExtension(final MessageBusProvider messageBusProvider,
                                    final EventBus eventBus,
                                    final MachineServiceClient machineServiceClient,
                                    final DtoUnmarshallerFactory dtoUnmarshallerFactory,
                                    final AppContext appContext,
                                    final CommandManager commandManager,
                                    final CommandPropertyValueProviderRegistry commandPropertyValueProviderRegistry, EditorAgent editorAgent, NotificationManager notificationManager) {
        this.dtoUnmarshallerFactory = dtoUnmarshallerFactory;
        this.commandManager = commandManager;
        this.messageBus = messageBusProvider.getMessageBus();
        this.commandPropertyValueProviderRegistry = commandPropertyValueProviderRegistry;
        this.eventBus = eventBus;
        this.appContext = appContext;
        this.machineServiceClient = machineServiceClient;
        this.editorAgent = editorAgent;
        this.notificationManager = notificationManager;

        injectSocketIO();
        injectCssStyles();

        connectToFluxOnProjectLoaded();

        connectToFluxOnFluxProcessStarted();

        sendFluxMessageOnDocumentModelChanged();
    }


    private void injectCssStyles(){
        com.google.gwt.dom.client.StyleInjector.inject(".pairProgramminigUser1 { outline: 1px solid #f3ff20; animation: blinker 1s linear infinite;} @keyframes blinker { 50% { opacity: 0.0; }}");
        com.google.gwt.dom.client.StyleInjector.inject(".pairProgramminigUser2 { outline: 1px solid #10ff22; animation: blinker 1s linear infinite;} @keyframes blinker { 50% { opacity: 0.0; }}");
        com.google.gwt.dom.client.StyleInjector.inject(".pairProgramminigUser3 { outline: 1px solid #00a1ff; animation: blinker 1s linear infinite;} @keyframes blinker { 50% { opacity: 0.0; }}");
        com.google.gwt.dom.client.StyleInjector.inject(".pairProgramminigUser4 { outline: 1px solid #ff00fb; animation: blinker 1s linear infinite;} @keyframes blinker { 50% { opacity: 0.0; }}");
        com.google.gwt.dom.client.StyleInjector.inject(".pairProgramminigUser5 { outline: 1px solid #10fdff; animation: blinker 1s linear infinite;} @keyframes blinker { 50% { opacity: 0.0; }}");
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
            commandManager.substituteProperties(commandLine).then(new Operation<String>() {
                @Override
                public void apply(String cmdLine) throws OperationException {
                    connectToFlux(cmdLine);
                }
            });
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

                isUpdatingModel = true;
                path = new Path(document.getFile().getPath());
                openedEditor = editorAgent.getOpenedEditor(path);
                if (openedEditor instanceof TextEditorPresenter){
                    textEditor  = (TextEditorPresenter)openedEditor;
                }

                String annotationStyle;
                String username = event.getChannelName();
                if (cursorHandlers.get(username)==null){
                    cursorHandlerForPairProgramming = new CursorHandlerForPairProgramming();
                    cursorHandlerForPairProgramming.setUser(username);
                    if (userCount==5){
                        userCount =0;
                    }
                    userCount++;
                    cursorHandlerForPairProgramming.setUserId(userCount);
                    cursorHandlers.put(username,cursorHandlerForPairProgramming);
                }

                cursorHandlerForPairProgramming = cursorHandlers.get(username);
                annotationStyle = "pairProgramminigUser"+ cursorHandlerForPairProgramming.getUserId();
                int offset = event.getOffset();
                if (event.getRemovedCharCount()==-100){
                    TextPosition markerPosition = textEditor.getDocument().getPositionFromIndex(offset);
                    TextRange textRange = new TextRange(markerPosition, markerPosition);
                    if (cursorHandlerForPairProgramming.getMarkerRegistration()!= null){
                        cursorHandlerForPairProgramming.clearMark();
                    }
                    cursorHandlerForPairProgramming.setMarkerRegistration(textEditor.getHasTextMarkers().addMarker(textRange,annotationStyle));
                    cursorHandlers.remove(username);
                    cursorHandlers.put(username,cursorHandlerForPairProgramming);
                    isUpdatingModel = false;
                    return;
                }

                if (openedEditor == null){
                    StatusNotification statusNotification = new StatusNotification(document.getFile().getPath()+" is being edited",SUCCESS,FLOAT_MODE);
                    statusNotification.setState(READ);
                    notificationManager.notify(statusNotification);
                    return;
                }
                if (event.getRemovedCharCount()==0){
                    offset ++;
                }
                String addedCharacters = event.getAddedCharacters();
                TextPosition cursorPosition = document.getCursorPosition();
                document.replace(event.getOffset(), event.getRemovedCharCount(), addedCharacters);
                document.setCursorPosition(cursorPosition);
                TextPosition markerPosition = textEditor.getDocument().getPositionFromIndex(offset);
                TextRange textRange = new TextRange(markerPosition, markerPosition);
                if (cursorHandlerForPairProgramming.getMarkerRegistration()!= null){
                    cursorHandlerForPairProgramming.clearMark();
                }
                cursorHandlerForPairProgramming.setMarkerRegistration(textEditor.getHasTextMarkers().addMarker(textRange,annotationStyle));
                cursorHandlers.remove(username);
                cursorHandlers.put(username,cursorHandlerForPairProgramming);
                isUpdatingModel = false;
            }
        });

        socket.emit("connectToChannel", JsonUtils.safeEval("{\"channel\" : \""+channelName+"\"}"));
    }


    public static native SocketIOOverlay getSocketIO()/*-{
                                                      return $wnd.io;
                                                      }-*/;

    private void connectToFluxOnFluxProcessStarted() {
        eventBus.addHandler(ProjectExplorerLoadedEvent.getType(), new ProjectExplorerLoadedEvent.ProjectExplorerLoadedHandler() {

            @Override
            public void onProjectsLoaded(ProjectExplorerLoadedEvent projectExplorerLoadedEvent) {
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
        });

    }

    private void wsSubscribe(String wsChannel, MessageHandler handler) {
        try {
            messageBus.subscribe(wsChannel, handler);
        } catch (WebSocketException e) {
            Log.error(getClass(), e);
        }
    }

    private void sendFluxMessageOnDocumentModelChanged() {

        cursorHandlerForPairProgramming = new CursorHandlerForPairProgramming();
        eventBus.addHandler(DocumentReadyEvent.TYPE, new DocumentReadyHandler() {
            @Override
            public void onDocumentReady(DocumentReadyEvent event) {
                userId = "user" + Math.random();
                liveDocuments.put(event.getDocument().getFile().getPath(), event.getDocument());
                documentMain = event.getDocument();
                setCursorHandler();
                final DocumentHandle documentHandle = documentMain.getDocumentHandle();
                Message message = new FluxMessageBuilder().with(documentMain).withChannelName(userId) //
                                                          .buildResourceRequestMessage();
                socket.emit(message);
                documentHandle.getDocEventBus().addHandler(DocumentChangeEvent.TYPE, new DocumentChangeHandler() {
                    @Override
                    public void onDocumentChange(DocumentChangeEvent event) {
                        if (socket != null) {
                            Message liveResourceChangeMessage = new FluxMessageBuilder().with(event).withUserName(channelName).withChannelName(userId)//
                                                                                        .buildLiveResourceChangeMessage();
                            isDocumentChanged = true;
                            if (isUpdatingModel) {
                                return;
                            }
                            socket.emit(liveResourceChangeMessage);

                        }
                    }
                });
            }
        });
    }

    @Override
    public void setCursorPosition(int offset) {
        TextPosition position = documentMain.getPositionFromIndex(offset);
        documentMain.setCursorPosition(position);
    }

    @Override
    public Position getCursorPosition() {
        TextPosition position = documentMain.getCursorPosition();
        int offset = documentMain.getIndexFromPosition(position);
        return new Position(offset);
    }

    @Override
    public ListenerRegistrar.Remover addCursorHandler(CursorHandler handler) {
        return this.cursorHandlerManager.add(handler);
    }

    private void sendCursorPosition() {
        if (socket != null) {
            path = new Path(documentMain.getFile().getPath());
            openedEditor = editorAgent.getOpenedEditor(path);
            if (openedEditor instanceof TextEditorPresenter){
                textEditor  = (TextEditorPresenter)openedEditor;
            }
            int offset = textEditor.getCursorOffset();
            Log.info(CheFluxLiveEditExtension.class,offset);
            Message liveResourceChangeMessage = new FluxMessageBuilder().with(documentMain).withOffset(offset).withRemovedCharCount(-100).withUserName(channelName).withChannelName(userId).buildLiveResourceChangeMessage();
            if (isUpdatingModel) {
                return;
            }
            socket.emit(liveResourceChangeMessage);
        }
    }

    @Override
    public void onCursorActivity(final CursorActivityEvent event) {
        if (isDocumentChanged){
            isDocumentChanged = false;
            return;
        }
        sendCursorPosition();
    }

    private void setCursorHandler(){
        this.documentMain.addCursorHandler(this);
    }
}
