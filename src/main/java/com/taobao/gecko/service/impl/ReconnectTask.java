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

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * 
 * 重连任务
 * 
 * @author boyan
 * 
 * @since 1.0, 2009-12-16 下午05:46:58
 */

public final class ReconnectTask {
    private Throwable lastException; //上次执行的异常
    private final InetSocketAddress remoteAddress;  //要连接的远程地址
    private volatile boolean done; //是否已执行过
    private final Set<String> groupSet; //本地任务的 所有需要重连的任务
    private final AtomicInteger counter = new AtomicInteger(); //执行次数


    public ReconnectTask(Set<String> groupSet, InetSocketAddress remoteAddress) {
        super();
        this.groupSet = groupSet;
        this.remoteAddress = remoteAddress;
    }

    //执行次数+1
    public long increaseRetryCounterAndGet() {
        return this.counter.incrementAndGet();
    }

    public Set<String> getGroupSet() {
        return this.groupSet;
    }


    public Throwable getLastException() {
        return this.lastException;
    }


    public void setLastException(Throwable lastException) {
        this.lastException = lastException;
    }


    public boolean isDone() {
        return this.done;
    }


    public void setDone(boolean done) {
        this.done = done;
    }


    public InetSocketAddress getRemoteAddress() {
        return this.remoteAddress;
    }

}