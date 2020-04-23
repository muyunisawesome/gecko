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
package com.taobao.gecko.core.core;

import java.util.Queue;

import com.taobao.gecko.core.statistics.Statistics;


/**
 * 连接配置
 * 
 * 
 * 
 * @author boyan
 * 
 * @since 1.0, 2009-12-16 下午06:01:37
 */
public class SessionConfig {
    public final Handler handler;//处理器
    public final CodecFactory codecFactory; //编码工厂
    public final Statistics statistics;//统计器
    public final Queue<WriteMessage> queue;//写消息队列
    public final Dispatcher dispatchMessageDispatcher;//消息派发器
    public final boolean handleReadWriteConcurrently;//是否读写并发
    public final long sessionTimeout;//session失效超时
    public final long sessionIdleTimeout;//session空闲超时


    public SessionConfig(final Handler handler, final CodecFactory codecFactory, final Statistics statistics,
            final Queue<WriteMessage> queue, final Dispatcher dispatchMessageDispatcher,
            final boolean handleReadWriteConcurrently, final long sessionTimeout, final long sessionIdelTimeout) {

        this.handler = handler;
        this.codecFactory = codecFactory;
        this.statistics = statistics;
        this.queue = queue;
        this.dispatchMessageDispatcher = dispatchMessageDispatcher;
        this.handleReadWriteConcurrently = handleReadWriteConcurrently;
        this.sessionTimeout = sessionTimeout;
        this.sessionIdleTimeout = sessionIdelTimeout;
    }
}