/*
 * (C) 2007-2012 Alibaba Group Holding Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.taobao.gecko.service.impl;

import com.taobao.gecko.core.buffer.IoBuffer;
import com.taobao.gecko.core.command.*;
import com.taobao.gecko.core.command.kernel.BooleanAckCommand;
import com.taobao.gecko.core.config.Configuration;
import com.taobao.gecko.core.core.SocketOption;
import com.taobao.gecko.core.core.impl.StandardSocketOption;
import com.taobao.gecko.core.nio.impl.SocketChannelController;
import com.taobao.gecko.core.nio.impl.TimerRef;
import com.taobao.gecko.core.util.WorkerThreadFactory;
import com.taobao.gecko.service.*;
import com.taobao.gecko.service.callback.GroupAllConnectionRequestCallBack;
import com.taobao.gecko.service.callback.MultiGroupRequestCallBack;
import com.taobao.gecko.service.config.BaseConfig;
import com.taobao.gecko.service.exception.NotifyRemotingException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Remoting Controller����ʵ���࣬ʵ����һЩserver��client���еĹ��ܣ��緢�������
 *
 * @author boyan
 * @since 1.0, 2009-12-16 ����02:16:29
 */

public abstract class BaseRemotingController implements RemotingController {

    //�������������
    protected ConcurrentHashMap<String/* group */, ConcurrentHashMap<String/* key */, Object>> attributes =
            new ConcurrentHashMap<String, ConcurrentHashMap<String, Object>>();

    //remoting������
    protected DefaultRemotingContext remotingContext;
    //����ѡ����
    protected ConnectionSelector connectionSelector = new RoundRobinConnectionSelector();
    /**
     * ����ɨ���������ӵĶ�ʱ�̳߳أ������趨һЩȫ���Ե�������ɨ����Ч�����ӣ����������ϵ���Ч��callBack
     */
    protected ScheduledExecutorService scanAllConnectionExecutor;
    //���ĵĵײ�controller
    protected SocketChannelController controller;
    //����
    protected BaseConfig config;
    protected volatile boolean started;
    private Thread shutdownHook;
    private volatile boolean isHutdownHookCalled;
    /**
     * Ĭ�ϵ��ó�ʱʱ�䣬1��
     */
    protected long opTimeout = 1000L;

    private static final Log log = LogFactory.getLog(BaseRemotingController.class);


    public BaseRemotingController(final BaseConfig baseConfig) {
        this.config = baseConfig;
        if (this.config == null) {
            throw new IllegalArgumentException("Null config object");
        }
        if (this.config.getWireFormatType() == null) {
            throw new IllegalArgumentException("Please set the wire format type");
        }
        this.remotingContext =
                new DefaultRemotingContext(this.config, this.config.getWireFormatType()
                        .newCommandFactory());
    }

    /**
     * ��������¶�ڲ�ʵ�֣��û�����ʹ�ô˷���
     *
     * @return
     */
    public SocketChannelController getController() {
        return this.controller;
    }

