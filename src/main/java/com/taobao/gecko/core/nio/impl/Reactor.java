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
/**
 * 
 */

package com.taobao.gecko.core.nio.impl;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Date;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.taobao.gecko.core.config.Configuration;
import com.taobao.gecko.core.core.EventType;
import com.taobao.gecko.core.core.Session;
import com.taobao.gecko.core.core.impl.AbstractSession;
import com.taobao.gecko.core.nio.NioSession;
import com.taobao.gecko.core.util.LinkedTransferQueue;
import com.taobao.gecko.core.util.SystemUtils;


/**
 * 
 * Reactorʵ��
 * 
 * @author boyan
 * 
 * @since 1.0, 2009-12-24 ����01:25:19
 */

public final class Reactor extends Thread {
    /**
     * ��ʱ�����з�����
     * 
     * @author boyan
     * @Date 2010-5-20
     * 
     */
    private final class TimerQueueVisitor implements TimerRefQueue.TimerQueueVisitor {
        private final long now;


        private TimerQueueVisitor(final long now) {
            this.now = now;
        }


        public boolean visit(final TimerRef timerRef) {
            if (!timerRef.isCanceled()) {
                // �Ѿ���ʱ�����ϴ���
                if (timerRef.getTimeoutTimestamp() < this.now) {
                    Reactor.this.timerQueue.remove(timerRef);
                    Reactor.this.controller.onTimeout(timerRef);
                }
                else if (this.now - timerRef.addTimestamp >= TIMEOUT_THRESOLD) {
                    // ������ֵ����Ǩ�����ȶ���
                    Reactor.this.timerQueue.remove(timerRef);
                    Reactor.this.timerHeap.offer(timerRef);
                }

            }
            return true;
        }
    }

    public static final long TIMEOUT_THRESOLD = Long.parseLong(System.getProperty(
        "notify.remoting.timer.timeout_threshold", "500"));
    /**
     * ��ֹjvm bug
     */
    public static final int JVMBUG_THRESHHOLD = Integer.getInteger("com.googlecode.yanf4j.nio.JVMBUG_THRESHHOLD", 128);
    public static final int JVMBUG_THRESHHOLD2 = JVMBUG_THRESHHOLD * 2;
    public static final int JVMBUG_THRESHHOLD1 = (JVMBUG_THRESHHOLD2 + JVMBUG_THRESHHOLD) / 2;

//    public static final int MAX_TIMER_COUNT = 500000;
//
//    public static final int MAX_TIME_OUT_EVENT_PER_TIME = 2000;

    private static final Log log = LogFactory.getLog(Reactor.class);

    // bug�ȼ�
    private boolean jvmBug0;
    private boolean jvmBug1;

    private final int reactorIndex;

    private final SelectorManager selectorManager;

    // bug��������
    private final AtomicInteger jvmBug = new AtomicInteger(0);

    // ��һ�η���bug��ʱ��
    private long lastJVMBug;

    private volatile Selector selector;

    private final NioController controller;

    private final Configuration configuration;

    private final AtomicBoolean wakenUp = new AtomicBoolean(false);

    /**
     * ע����¼��б� LinkedTransferQueue���ݵĴ洢ʱ��Խ��Խ��ǰ
     */
    private final Queue<Object[]> register = new LinkedTransferQueue<Object[]>();

    private final TimerRefQueue timerQueue = new TimerRefQueue();

    /**
     * ��¼cancel��key��Ŀ�����ﱾ����AtomicInteger���������ǲ�׷����ȫ��ȷ�Ŀ��ƣ�ֻ��һ��Ԥ���ֶ�
     */
    private volatile int cancelledKeys;

    // cancel keys�ĸ�����ֵ�����������ֵ����һ��selectNowһ�������
    static final int CLEANUP_INTERVAL = 256;

    /**
     * ��ʱʱ��Ķ����
     *
     * ���ȶ�����ʽ ʵ�ֵ� Timer��
     *
     * �ڲ��Զ�����Ԫ��compare�����Զ�����peek�õ�һ����ɾ��poll�õ�һ��ɾ�������̰߳�ȫ
     */
    private final PriorityQueue<TimerRef> timerHeap = new PriorityQueue<TimerRef>();
    /**
     * ִ��select��ʱ�仺��
     */
    private volatile long timeCache;

