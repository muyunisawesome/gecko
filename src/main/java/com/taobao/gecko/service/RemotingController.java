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
package com.taobao.gecko.service;

import com.taobao.gecko.core.buffer.IoBuffer;
import com.taobao.gecko.core.command.RequestCommand;
import com.taobao.gecko.core.command.ResponseCommand;
import com.taobao.gecko.core.nio.impl.TimerRef;
import com.taobao.gecko.service.exception.NotifyRemotingException;

import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * Notify Remoting�����ӿ�<br>
 * <p>
 * �����ÿͻ��˺ͷ������˼̳�
 * <p>
 * ��������ѡ�����������������
 *
 * @author boyan
 * @since 1.0, 2009-12-16 ����02:15:02
 */

public interface RemotingController {

    /**
     * ��������ѡ������Ĭ��Ϊ���ѡ����
     *
     * @param selector
     */
    void setConnectionSelector(ConnectionSelector selector);


    /**
     * ����Remoting������
     *
     * @throws NotifyRemotingException
     */
    void start() throws NotifyRemotingException;


    /**
     * �ر�Remoting������
     *
     * @throws NotifyRemotingException
     */
    void stop() throws NotifyRemotingException;


    /**
     * �ж�ͨѶ����Ƿ�����
     *
     * @return
     */
    boolean isStarted();


    /**
     * ע��command��Ӧ�Ĵ�����
     * <p>
     * ��������----��Ӧ----command
     *
     * @param <T>
     * @param commandClazz ������ ������
     * @param processor    ��Ӧ�Ĵ�����
     */
    <T extends RequestCommand> void registerProcessor(Class<T> commandClazz, RequestProcessor<T> processor);


    /**
     * ��ȡcommand��Ӧ�Ĵ�����
     *
     * @param clazz
     * @return
     */
    RequestProcessor<? extends RequestCommand> getProcessor(Class<? extends RequestCommand> clazz);


    /**
     * ȡ����������ע��,���ر�ȡ���Ĵ�����
     *
     * @param clazz
     * @return
     */
    RequestProcessor<? extends RequestCommand> unRegisterProcessor(Class<? extends RequestCommand> clazz);


    /**
     * ���������������
     *
     * @param map
     */
    void addAllProcessors(Map<Class<? extends RequestCommand>, RequestProcessor<? extends RequestCommand>> map);


    /**
     * ���һ����ʱ��
     *
     * @param timerRef
     */
    void insertTimer(TimerRef timerRef);


    /**
     * �첽������Ϣ��������飬ÿ��������ݲ���ѡһ�����ӷ��ͣ�ָ���ص��������ͳ�ʱʱ�䣬��ʱ������һ����ʱӦ����ص�������
     *
     * @param groupObjects group->message
     * @param listener     Ӧ������
     * @param timeout      ��ʱʱ��
     * @param timeUnit     ʱ�䵥λ
     * @param args         ����
     */
    void sendToGroups(Map<String, RequestCommand> groupObjects, MultiGroupCallBackListener listener,
                      long timeout, TimeUnit timeUnit, Object... args) throws NotifyRemotingException;


    /**
     * �첽��������Ϣ���������
     *
     * @param groupObjects
     */
    void sendToGroups(Map<String, RequestCommand> groupObjects) throws NotifyRemotingException;


    /**
     * �첽�����͸���������
     *
     * @param command
     */
    void sendToAllConnections(RequestCommand command) throws NotifyRemotingException;


    /**
     * �첽�����͸�ָ�������е�һ�����ӣ�Ĭ�����������
     *
     * @param group
     * @param command
     */
    void sendToGroup(String group, RequestCommand command) throws NotifyRemotingException;


    /**
     * ��ָ��FileChannel��positionλ�ÿ�ʼ����size���ֽڵ�ָ��group��һ��socket,
     * remoting�Ḻ��֤��ָ����С�����ݴ����socket�����file channel������ݲ���size��С������ʵ�ʴ�С���䡣
     * ����head��tail��ָ�ڴ����ļ�֮ǰ����֮����Ҫд������ݣ�����Ϊnull�����Ǻ��ļ�������Ϊһ�����������͡�
     * ����ָ���ĳ�ʱʱ����ȡ������(�����û�п�ʼ����Ļ�,�Ѿ���ʼ���޷���ֹ)����֪ͨlistener��
     *
     * @param group
     * @param head
     * @param tail
     * @param channel
     * @param position
     * @param size
     * @param opaque
     * @param listener
     * @param time
     * @param unit
     * @throws NotifyRemotingException
     */
    void transferToGroup(String group, IoBuffer head, IoBuffer tail, FileChannel channel, long position,
                         long size, Integer opaque, SingleRequestCallBackListener listener, long time, TimeUnit unit)
            throws NotifyRemotingException;


    /**
     * ���������ݵ�ָ��group��ĳ��socket���ӣ�������Ҫʹ�õ�ʱ��δ֪��Ҳ����ȡ��
     *
     * @param group
     * @param head
     * @param tail
     * @param channel
     * @param position
     * @param size
     * @see #transferToGroup(String, IoBuffer, IoBuffer, FileChannel, long,
     * long, Integer, SingleRequestCallBackListener, long, TimeUnit)
     */
    void transferToGroup(String group, IoBuffer head, IoBuffer tail, FileChannel channel, long position,
                         long size) throws NotifyRemotingException;


    /**
     * �첽�����͸�ָ���������������
     *
     * @param group
     * @param command
     */
    void sendToGroupAllConnections(String group, RequestCommand command) throws NotifyRemotingException;


