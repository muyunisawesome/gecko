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
import com.taobao.gecko.service.exception.NotifyRemotingException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * ���ӵİ�װ���ṩ���߲�εĳ���
 *
 * @author boyan
 * @since 1.0, 2009-12-15 ����02:38:20
 */

public interface Connection {

    /**
     * ��ȡnotify-remoting��ȫ��������
     *
     * @return
     */
    RemotingContext getRemotingContext();


    /**
     * �ر�����
     *
     * @param allowReconnect ���true���������Զ�����
     * @throws NotifyRemotingException
     */
    void close(boolean allowReconnect) throws NotifyRemotingException;


    /**
     * �����Ƿ���Ч
     *
     * @return
     */
    boolean isConnected();


    /**
     * ͬ�����ã�ָ����ʱʱ��
     *
     * @param requestCommand ��������
     * @param time           ʱ��
     * @param timeUnit       ʱ�䵥λ
     * @return
     * @throws InterruptedException
     * @throws TimeoutException
     */
    ResponseCommand invoke(final RequestCommand requestCommand, long time, TimeUnit timeUnit)
            throws InterruptedException, TimeoutException, NotifyRemotingException;


    /**
     * ͬ�����ã�Ĭ�ϳ�ʱ1��
     *
     * @param request
     * @return
     * @throws InterruptedException
     * @throws TimeoutException
     */
    ResponseCommand invoke(final RequestCommand request) throws InterruptedException, TimeoutException,
            NotifyRemotingException;


    /**
     * �첽���ͣ�ָ���ص���������Ĭ�ϳ�ʱ1�룬��ʱ������һ����ʱӦ����ص�������
     *
     * @param requestCommand
     * @param listener
     */
    void send(final RequestCommand requestCommand, SingleRequestCallBackListener listener)
            throws NotifyRemotingException;


    /**
     * �첽���ͣ�ָ���ص��������ͳ�ʱʱ�䣬��ʱ������һ����ʱӦ����ص�������
     *
     * @param requestCommand
     * @param listener
     */
    void send(final RequestCommand requestCommand, SingleRequestCallBackListener listener, long time,
              TimeUnit timeUnit) throws NotifyRemotingException;


    /**
     * �첽������
     *
     * @param requestCommand
     */
    void send(final RequestCommand requestCommand) throws NotifyRemotingException;


    /**
     * �첽���ͣ������ؿ�ȡ����future
     *
     * @param requestCommand
     * @return
     * @throws NotifyRemotingException
     */
    Future<Boolean> asyncSend(final RequestCommand requestCommand) throws NotifyRemotingException;


    /**
     * �����첽Ӧ��
     *
     * @param responseCommand
     */
    void response(final Object responseCommand) throws NotifyRemotingException;


    /**
     * ������ӵ���������
     */
    void clearAttributes();


    /**
     * ��ȡ�����ϵ�ĳ������
     *
     * @param key
     * @return
     */
    Object getAttribute(String key);

    /**
     * �Ƴ�����
     *
     * @param key
     */
    void removeAttribute(String key);


    /**
     * ��������
     *
     * @param key
     * @param value
     */
    void setAttribute(String key, Object value);

    /**
     * �������ԣ�����ConcurrentHashMap.putIfAbsent����
     *
     * @param key
     * @param value
     * @return
     */
    Object setAttributeIfAbsent(String key, Object value);

    /**
     * �������Ե�key����
     *
     * @return
     * @since 1.8.3
     */
    Set<String> attributeKeySet();

    /**
     * ��ȡԶ�˵�ַ
     *
     * @return
     */
    InetSocketAddress getRemoteSocketAddress();

    /**
     * ��ȡ����IP��ַ
     *
     * @return
     */
    InetAddress getLocalAddress();

    /**
     * �������ӵĶ����������ֽ��򣬴�˻���С��
     *
     * @param byteOrder
     */
    void setReadBufferOrder(ByteOrder byteOrder);


    /**
     * ��ȡ���ӵĶ����������ֽ���
     *
     * @return TODO
     */
    ByteOrder getReadBufferOrder();

    /**
     * ���ظ��������ڵķ��鼯��
     *
     * @return
     */
    Set<String> getGroupSet();


    /**
     * �Ƿ����ÿ��ж�д�������������ã����������û��߳�д��socket buffer������ݵķ���Ч�ʣ�
     * �����û��̵߳��жϿ����������ӶϿ���������ʹ�á�Ĭ�ϲ����á�
     *
     * @param writeInterruptibly true�������� false����������
     */
    void setWriteInterruptibly(boolean writeInterruptibly);


    /**
     * �����䣬�޳�ʱ
     *
     * @param head
     * @param tail
     * @param channel
     * @param position
     * @param size
     * @see #transferFrom(IoBuffer, IoBuffer, FileChannel, long, long, Integer,
     * SingleRequestCallBackListener, long, TimeUnit)
     * @since 1.8.3
     */
    void transferFrom(IoBuffer head, IoBuffer tail, FileChannel channel, long position, long size);


    /**
     * ��ָ��FileChannel��positionλ�ÿ�ʼ����size���ֽڵ�socket,
     * remoting�Ḻ��֤��ָ����С�����ݴ����socket�����file channel������ݲ���size��С������ʵ�ʴ�С���䡣
     * ������head��tail��ָ�ڴ����ļ�֮ǰ����֮����Ҫд������ݣ�����Ϊnull�����Ǻ��ļ�������Ϊһ�����������͡�
     * ����ָ���ĳ�ʱʱ����ȡ������(�����û�п�ʼ����Ļ�,�Ѿ���ʼ���޷���ֹ)����֪ͨlistener��
     *
     * @param head
     * @param tail
     * @param channel
     * @param position
     * @param size
     * @param opaque
     * @param listener
     * @param time
     * @param unit
     * @since 1.1.0
     */
    void transferFrom(IoBuffer head, IoBuffer tail, FileChannel channel, long position, long size,
                      Integer opaque, SingleRequestCallBackListener listener, long time, TimeUnit unit)
            throws NotifyRemotingException;

}