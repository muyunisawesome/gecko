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

import com.taobao.gecko.core.command.ResponseCommand;

import java.util.concurrent.ThreadPoolExecutor;


/**
 * 单个分组的单个连接的应答回调监听器
 *
 * @author boyan
 * @since 1.0, 2009-12-15 下午04:13:17
 */

public interface SingleRequestCallBackListener {

    /**
     * 处理应答
     *
     * @param responseCommand 应答命令
     * @param conn            应答连接
     */
    void onResponse(ResponseCommand responseCommand, Connection conn);


    /**
     * 异常发生的时候回调
     *
     * @param e
     */
    void onException(Exception e);


    /**
     * onResponse回调执行的线程池
     *
     * @return
     */
    ThreadPoolExecutor getExecutor();
}