    private final Lock gate = new ReentrantLock();

    private volatile int selectTries = 0;

    private long nextTimeout = 0;//��һ��session�ĳ�ʱ��û��select�����

    private long lastMoveTimestamp = 0; // �ϴδ�timerQueue��Ǩ��timerHeap��ʱ���


    Reactor(final SelectorManager selectorManager, final Configuration configuration, final int index)
            throws IOException {
        super();
        this.reactorIndex = index;
        this.selectorManager = selectorManager;
        this.controller = selectorManager.getController();
        this.selector = SystemUtils.openSelector();
        this.configuration = configuration;
        //����reactor�̵߳�����
        this.setName("notify-remoting-reactor-" + index);
    }


    final Selector getSelector() {
        return this.selector;
    }

    public int getReactorIndex() {
        return this.reactorIndex;
    }


    /**
     * ȡ����ĳ�ʱʱ���ʱ��
     * 
     * @return
     */
    private long timeoutNext() {
        long selectionTimeout = TIMEOUT_THRESOLD;
        TimerRef timerRef = this.timerHeap.peek();//�������ȶ���
        while (timerRef != null && timerRef.isCanceled()) { //Ѱ��Timer������δȡ����
            this.timerHeap.poll();
            timerRef = this.timerHeap.peek();
        }
        if (timerRef != null) {
            final long now = this.getTime();//��ȡʱ�仺��
            // �Ѿ����¼���ʱ������-1��������select����ʱ����ʱ
            if (timerRef.getTimeoutTimestamp() < now) {
                selectionTimeout = -1L;
            }
            else {
                selectionTimeout = timerRef.getTimeoutTimestamp() - now;
            }
        }
        return selectionTimeout;
    }


    /**
     * Select���ɷ��¼�
     */
    @Override
    public void run() {
        //��reactor������֪ͨ��reactor׼����
        this.selectorManager.notifyReady();
        //�����������������selector�Ǵ�״̬
        while (this.selectorManager.isStarted() && this.selector.isOpen()) {
            try {
                this.cancelledKeys = 0;//cancel��key������0
                //selectǰ��Ҫ���Ĵ���
                this.beforeSelect();

                long before = -1;
                if (this.isNeedLookingJVMBug()) { //�����linuxƽ̨����jdk<1.6.4��Ҫ����bug
                    before = System.currentTimeMillis();
                }
                // ��ȡ����Ҫ�����Timer(��ʱ����)�ĵȴ�ʱ��
                long wait = this.timeoutNext();
                //��sessionʱ���˵��session�����ţ����Եȴ�select����һ��ѯ������ε����timer
                //��sessionʱ��С��˵��session�����ˣ����������Ͻ��ȴ�select�����session״̬
                //Ҳ����˭�ȴ�ʱ��С��˭Խ����
                if (this.nextTimeout > 0 && this.nextTimeout < wait) {
                    wait = this.nextTimeout;
                }
                // ���ʱ�仺��
                this.timeCache = 0;
                this.wakenUp.set(false);
                final int selected = this.select(wait);
                if (selected == 0) { //����0˵��û�л�ȡ��
                    /**
                     * �鿴�Ƿ���BUG���μ�http://bugs.sun.com/bugdatabase /view_bug
                     * .do?bug_id=6403933
                     */
                    if (before != -1) {
                        this.lookJVMBug(before, selected, wait);
                    }
                    this.selectTries++;//���Դ���+1
                    // ��������Ƿ���ڻ���idle�������´�timeoutʱ��
                    this.nextTimeout = this.checkSessionTimeout();
                }
                else {
                    this.selectTries = 0;
                }
                // ����ʱ�䣬��ô�����Ĵ���ʱ�����timer�Ļ�ȡ��ʱ�䶼�ǻ����ʱ�䣬���Ϳ���
                this.timeCache = this.getTime(); //����Ѿ�������û����ʱ�䣬���û��ȥ��ǰʱ��
                this.processTimeout();//����ʱ
                this.processSelectedKeys();//��������key��δ������key���ɷ�������key���ڵ��¼������δ������key��session״̬
            }
            catch (final ClosedSelectorException e) {
                break;
            }
            catch (final Exception e) {
                log.error("Reactor select error", e);
                if (this.selector.isOpen()) {
                    continue;
                }
                else {
                    break;
                }
            }
        }
        // ---------------�ߵ������ʾ�ر�-----
        //���selectorManager�رջ���selector�ر�
        //��ر�channel�����selector���ر�selector
        if (this.selector != null) {
            if (this.selector.isOpen()) {
                try {
                    this.controller.closeChannel(this.selector);
                    this.selector.selectNow();
                    this.selector.close();
                }
                catch (final IOException e) {
                    this.controller.notifyException(e);
                    log.error("stop reactor error", e);
                }
            }
        }

    }