    @Override
    public final synchronized void start() throws NotifyRemotingException {
        if (this.started) {
            return;
        }
        this.started = true;
        final StringBuffer info = new StringBuffer("��������RemotingController...\n");
        info.append("����Ϊ��\n").append(this.config.toString());
        log.info(info.toString());
        // 1.��һ����ȡ������
        // ���֮ǰû�������ģ�����һ��ȫ�µ�
        if (this.remotingContext == null) {
            this.remotingContext =
                    new DefaultRemotingContext(this.config, this.config.getWireFormatType()
                            .newCommandFactory());
        } else { //���֮ǰ����һ��������
            // 1.1 ȥ�����ܸ��õĶ���(groupManager, attributes), ����processor��listener
            this.remotingContext.dispose();
            // 1.2 ��ȡ���еĴ�����
            final ConcurrentHashMap<Class<? extends RequestCommand>, RequestProcessor<? extends RequestCommand>> processorMap =
                    this.remotingContext.processorMap;
            // 1.3 ��ȡ���е�connection�������ڼ�����
            final CopyOnWriteArrayList<ConnectionLifeCycleListener> connectionLifeCycleListenerList =
                    this.remotingContext.connectionLifeCycleListenerList;
            // 1.4 ����ȫ�µ�һ�������ġ�����֮ǰ�����������ã����ṩ��Э�������е� �����(������Ӧ����)
            this.remotingContext =
                    new DefaultRemotingContext(this.remotingContext.getConfig(), this.config
                            .getWireFormatType().newCommandFactory());
            // 1.5 ����֮ǰ�Ĵ�����
            this.remotingContext.processorMap.putAll(processorMap);
            // 1.6 ����֮ǰ���������ڼ�����
            this.remotingContext.connectionLifeCycleListenerList
                    .addAll(connectionLifeCycleListenerList);
        }
        //�����ⲿ���ã������ڲ�ȫ������
        final Configuration conf = this.getConfigurationFromConfig(this.config);
        //�����ڲ�����controller������Ƿ������Ǿ���TCPController������ǿͻ����Ǿ���GeckoTCPConnectionController��
        this.controller = this.initController(conf);
        //���ú���controller��codeFactory
        this.controller.setCodecFactory(this.config.getWireFormatType().newCodecFactory());
        //���ú���controller��Handler
        this.controller.setHandler(new GeckoHandler(this));
        //���ú���controller��TCPѡ�so_linger��socketOptions
        this.controller.setSoLinger(this.config.isSoLinger(), this.config.getLinger());
        this.controller.setSocketOptions(this.getSocketOptionsFromConfig(this.config));
        //���ú���controller��selector�ش�С
        this.controller.setSelectorPoolSize(this.config.getSelectorPoolSize());
        //������������ɨ���̳߳أ�Ϊ���̵߳��̳߳�
        this.scanAllConnectionExecutor =
                Executors.newSingleThreadScheduledExecutor(new WorkerThreadFactory(
                        "notify-remoting-ScanAllConnection"));
        //���������ɨ�����ӵ����ڣ�������ɨ���߳�ִ�����ں�����
        if (this.config.getScanAllConnectionInterval() > 0) {
            this.scanAllConnectionExecutor.scheduleAtFixedRate(new ScanAllConnectionRunner(this,
                            this.getScanTasks()), 1, this.config.getScanAllConnectionInterval(),
                    TimeUnit.SECONDS);
        }
        //�ײ�����
        this.doStart();
        //����
        this.addShutdownHook();
    }


    protected abstract void doStart() throws NotifyRemotingException;

