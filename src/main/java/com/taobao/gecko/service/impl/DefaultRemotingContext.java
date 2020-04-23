/*
 * (C) 2007-2012 Alibaba Group Holding Limited.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.taobao.gecko.service.impl;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.taobao.gecko.core.command.CommandFactory;
import com.taobao.gecko.core.command.Constants;
import com.taobao.gecko.core.command.RequestCommand;
import com.taobao.gecko.core.nio.NioSession;
import com.taobao.gecko.core.util.MBeanUtils;
import com.taobao.gecko.service.Connection;
import com.taobao.gecko.service.ConnectionLifeCycleListener;
import com.taobao.gecko.service.RemotingContext;
import com.taobao.gecko.service.RequestProcessor;
import com.taobao.gecko.service.config.BaseConfig;


/**
 * 
 * ͨѶ���ȫ��������
 * 
 * @author boyan
 * 
 * @since 1.0, 2009-12-15 ����02:46:34
 */

public class DefaultRemotingContext implements RemotingContext, DefaultRemotingContextMBean {
    private final GroupManager groupManager;
    private final ConcurrentHashMap<Object, Object> attributes = new ConcurrentHashMap<Object, Object>();
    private final BaseConfig config;
    private final CommandFactory commandFactory;
    private final Semaphore callBackSemaphore;
    static final Log log = LogFactory.getLog(DefaultRemotingContext.class);
    /**
     * Session��connection��ӳ���ϵ
     */
    protected final ConcurrentHashMap<NioSession, DefaultConnection> session2ConnectionMap =
            new ConcurrentHashMap<NioSession, DefaultConnection>();
    protected ConcurrentHashMap<Class<? extends RequestCommand>, RequestProcessor<? extends RequestCommand>> processorMap =
            new ConcurrentHashMap<Class<? extends RequestCommand>, RequestProcessor<? extends RequestCommand>>();

    protected final CopyOnWriteArrayList<ConnectionLifeCycleListener> connectionLifeCycleListenerList =
            new CopyOnWriteArrayList<ConnectionLifeCycleListener>();


    public DefaultRemotingContext(final BaseConfig config, final CommandFactory commandFactory) {
        this.groupManager = new GroupManager();
        this.config = config;
        if (commandFactory == null) {
            throw new IllegalArgumentException("CommandFactory����Ϊ��");
        }
        this.commandFactory = commandFactory;
        this.callBackSemaphore = new Semaphore(this.config.getMaxCallBackCount());
        MBeanUtils.registerMBeanWithIdPrefix(this, null);
    }


    @Override
    public int getCallBackCountAvailablePermits() {
        return this.callBackSemaphore.availablePermits();
    }


    @Override
    public CommandFactory getCommandFactory() {
        return this.commandFactory;
    }


    @Override
    public BaseConfig getConfig() {
        return this.config;
    }


    /**
     * �����������callBack����callBack��������
     * 
     * @return
     */
    boolean aquire() {
        return this.callBackSemaphore.tryAcquire();
    }


    /**
     * ��Ӧ�𵽴�ʱ�ͷ����
     */
    void release() {
        this.callBackSemaphore.release();
    }


    void release(final int n) {
        this.callBackSemaphore.release(n);
    }


    void notifyConnectionCreated(final Connection conn) {
        for (final ConnectionLifeCycleListener listener : this.connectionLifeCycleListenerList) {
            try {
                listener.onConnectionCreated(conn);
            }
            catch (final Throwable t) {
                log.error("NotifyRemoting-����ConnectionLifeCycleListener.onConnectionCreated����", t);
            }
        }
    }


    void notifyConnectionClosed(final Connection conn) {
        for (final ConnectionLifeCycleListener listener : this.connectionLifeCycleListenerList) {
            try {
                listener.onConnectionClosed(conn);
            }
            catch (final Throwable t) {
                log.error("NotifyRemoting-����ConnectionLifeCycleListener.onConnectionClosed����", t);
            }
        }
    }


    void addSession2ConnectionMapping(final NioSession session, final DefaultConnection conn) {
        this.session2ConnectionMap.put(session, conn);
    }


    DefaultConnection getConnectionBySession(final NioSession session) {
        return this.session2ConnectionMap.get(session);
    }


    DefaultConnection removeSession2ConnectionMapping(final NioSession session) {
        return this.session2ConnectionMap.remove(session);
    }


    @Override
    public Set<String> getGroupSet() {
        return this.groupManager.getGroupSet();
    }


    @Override
    public boolean addConnectionToGroup(final String group, final Connection connection) {
        return this.groupManager.addConnection(group, connection);
    }


    @Override
    public void addConnection(final Connection connection) {
        this.groupManager.addConnection(Constants.DEFAULT_GROUP, connection);
    }


    @Override
    public void removeConnection(final Connection connection) {
        this.groupManager.removeConnection(Constants.DEFAULT_GROUP, connection);
    }


    @Override
    public List<Connection> getConnectionsByGroup(final String group) {
        return this.groupManager.getConnectionsByGroup(group);
    }


    public void awaitGroupConnectionsEmpty(String group, long time) throws InterruptedException, TimeoutException {
        this.groupManager.awaitGroupConnectionsEmpty(group, time);
    }


    @Override
    public boolean removeConnectionFromGroup(final String group, final Connection connection) {
        return this.groupManager.removeConnection(group, connection);
    }


    @Override
    public Object getAttribute(final Object key, final Object defaultValue) {
        final Object value = this.attributes.get(key);
        return value == null ? defaultValue : value;
    }


    @Override
    public Object getAttribute(final Object key) {
        return this.attributes.get(key);
    }

    @Override
    public Set<Object> getAttributeKeys() {
        return this.attributes.keySet();
    }


    @Override
    public Object setAttribute(final Object key, final Object value) {
        return this.attributes.put(key, value);
    }


    public Object setAttribute(final Object key) {
        return this.attributes.put(key, null);
    }


    public void dispose() {
        this.groupManager.clear();
        this.attributes.clear();
    }

    @Override
    public Object setAttributeIfAbsent(final Object key, final Object value) {
        return this.attributes.putIfAbsent(key, value);
    }

    public Object removeAttribute(final Object key) {
        return this.attributes.remove(key);
    }


    @Override
    public Object setAttributeIfAbsent(final Object key) {
        return this.attributes.putIfAbsent(key, null);
    }

}