    private void processTimeout() {
        if (!this.timerHeap.isEmpty()) {
            final long now = this.getTime();
            TimerRef timerRef = null;
            while ((timerRef = this.timerHeap.peek()) != null) {
                if (timerRef.isCanceled()) {//�����ȡ���ˣ��Ǿ�ɾ�����Ԫ�أ���������
                    this.timerHeap.poll();
                    continue;
                }
                // û�г�ʱ��break������Ϊ�ǻ���priorityQueue����һ���϶������ģ���һ��û��ʱ����Ŀ϶���û��ʱ
                if (timerRef.getTimeoutTimestamp() > now) {
                    break;
                }
                // �����ȶ������Ƴ�������
                this.controller.onTimeout(this.timerHeap.poll());
            }
        }
    }


    private Set<SelectionKey> processSelectedKeys() throws IOException {
        final Set<SelectionKey> selectedKeys = this.selector.selectedKeys();
        this.gate.lock();
        try {
            //selectedKeys������key, this.selector.keys()����ע���key
            this.postSelect(selectedKeys, this.selector.keys());
            //ͨ��������key�ɷ��¼�
            this.dispatchEvent(selectedKeys);
        }
        finally {
            this.gate.unlock();
        }
        //���ȡ����key
        this.clearCancelKeys();
        return selectedKeys;
    }


    private void clearCancelKeys() throws IOException {
        if (this.cancelledKeys > CLEANUP_INTERVAL) {
            final Selector selector = this.selector;
            selector.selectNow();
            this.cancelledKeys = 0;
        }
    }


    private int select(final long wait) throws IOException {
        // ������Ȼ���о��������ģ�ֻ�ܾ�������
        if (wait > 0 && !this.wakenUp.get()) {
            return this.selector.select(wait);
        }
        else {
            return this.selector.selectNow();
        }
    }

    //��ȡʱ�仺��
    public long getTime() {
        final long timeCache = this.timeCache;
        if (timeCache > 0) {
            return timeCache;
        }
        else {
            return System.currentTimeMillis();
        }
    }


    /**
     * ���붨ʱ�������ص�ǰʱ��
     * 
     * @param timeout
     * @param runnable
     */
    public void insertTimer(final TimerRef timerRef) {
        if (timerRef.getTimeout() > 0 && timerRef.getRunnable() != null && !timerRef.isCanceled()) {
            final long now = this.getTime();
            final long timestamp = now + timerRef.getTimeout();
            timerRef.setTimeoutTimestamp(timestamp);
            timerRef.addTimestamp = now;
            this.timerQueue.add(timerRef);
        }
    }