    @Override
    public synchronized final void stop() throws NotifyRemotingException {
        if (!this.started) {
            return;
        }
        this.started = false;
        this.doStop();
        try {
            this.controller.stop();
        } catch (final IOException e) {
            throw new NotifyRemotingException("�ر����Ӵ���", e);
        }
        this.remotingContext.dispose();
        this.scanAllConnectionExecutor.shutdown();
        try {
            if (!this.scanAllConnectionExecutor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                this.scanAllConnectionExecutor.shutdownNow();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.attributes.clear();
        this.removeShutdownHook();
    }

    protected abstract void doStop() throws NotifyRemotingException;

    private void addShutdownHook() {
        this.shutdownHook = new Thread() {
            @Override
            public void run() {
                try {
                    BaseRemotingController.this.isHutdownHookCalled = true;
                    BaseRemotingController.this.stop();
                } catch (final NotifyRemotingException e) {
                    log.error("Shutdown remoting failed", e);
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(this.shutdownHook);
    }

    private void removeShutdownHook() {
        if (!this.isHutdownHookCalled && this.shutdownHook != null) {
            Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
        }
    }

    /**
     * ������������Ҫ��ȫ��ɨ������Ĭ��ֻ��ɨ����Чcallback����������ɸ��Ǵ˷���
     *
     * @return
     */
    protected ScanTask[] getScanTasks() {
        return new ScanTask[]{new InvalidCallBackScanTask()};
    }

    @Override
    public DefaultRemotingContext getRemotingContext() {
        return this.remotingContext;
    }


    protected abstract SocketChannelController initController(Configuration conf);

    @Override
    public void insertTimer(final TimerRef timerRef) {
        if (timerRef == null) {
            throw new IllegalArgumentException("��Ч��timerRef,����Ϊnull");
        }
        if (timerRef.getRunnable() == null) {
            throw new IllegalArgumentException("runnable����Ϊnull����ָ����ʱ���е�����");
        }
        if (timerRef.getTimeout() <= 0) {
            throw new IllegalArgumentException("timeout�������0");
        }
        this.controller.getSelectorManager().insertTimer(timerRef);
    }

    @Override
    public boolean isStarted() {
        return this.controller != null && this.controller.isStarted();
    }

    @Override
    public <T extends RequestCommand> void registerProcessor(final Class<T> commandClazz,
                                                             final RequestProcessor<T> processor) {
        if (commandClazz == null) {
            throw new NullPointerException("Null command class");
        }
        if (processor == null) {
            throw new NullPointerException("Null processor");
        }
        this.remotingContext.processorMap.put(commandClazz, processor);
    }

    @Override
    public RequestProcessor<? extends RequestCommand> getProcessor(
            final Class<? extends RequestCommand> clazz) {
        return this.remotingContext.processorMap.get(clazz);
    }

    @Override
    public RequestProcessor<? extends RequestCommand> unRegisterProcessor(
            final Class<? extends RequestCommand> clazz) {
        return this.remotingContext.processorMap.remove(clazz);
    }

    @Override
    public void addAllProcessors(
            final Map<Class<? extends RequestCommand>, RequestProcessor<? extends RequestCommand>> map) {
        this.remotingContext.processorMap.putAll(map);
    }

    @Override
    public void addConnectionLifeCycleListener(
            final ConnectionLifeCycleListener connectionLifeCycleListener) {
        this.remotingContext.connectionLifeCycleListenerList.add(connectionLifeCycleListener);

    }

    @Override
    public void removeConnectionLifeCycleListener(
            final ConnectionLifeCycleListener connectionLifeCycleListener) {
        this.remotingContext.connectionLifeCycleListenerList.remove(connectionLifeCycleListener);
    }

    protected Configuration getConfigurationFromConfig(final BaseConfig config) {
        final Configuration conf = new Configuration();
        conf.setSessionReadBufferSize(config.getReadBufferSize());
        conf.setSessionIdleTimeout(config.getIdleTime() * 1000);
        conf.setStatisticsServer(false);
        conf.setHandleReadWriteConcurrently(true);
        conf.setDispatchMessageThreadCount(config.getDispatchMessageThreadCount());
        conf.setReadThreadCount(config.getReadThreadCount());
        conf.setWriteThreadCount(config.getWriteThreadCount());
        return conf;
    }


    protected Map<SocketOption<?>, Object> getSocketOptionsFromConfig(final BaseConfig config) {
        final Map<SocketOption<?>, Object> result = new HashMap<SocketOption<?>, Object>();

        result.put(StandardSocketOption.SO_SNDBUF, config.getSndBufferSize());
        result.put(StandardSocketOption.SO_KEEPALIVE, config.isKeepAlive());
        if (config.isSoLinger()) {
            result.put(StandardSocketOption.SO_LINGER, config.getLinger());
        }
        result.put(StandardSocketOption.SO_RCVBUF, config.getRcvBufferSize());
        result.put(StandardSocketOption.SO_REUSEADDR, config.isReuseAddr());
        result.put(StandardSocketOption.TCP_NODELAY, config.isTcpNoDelay());
        return result;
    }

    @Override
    public void sendToGroup(final String group, final RequestCommand command)
            throws NotifyRemotingException {
        if (group == null) {
            throw new NotifyRemotingException("Null group");
        }
        if (command == null) {
            throw new NotifyRemotingException("Null command");
        }
        final Connection conn =
                this.selectConnectionForGroup(group, this.connectionSelector, command);
        if (conn != null) {
            conn.send(command);
        } else {
            throw new NotifyRemotingException("����" + group + "û�п��õ�����");
        }
    }

    @Override
    public void sendToGroup(final String group, final RequestCommand command,
                            final SingleRequestCallBackListener listener) throws NotifyRemotingException {
        this.sendToGroup(group, command, listener, this.opTimeout, TimeUnit.MILLISECONDS);
    }

    @Override
    public void sendToGroup(final String group, final RequestCommand request,
                            final SingleRequestCallBackListener listener, final long time, final TimeUnit timeunut)
            throws NotifyRemotingException {
        if (group == null) {
            throw new NotifyRemotingException("Null group");
        }
        if (request == null) {
            throw new NotifyRemotingException("Null command");
        }
        if (listener == null) {
            throw new NotifyRemotingException("Null listener");
        }
        if (timeunut == null) {
            throw new NotifyRemotingException("Null TimeUnit");
        }
        //��group�л�ȡһ��connectionִ�з���
        final Connection conn =
                this.selectConnectionForGroup(group, this.connectionSelector, request);
        if (conn != null) {
            //ִ�з���
            conn.send(request, listener, time, timeunut);
        } else {
            if (listener != null) {
                final ThreadPoolExecutor executor = listener.getExecutor();
                if (executor != null) {
                    executor.execute(new Runnable() {
                        public void run() {
                            listener.onResponse(BaseRemotingController.this
                                            .createNoConnectionResponseCommand(request.getRequestHeader()),
                                    null);
                        }
                    });
                } else {
                    listener.onResponse(
                            this.createNoConnectionResponseCommand(request.getRequestHeader()),
                            null);
                }
            }
        }

    }


    private BooleanAckCommand createNoConnectionResponseCommand(final CommandHeader requestHeader) {
        return this.createCommErrorResponseCommand(requestHeader, "�޿�������");
    }


    private BooleanAckCommand createCommErrorResponseCommand(final CommandHeader requestHeader,
                                                             final String message) {
        final BooleanAckCommand responseCommand =
                this.remotingContext.getCommandFactory().createBooleanAckCommand(requestHeader,
                        ResponseStatus.ERROR_COMM, message);
        responseCommand.setResponseTime(System.currentTimeMillis());
        return responseCommand;
    }

    @Override
    public Connection selectConnectionForGroup(final String group,
                                               final ConnectionSelector connectionSelector, final RequestCommand request)
            throws NotifyRemotingException {
        if (group == null) {
            throw new NotifyRemotingException("Null group");
        }
        final List<Connection> connnections = this.remotingContext.getConnectionsByGroup(group);
        if (connnections != null) {
            return connectionSelector.select(group, request, connnections);
        } else {
            return null;
        }
    }

    @Override
    public ResponseCommand invokeToGroup(final String group, final RequestCommand command)
            throws InterruptedException, TimeoutException, NotifyRemotingException {
        if (group == null) {
            throw new NotifyRemotingException("Null group");
        }
        if (command == null) {
            throw new NotifyRemotingException("Null command");
        }
        final Connection conn =
                this.selectConnectionForGroup(group, this.connectionSelector, command);
        if (conn != null) {
            return conn.invoke(command);
        } else {
            return this.createNoConnectionResponseCommand(command.getRequestHeader());
        }
    }

    @Override
    public ResponseCommand invokeToGroup(final String group, final RequestCommand command,
                                         final long time, final TimeUnit timeUnit) throws InterruptedException,
            TimeoutException, NotifyRemotingException {
        if (group == null) {
            throw new NotifyRemotingException("Null group");
        }
        if (command == null) {
            throw new NotifyRemotingException("Null command");
        }
        if (timeUnit == null) {
            throw new NotifyRemotingException("Null TimeUnit");
        }
        final Connection conn =
                this.selectConnectionForGroup(group, this.connectionSelector, command);
        if (conn != null) {
            return conn.invoke(command, time, timeUnit);
        } else {
            return this.createNoConnectionResponseCommand(command.getRequestHeader());
        }
    }

    @Override
    public Map<Connection, ResponseCommand> invokeToGroupAllConnections(final String group,
                                                                        final RequestCommand command, final long time, final TimeUnit timeUnit)
            throws InterruptedException, NotifyRemotingException {
        if (group == null) {
            throw new NotifyRemotingException("Null group");
        }
        if (command == null) {
            throw new NotifyRemotingException("Null command");
        }
        final List<Connection> connections = this.remotingContext.getConnectionsByGroup(group);

        if (connections != null && connections.size() > 0) {
            final long now = System.currentTimeMillis();
            final CountDownLatch countDownLatch = new CountDownLatch(connections.size());
            final ConcurrentHashMap<Connection, ResponseCommand> resultMap =
                    new ConcurrentHashMap<Connection, ResponseCommand>();
            final GroupAllConnectionRequestCallBack requestCallBack =
                    new GroupAllConnectionRequestCallBack(null, countDownLatch,
                            TimeUnit.MILLISECONDS.convert(time, timeUnit), now, resultMap);

            for (final Connection conn : connections) {
                final DefaultConnection connection = (DefaultConnection) conn;
                if (connection.isConnected()) {
                    try {
                        connection.addRequestCallBack(command.getOpaque(), requestCallBack);
                        requestCallBack.addWriteFuture(connection, connection.asyncSend(command));
                    } catch (final Throwable e) {
                        requestCallBack.onResponse(
                                group,
                                this.createCommErrorResponseCommand(command.getRequestHeader(),
                                        e.getMessage()), connection);
                    }
                } else {
                    requestCallBack.onResponse(group, this.createCommErrorResponseCommand(
                            command.getRequestHeader(), "�����Ѿ��ر�"), connection);
                }
            }
            if (!countDownLatch.await(time, timeUnit)) {
                for (final Connection conn : connections) {
                    if (!resultMap.containsKey(conn)) {
                        if (resultMap.putIfAbsent(
                                conn,
                                this.createTimeoutCommand(command.getRequestHeader(),
                                        conn.getRemoteSocketAddress())) == null) {
                            requestCallBack.cancelWrite(conn);
                            // �м��Ƴ��ص�
                            ((DefaultConnection) conn).removeRequestCallBack(command.getOpaque());
                        }
                    }
                }
            }
            return resultMap;
        } else {
            return null;
        }
    }

    @Override
    public Map<Connection, ResponseCommand> invokeToGroupAllConnections(final String group,
                                                                        final RequestCommand command) throws InterruptedException, NotifyRemotingException {
        return this.invokeToGroupAllConnections(group, command, this.opTimeout,
                TimeUnit.MILLISECONDS);
    }

    private BooleanAckCommand createTimeoutCommand(final CommandHeader requestHeader,
                                                   final InetSocketAddress address) {
        final BooleanAckCommand value =
                this.remotingContext.getCommandFactory().createBooleanAckCommand(requestHeader,
                        ResponseStatus.TIMEOUT, "�ȴ���Ӧ��ʱ");
        value.setResponseStatus(ResponseStatus.TIMEOUT);
        value.setResponseTime(System.currentTimeMillis());
        value.setResponseHost(address);
        return value;
    }

    @Override
    public void sendToAllConnections(final RequestCommand command) throws NotifyRemotingException {
        if (command == null) {
            throw new NotifyRemotingException("Null command");
        }
        final List<Connection> connnections =
                this.remotingContext.getConnectionsByGroup(Constants.DEFAULT_GROUP);
        if (connnections != null) {
            for (final Connection conn : connnections) {
                if (conn.isConnected()) {
                    conn.send(command);
                }
            }
        }
    }

    @Override
    public void sendToGroupAllConnections(final String group, final RequestCommand command)
            throws NotifyRemotingException {
        if (group == null) {
            throw new NotifyRemotingException("Null group");
        }
        if (command == null) {
            throw new NotifyRemotingException("Null command");
        }
        final List<Connection> connections = this.remotingContext.getConnectionsByGroup(group);
        if (connections != null) {
            for (final Connection conn : connections) {
                if (conn.isConnected()) {
                    conn.send(command);
                }
            }
        }
    }

    @Override
    public void sendToGroupAllConnections(final String group, final RequestCommand command,
                                          final GroupAllConnectionCallBackListener listener, final long timeout,
                                          final TimeUnit timeUnit) throws NotifyRemotingException {
        if (group == null) {
            throw new NotifyRemotingException("Null group");
        }
        if (command == null) {
            throw new NotifyRemotingException("Null command");
        }
        if (timeUnit == null) {
            throw new NotifyRemotingException("Null timeUnit");
        }
        final List<Connection> connections = this.remotingContext.getConnectionsByGroup(group);

        if (connections != null && connections.size() > 0) {
            final CountDownLatch countDownLatch = new CountDownLatch(connections.size());
            final ConcurrentHashMap<Connection, ResponseCommand> resultMap =
                    new ConcurrentHashMap<Connection, ResponseCommand>();
            final long timeoutInMillis = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
            // ����callBack
            final GroupAllConnectionRequestCallBack requestCallBack =
                    new GroupAllConnectionRequestCallBack(listener, countDownLatch,
                            timeoutInMillis, System.currentTimeMillis(), resultMap);
            // ������ʱ������
            final TimerRef timerRef =
                    new TimerRef(timeoutInMillis, new GroupAllConnectionCallBackRunner(
                            requestCallBack, resultMap, connections, command.getRequestHeader()));
            requestCallBack.setTimerRef(timerRef);

            for (final Connection conn : connections) {
                final DefaultConnection connection = (DefaultConnection) conn;
                if (connection.isConnected()) {
                    try {
                        connection.addRequestCallBack(command.getOpaque(), requestCallBack);
                        requestCallBack.addWriteFuture(connection, connection.asyncSend(command));
                    } catch (final Throwable t) {
                        requestCallBack.onResponse(group, this.createCommErrorResponseCommand(
                                command.getRequestHeader(), "����ʧ�ܣ�root:" + t.getMessage()),
                                connection);
                    }
                } else {
                    requestCallBack.onResponse(group, this.createCommErrorResponseCommand(
                            command.getRequestHeader(), "�����Ѿ��ر�"), connection);
                }
            }
            // ���붨ʱ��
            this.insertTimer(timerRef);
        } else {
            if (listener != null) {
                if (listener.getExecutor() != null) {
                    listener.getExecutor().execute(new Runnable() {
                        public void run() {
                            listener.onResponse(new HashMap<Connection, ResponseCommand>());
                        }
                    });
                } else {
                    // ����ֱ�ӷ��ؿս����
                    listener.onResponse(new HashMap<Connection, ResponseCommand>());
                }
            }
        }

    }

    @Override
    public void sendToGroups(final Map<String, RequestCommand> groupObjects,
                             final MultiGroupCallBackListener listener, final long timeout, final TimeUnit timeUnit,
                             final Object... args) throws NotifyRemotingException {
        if (groupObjects == null || groupObjects.size() == 0) {
            throw new NotifyRemotingException("groupObjectΪ��");
        }
        if (listener == null) {
            throw new NotifyRemotingException("Null GroupCallBackListener");
        }
        if (timeUnit == null) {
            throw new NotifyRemotingException("Null TimeUnit");
        }
        // ����ռ�
        final CountDownLatch countDownLatch = new CountDownLatch(groupObjects.size());
        final ConcurrentHashMap<String/* group */, ResponseCommand/* Ӧ�� */> resultMap =
                new ConcurrentHashMap<String, ResponseCommand>();
        // ����ӳ��
        final Map<String/* group */, Connection> connectionMap = new HashMap<String, Connection>();
        // ����ӳ��
        final Map<String/* group */, CommandHeader> headerMap =
                new HashMap<String, CommandHeader>();
        // ��ֹ�ظ���Ӧ����Ӧ���
        final AtomicBoolean responsed = new AtomicBoolean(false);

        InetSocketAddress remoteAddr = null;
        // �����ظ���ȡʱ��
        final long now = System.currentTimeMillis();

        final long timeoutInMillis = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
        // ����callBack
        final MultiGroupRequestCallBack groupRequestCallBack =
                new MultiGroupRequestCallBack(listener, countDownLatch, timeoutInMillis, now,
                        resultMap, responsed, args);
        // ������ʱ������
        final TimerRef timerRef =
                new TimerRef(timeoutInMillis, new GroupCallBackRunner(connectionMap,
                        groupRequestCallBack, headerMap, resultMap, remoteAddr));
        groupRequestCallBack.setTimerRef(timerRef);

        for (final Map.Entry<String, RequestCommand> entry : groupObjects.entrySet()) {
            final RequestCommand requestCommand = entry.getValue();
            final String group = entry.getKey();
            final DefaultConnection conn =
                    (DefaultConnection) this.selectConnectionForGroup(group,
                            this.connectionSelector, requestCommand);

            if (conn != null) {
                try {
                    // ���ӳ��
                    connectionMap.put(group, conn);
                    // ���Ӧ��ķ���ӳ��
                    conn.addOpaqueToGroupMapping(requestCommand.getOpaque(), group);
                    // ��ӻص�
                    conn.addRequestCallBack(requestCommand.getOpaque(), groupRequestCallBack);
                    if (remoteAddr == null) {
                        remoteAddr = conn.getRemoteSocketAddress();
                    }
                    groupRequestCallBack.addWriteFuture(conn, conn.asyncSend(requestCommand));
                    headerMap.put(group, requestCommand.getRequestHeader());
                } catch (final Throwable t) {
                    groupRequestCallBack.onResponse(group, this.createCommErrorResponseCommand(
                            requestCommand.getRequestHeader(), t.getMessage()), conn);
                }
            } else {
                // ֱ�ӷ��ش���Ӧ��
                groupRequestCallBack.onResponse(group,
                        this.createNoConnectionResponseCommand(requestCommand.getRequestHeader()),
                        null);
            }
        }
        // ���붨ʱ��
        this.insertTimer(timerRef);
    }

    @Override
    public void sendToGroupAllConnections(final String group, final RequestCommand command,
                                          final GroupAllConnectionCallBackListener listener) throws NotifyRemotingException {
        this.sendToGroupAllConnections(group, command, listener, this.opTimeout,
                TimeUnit.MILLISECONDS);
    }

    /**
     * �������������ӵĻص�ִ���߳�
     *
     * @author boyan
     * @since 1.0, 2009-12-16 ����03:41:29
     */
    private final class GroupAllConnectionCallBackRunner implements Runnable {
        private final ConcurrentHashMap<Connection, ResponseCommand> resultMap;
        private final GroupAllConnectionRequestCallBack requestCallBack;
        private final List<Connection> connections;
        final CommandHeader requestHeader;


        private GroupAllConnectionCallBackRunner(
                final GroupAllConnectionRequestCallBack requestCallBack,
                final ConcurrentHashMap<Connection, ResponseCommand> resultMap,
                final List<Connection> connections, final CommandHeader requestHeader) {
            this.requestCallBack = requestCallBack;
            this.resultMap = resultMap;
            this.connections = connections;
            this.requestHeader = requestHeader;
        }


        public void run() {
            for (final Connection conn : this.connections) {
                if (!this.resultMap.containsKey(conn)) {
                    this.requestCallBack.cancelWrite(conn);
                    this.requestCallBack.onResponse(null,
                            BaseRemotingController.this.createTimeoutCommand(this.requestHeader,
                                    conn.getRemoteSocketAddress()), conn);
                }
            }

        }
    }

    /**
     * ����鷢�ͻص�ִ���߳�
     *
     * @author boyan
     * @since 1.0, 2009-12-16 ����02:00:27
     */
    protected class GroupCallBackRunner implements Runnable {
        final Map<String, CommandHeader> groupObjects;
        final Map<String/* group */, ResponseCommand/* Ӧ�� */> resultMap;
        final Map<String /* group */, Connection> connectionMap;
        final MultiGroupRequestCallBack requestCallBack;
        final InetSocketAddress remoteAddr;


        public GroupCallBackRunner(final Map<String /* group */, Connection> connectionMap,
                                   final MultiGroupRequestCallBack requestCallBack,
                                   final Map<String, CommandHeader> groupObjects,
                                   final Map<String, ResponseCommand> resultMap, final InetSocketAddress remoteAddr) {
            super();
            this.connectionMap = connectionMap;
            this.requestCallBack = requestCallBack;
            this.groupObjects = groupObjects;
            this.resultMap = resultMap;
            this.remoteAddr = remoteAddr;
        }


        public void run() {
            for (final Map.Entry<String, CommandHeader> entry : this.groupObjects.entrySet()) {
                final String group = entry.getKey();
                // û�з���Ӧ��ģ����ó�ʱ
                if (!this.resultMap.containsKey(group)) {
                    final Connection connection = this.connectionMap.get(group);
                    if (connection != null) {
                        this.requestCallBack.cancelWrite(connection);
                        // �޸�Ͷ�ݳ�ʱʱ, ��־�м�¼��hostΪnull�����
                        /*
                         * ResponseCommand value = BaseRemotingController.this.createTimeoutCommand
                         * (entry.getValue(), this.remoteAddr);
                         */
                        final ResponseCommand value =
                                BaseRemotingController.this.createTimeoutCommand(entry.getValue(),
                                        connection.getRemoteSocketAddress());
                        this.requestCallBack.onResponse(group, value, connection);
                    } else {
                        this.requestCallBack.onResponse(group, BaseRemotingController.this
                                .createNoConnectionResponseCommand(entry.getValue()), null);
                    }
                }
            }
        }

    }

    @Override
    public void sendToGroups(final Map<String, RequestCommand> groupObjects)
            throws NotifyRemotingException {
        if (groupObjects == null || groupObjects.size() == 0) {
            throw new NotifyRemotingException("groupObjectsΪ��");
        }
        for (final Map.Entry<String, RequestCommand> entry : groupObjects.entrySet()) {
            final RequestCommand requestCommand = entry.getValue();
            final String group = entry.getKey();
            final Connection conn =
                    this.selectConnectionForGroup(group, this.connectionSelector, requestCommand);
            if (conn != null) {
                conn.send(requestCommand);
            }
        }

    }

    @Override
    public void transferToGroup(final String group, final IoBuffer head, final IoBuffer tail,
                                final FileChannel channel, final long position, final long size, final Integer opaque,
                                final SingleRequestCallBackListener listener, final long time, final TimeUnit unit)
            throws NotifyRemotingException {
        if (group == null) {
            throw new NotifyRemotingException("Null group");
        }
        final Connection conn = this.selectConnectionForGroup(group, this.connectionSelector, null);
        if (conn != null) {
            conn.transferFrom(head, tail, channel, position, size, opaque, listener, time, unit);
        } else {
            throw new NotifyRemotingException("����" + group + "û�п��õ�����");
        }

    }

    @Override
    public void transferToGroup(final String group, final IoBuffer head, final IoBuffer tail,
                                final FileChannel channel, final long position, final long size)
            throws NotifyRemotingException {
        if (group == null) {
            throw new NotifyRemotingException("Null group");
        }
        final Connection conn = this.selectConnectionForGroup(group, this.connectionSelector, null);
        if (conn != null) {
            conn.transferFrom(head, tail, channel, position, size);
        } else {
            throw new NotifyRemotingException("����" + group + "û�п��õ�����");
        }

    }

    @Override
    public void setConnectionSelector(final ConnectionSelector selector) {
        if (selector == null) {
            throw new IllegalArgumentException("Null selector");
        }
        this.connectionSelector = selector;

    }


    public Object getAttribute(final String url, final String key) {
        final ConcurrentHashMap<String, Object> subAttr = this.attributes.get(url);
        if (subAttr == null) {
            return null;
        }
        return subAttr.get(key);
    }


    public Object removeAttribute(final String url, final String key) {
        final ConcurrentHashMap<String, Object> subAttr = this.attributes.get(url);
        if (subAttr == null) {
            return null;
        }
        return subAttr.remove(key);
    }


    public void setAttribute(final String url, final String key, final Object value) {
        ConcurrentHashMap<String, Object> subAttr = this.attributes.get(url);
        if (subAttr == null) {
            subAttr = new ConcurrentHashMap<String, Object>();
            final ConcurrentHashMap<String, Object> oldSubAttr =
                    this.attributes.putIfAbsent(url, subAttr);
            if (oldSubAttr != null) {
                subAttr = oldSubAttr;
            }
        }
        subAttr.put(key, value);

    }


    public Object setAttributeIfAbsent(final String url, final String key, final Object value) {
        ConcurrentHashMap<String, Object> subAttr = this.attributes.get(url);
        if (subAttr == null) {
            subAttr = new ConcurrentHashMap<String, Object>();
            final ConcurrentHashMap<String, Object> oldSubAttr =
                    this.attributes.putIfAbsent(url, subAttr);
            if (oldSubAttr != null) {
                subAttr = oldSubAttr;
            }
        }
        return subAttr.putIfAbsent(key, value);
    }


    public int getConnectionCount(final String group) {
        final List<Connection> connections = this.remotingContext.getConnectionsByGroup(group);
        return connections == null ? 0 : connections.size();
    }


    public Set<String> getGroupSet() {
        return this.remotingContext.getGroupSet();
    }

}