    /**
     * �첽���͸�ָ�������е�һ�����ӣ�ָ���ص�������RequestCallBackListener��Ĭ�ϲ����������Ĭ�ϳ�ʱΪ1��,
     * ������ʱʱ�佫����һ����ʱӦ����ص�������
     *
     * @param group    ��������
     * @param command  ��������
     * @param listener ��Ӧ������
     */
    void sendToGroup(String group, RequestCommand command, SingleRequestCallBackListener listener)
            throws NotifyRemotingException;


    /**
     * �첽���͸�ָ�������е�һ�����ӣ�Ĭ�ϲ����������ָ����ʱ,������ʱʱ�佫����һ����ʱӦ����ص�������
     *
     * @param group    ��������
     * @param command  ��������
     * @param listener ��Ӧ������
     */
    void sendToGroup(String group, RequestCommand command, SingleRequestCallBackListener listener, long time,
                     TimeUnit timeunut) throws NotifyRemotingException;


    /**
     * ͬ�����÷����е�һ�����ӣ�Ĭ�ϳ�ʱ1��
     *
     * @param group   ��������
     * @param command ��������
     * @return
     * @throws InterruptedException
     * @throws TimeoutException
     */
    ResponseCommand invokeToGroup(String group, RequestCommand command) throws InterruptedException,
            TimeoutException, NotifyRemotingException;


    /**
     * ͬ�����÷����е�һ�����ӣ�ָ����ʱʱ��
     *
     * @param group    ��������
     * @param command  ��������
     * @param time     ��ʱʱ��
     * @param timeUnit ʱ�䵥λ
     * @return
     * @throws InterruptedException
     * @throws TimeoutException
     */
    ResponseCommand invokeToGroup(String group, RequestCommand command, long time, TimeUnit timeUnit)
            throws InterruptedException, TimeoutException, NotifyRemotingException;


    /**
     * �첽���͸�ָ��������������ӣ�Ĭ�ϳ�ʱ1��,������ʱʱ�佫����һ����ʱӦ����ص�������
     *
     * @param group    ��������
     * @param command  ��������
     * @param listener ��Ӧ������
     */
    void sendToGroupAllConnections(String group, RequestCommand command,
                                   GroupAllConnectionCallBackListener listener) throws NotifyRemotingException;


    /**
     * ͬ�����÷����ڵ��������ӣ�
     * ��ʱ��Ӧ�����ӽ�����һ��BooleanResponseCommand��Ϊ�����������responseStatusΪTIMEOUT
     * ,���������û�����ӽ�����null
     *
     * @param group
     * @param command
     * @return
     * @throws InterruptedException
     * @throws NotifyRemotingException
     */
    Map<Connection, ResponseCommand> invokeToGroupAllConnections(String group, RequestCommand command)
            throws InterruptedException, NotifyRemotingException;


    /**
     * ͬ�����÷����ڵ��������ӣ�
     * ��ʱ��Ӧ�����ӽ�����һ��BooleanResponseCommand��Ϊ�����������responseStatusΪTIMEOUT
     * ,���������û�����ӽ�����null
     *
     * @param group
     * @param command
     * @return
     * @throws InterruptedException
     * @throws NotifyRemotingException
     */
    Map<Connection, ResponseCommand> invokeToGroupAllConnections(String group, RequestCommand command,
                                                                 long time, TimeUnit timeUnit) throws InterruptedException, NotifyRemotingException;


    /**
     * �첽���͸�ָ��������������ӣ�ָ����ʱʱ�䣬������ʱʱ�佫����һ����ʱӦ����ص�������
     *
     * @param group    ��������
     * @param command  ��������
     * @param listener ��Ӧ������
     */
    void sendToGroupAllConnections(String group, RequestCommand command,
                                   GroupAllConnectionCallBackListener listener, long time, TimeUnit timeUnit) throws NotifyRemotingException;


    /**
     * ��ȡgroup��Ӧ��������
     *
     * @param group
     * @return
     */
    int getConnectionCount(String group);


    /**
     * ��ȡgroup����
     *
     * @return
     */
    Set<String> getGroupSet();


    /**
     * ��������
     *
     * @param group
     * @param key
     * @param value
     */
    void setAttribute(String group, String key, Object value);


    /**
     * �������ԣ�����ConcurrentHashMap.putIfAbsent
     *
     * @param group
     * @param key
     * @param value
     * @return
     */
    Object setAttributeIfAbsent(String group, String key, Object value);


    /**
     * ��ȡ����
     *
     * @param group
     * @param key
     * @return
     */
    Object getAttribute(String group, String key);


    /**
     * ��������������ڼ�����
     *
     * @param connectionLifeCycleListener
     */
    void addConnectionLifeCycleListener(ConnectionLifeCycleListener connectionLifeCycleListener);


    /**
     * ��������������ڼ�����
     *
     * @param connectionLifeCycleListener
     */
    void removeConnectionLifeCycleListener(ConnectionLifeCycleListener connectionLifeCycleListener);


    /**
     * �Ƴ�����
     *
     * @param group
     * @param key
     * @return
     */
    Object removeAttribute(String group, String key);


    /**
     * ��ȡȫ��������
     *
     * @return
     */
    RemotingContext getRemotingContext();


    /**
     * ���ݲ��Դӷ����е�����ѡ��һ��
     *
     * @param group
     * @param connectionSelector ����ѡ����
     * @param request            ���͵�����
     * @return
     */
    Connection selectConnectionForGroup(String group, ConnectionSelector connectionSelector,
                                        RequestCommand request) throws NotifyRemotingException;

}