    private boolean lookJVMBug(final long before, final int selected, final long wait) throws IOException {
        boolean seeing = false;
        final long now = System.currentTimeMillis();
        /**
         * Bug�ж�����,(1)selectΪ0 (2)select����ʱ��С��ĳ����ֵ (3)���߳��ж����� (4)��wakenup����
         */
        if (JVMBUG_THRESHHOLD > 0 && selected == 0 && wait > JVMBUG_THRESHHOLD && now - before < wait / 4
                && !this.wakenUp.get() /* waken up */
                && !Thread.currentThread().isInterrupted()/* Interrupted */) {
            this.jvmBug.incrementAndGet();
            // ���صȼ�1�����´���selector
            if (this.jvmBug.get() >= JVMBUG_THRESHHOLD2) {
                this.gate.lock();
                try {
                    this.lastJVMBug = now;
                    log.warn("JVM bug occured at " + new Date(this.lastJVMBug)
                            + ",http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6403933,reactIndex="
                            + this.reactorIndex);
                    if (this.jvmBug1) {
                        log.debug("seeing JVM BUG(s) - recreating selector,reactIndex=" + this.reactorIndex);
                    }
                    else {
                        this.jvmBug1 = true;
                        log.info("seeing JVM BUG(s) - recreating selector,reactIndex=" + this.reactorIndex);
                    }
                    seeing = true;
                    // �����µ�selector
                    final Selector new_selector = SystemUtils.openSelector();

                    for (final SelectionKey k : this.selector.keys()) {
                        if (!k.isValid() || k.interestOps() == 0) {
                            continue;
                        }

                        final SelectableChannel channel = k.channel();
                        final Object attachment = k.attachment();
                        // ��������Ч������interestOps>0��channel����ע��
                        channel.register(new_selector, k.interestOps(), attachment);
                    }

                    this.selector.close();
                    this.selector = new_selector;

                }
                finally {
                    this.gate.unlock();
                }
                this.jvmBug.set(0);

            }
            else if (this.jvmBug.get() == JVMBUG_THRESHHOLD || this.jvmBug.get() == JVMBUG_THRESHHOLD1) {
                // BUG���صȼ�0��ȡ������interestedOps==0��key
                if (this.jvmBug0) {
                    log.debug("seeing JVM BUG(s) - cancelling interestOps==0,reactIndex=" + this.reactorIndex);
                }
                else {
                    this.jvmBug0 = true;
                    log.info("seeing JVM BUG(s) - cancelling interestOps==0,reactIndex=" + this.reactorIndex);
                }
                this.gate.lock();
                seeing = true;
                try {
                    for (final SelectionKey k : this.selector.keys()) {
                        if (k.isValid() && k.interestOps() == 0) {
                            k.cancel();
                        }
                    }
                }
                finally {
                    this.gate.unlock();
                }
            }
        }
        else {
            this.jvmBug.set(0);
        }
        return seeing;
    }


    private boolean isNeedLookingJVMBug() {
        return SystemUtils.isLinuxPlatform() && !SystemUtils.isAfterJava6u4Version();
    }


    final void dispatchEvent(final Set<SelectionKey> selectedKeySet) {
        final Iterator<SelectionKey> it = selectedKeySet.iterator();
        boolean skipOpRead = false; // �Ƿ�������
        //����������key
        while (it.hasNext()) {
            final SelectionKey key = it.next();
            it.remove();//��ȡ���Set��ɾ��
            if (!key.isValid()) {//������Ϸ�
                if (key.attachment() != null) { //�����session
                    this.controller.closeSelectionKey(key); //�ص�session
                }
                else {
                    key.cancel(); //���û��session���ͷ�key���ڵ�channel
                }
                continue;
            }
            try {
                //�����accept
                if (key.isAcceptable()) {
                    this.controller.onAccept(key);//ͳ��accpet��
                    continue; //����������key
                }
                //�����д
                if ((key.readyOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
                    // Remove write interest
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                    this.controller.onWrite(key);
                    //�����֧�ֲ�����д����ô��д��������
                    if (!this.controller.isHandleReadWriteConcurrently()) {
                        skipOpRead = true;
                    }
                }
                //�����
                if (!skipOpRead && (key.readyOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
                    // �Ƴ���read����Ȥ
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                    // �Ƿ񳬹���������
                    if (!this.controller.getStatistics().isReceiveOverFlow()) {
                        this.controller.onRead(key);// û�г����������ƣ��ɷ���
                        continue;
                    }
                    else {
                        //�������������ƣ����ɷ��ȣ�������
                        key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                    }

                }
                //��������� -----------�ͻ���ʹ��-------
                if ((key.readyOps() & SelectionKey.OP_CONNECT) == SelectionKey.OP_CONNECT) {
                    this.controller.onConnect(key);
                    continue;
                }

            }
            catch (final RejectedExecutionException e) {
                // �����̳߳ط�æ���쳣�����ر�����
                if (key.attachment() instanceof AbstractNioSession) {
                    ((AbstractSession) key.attachment()).onException(e);//session exception
                }
                this.controller.notifyException(e); //controller֪ͨexception
                if (this.selector.isOpen()) {
                    continue;
                }
                else {
                    break;
                }
            }
            catch (final CancelledKeyException e) {
                // ignore
            }
            catch (final Exception e) {
                if (key.attachment() instanceof AbstractNioSession) {
                    ((AbstractSession) key.attachment()).onException(e);
                }
                this.controller.closeSelectionKey(key);
                this.controller.notifyException(e);
                log.error("Reactor dispatch events error", e);
                if (this.selector.isOpen()) {
                    continue;
                }
                else {
                    break;
                }
            }
        }
    }

    /**
     * ���session�Ƿ�ʱ
     *
     * ��ȡselector����������session�����ʱʱ�䣨ʧЧ�����У���������е����
     */
    private final long checkSessionTimeout() {
        long nextTimeout = 0;
        //��������˼��session��ʱ������ʱ��
        if (this.configuration.getCheckSessionTimeoutInterval() > 0) {
            this.gate.lock();
            try {
                //������Դ���ת���ms>���õ�session��ʱ�������---> Ҳ���ǵ��˼���ڣ�����Ƿ�ʱ
                if (this.selectTries * 1000 >= this.configuration.getCheckSessionTimeoutInterval()) {
                    nextTimeout = this.configuration.getCheckSessionTimeoutInterval();
                    for (final SelectionKey key : this.selector.keys()) {
                        // ����Ƿ�expired����idle
                        if (key.attachment() != null) {//selectionKey������Ϊ��
                            //���ʧЧ����ʧЧ�ļ��ʱ�䣬������о��ǿ��еļ��ʱ��
                            final long n = this.checkExpiredIdle(key, this.getSessionFromAttchment(key));
                            nextTimeout = n < nextTimeout ? n : nextTimeout;
                        }
                    }
                    this.selectTries = 0;
                }
            }
            finally {
                this.gate.unlock();
            }
        }
        return nextTimeout;
    }


    private final Session getSessionFromAttchment(final SelectionKey key) {
        if (key.attachment() instanceof Session) {
            return (Session) key.attachment();
        }
        return null;
    }


    final void registerSession(final Session session, final EventType event) {
        final Selector selector = this.selector;
        if (this.isReactorThread() && selector != null) {
            this.dispatchSessionEvent(session, event, selector);
        }
        else {
            this.register.offer(new Object[] { session, event });
            this.wakeup();
        }
    }


    private final boolean isReactorThread() {
        return Thread.currentThread() == this;
    }


    final void beforeSelect() throws IOException {
        this.controller.checkStatisticsForRestart();
        //����channel����session��ע��
        this.processRegister();
        //�����ƶ���ʱ��
        this.processMoveTimer();
        //���cancelkey, ������ֵ
        this.clearCancelKeys();
    }


    private void processMoveTimer() {
        //��ȡ����ĵ�ǰʱ��
        final long now = this.getTime();
        // �ﵽ�ƶ���������ڣ���now-last��������timer���в�Ϊ�գ��ͱ���timer����
        if (now - this.lastMoveTimestamp >= TIMEOUT_THRESOLD && !this.timerQueue.isEmpty()) {
            //���¼��ʱ��
            this.lastMoveTimestamp = now;
            //������ʱ���¼�����
            this.timerQueue.iterateQueue(new TimerQueueVisitor(now));
        }
    }

    //����洢��ע���¼�
    private final void processRegister() {
        Object[] object = null;
        while ((object = this.register.poll()) != null) {
            switch (object.length) {
            case 2:
                //�ɷ�session�¼�
                this.dispatchSessionEvent((Session) object[0], (EventType) object[1], this.selector);
                break;
            case 3:
                //ע��channel
                this.registerChannelNow((SelectableChannel) object[0], (Integer) object[1], object[2], this.selector);
                break;
            }
        }
    }


    Configuration getConfiguration() {
        return this.configuration;
    }


    private final void dispatchSessionEvent(final Session session, final EventType event, final Selector selector) {
        if (EventType.REGISTER.equals(event)) {
            this.controller.registerSession(session);
        }
        else if (EventType.UNREGISTER.equals(event)) {
            this.controller.unregisterSession(session);
            this.unregisterChannel(((NioSession) session).channel());
        }
        else {
            ((NioSession) session).onEvent(event, selector);
        }
    }


    /**
     *
     * ���δ������key��Ӧ��session�Ƿ�ʱ�� ����Ҳûʲô��
     *
     * @param selectedKeys  ������key
     * @param allKeys       ���е�key
     */
    final void postSelect(final Set<SelectionKey> selectedKeys, final Set<SelectionKey> allKeys) {
        if (this.controller.getSessionTimeout() > 0 || this.controller.getSessionIdleTimeout() > 0) {
            for (final SelectionKey key : allKeys) {
                // û�д�����key����Ƿ�ʱ����idle
                if (!selectedKeys.contains(key)) {
                    if (key.attachment() != null) {
                        this.checkExpiredIdle(key, this.getSessionFromAttchment(key));
                    }
                }
            }
        }
    }


    /**
     * ���session�Ƿ�ʧЧ���߿��У�keyûʲô��
     * @param key
     * @param session
     * @return
     */
    private long checkExpiredIdle(final SelectionKey key, final Session session) {
        if (session == null) {
            return 0;
        }
        long nextTimeout = 0;
        boolean expired = false;
        //ʧЧ�Ϳ��У� �϶�ʧЧ��ʱ���ȴ������ȼ����
        //���controller������session��ʱ
        if (this.controller.getSessionTimeout() > 0) {
            //keyûʲô�ã� ���session�Ƿ�ʧЧ
            expired = this.checkExpired(key, session);
            nextTimeout = this.controller.getSessionTimeout();
        }
        //���controller������session���г�ʱ������sessionû��ʧЧ
        if (this.controller.getSessionIdleTimeout() > 0 && !expired) {
            //���session�Ƿ����
            this.checkIdle(session);
            nextTimeout = this.controller.getSessionIdleTimeout();
        }
        return nextTimeout;
    }


    private final void checkIdle(final Session session) {
        if (this.controller.getSessionIdleTimeout() > 0) {
            if (session.isIdle()) {
                ((NioSession) session).onEvent(EventType.IDLE, this.selector);
            }
        }
    }


    private final boolean checkExpired(final SelectionKey key, final Session session) {
        //������ھ���session�����ʱ��>��ʱʱ�䣬��ʧЧ
        if (session.isExpired()) {
            //session����ʧЧ�¼�
            ((NioSession) session).onEvent(EventType.EXPIRED, this.selector);
            return true;
        }
        return false;
    }

    final void registerChannel(final SelectableChannel channel, final int ops, final Object attachment) {
        final Selector selector = this.selector;
        if (this.isReactorThread() && selector != null) { //������ע�������¼�ʱ����reactor�߳�
            this.registerChannelNow(channel, ops, attachment, selector);
        }
        else {//����ʱ��ע��acceptʱ�������̲߳���reactor�߳�
            //�����ɹ���reactor�߳�ִ��
            this.register.offer(new Object[] { channel, ops, attachment });
            this.wakeup();
        }

    }

    //ȡ��ע��channel
    final void unregisterChannel(final SelectableChannel channel) {
        try {
            final Selector selector = this.selector;
            if (selector != null) {
                if (channel != null) {
                    //��ȡ���channelע���key��Ȼ��ȡ����������¼ȡ����key����
                    final SelectionKey key = channel.keyFor(selector);
                    if (key != null) {
                        key.cancel();
                        this.cancelledKeys++;
                    }
                }
            }
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        }
        catch (final Throwable t) {
            // ignore
        }
        this.wakeup();
    }

    private void registerChannelNow(final SelectableChannel channel, final int ops, final Object attachment,
            final Selector selector) {
        this.gate.lock();
        try {
            if (channel.isOpen()) {
                channel.register(selector, ops, attachment);
            }
        }
        catch (final ClosedChannelException e) {
            log.error("Register channel error", e);
            this.controller.notifyException(e);
        }
        finally {
            this.gate.unlock();
        }
    }


    final void wakeup() {
        if (this.wakenUp.compareAndSet(false, true)) {
            final Selector selector = this.selector;
            if (selector != null) {
                selector.wakeup();
            }
        }
    }


    final void selectNow() throws IOException {
        final Selector selector = this.selector;
        if (selector != null) {
            selector.selectNow();
        }
    }
}