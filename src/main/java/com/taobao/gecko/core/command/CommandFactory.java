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
package com.taobao.gecko.core.command;

import com.taobao.gecko.core.command.kernel.BooleanAckCommand;
import com.taobao.gecko.core.command.kernel.HeartBeatRequestCommand;


/**
 * 协议工厂类，任何协议的实现都必须实现此工厂接口，提供创建BooleanAckCommand和HeartBeatRequestCommand的方法
 * 
 * @author boyan
 * 
 */
public interface CommandFactory {
    /**
     * 创建特定于协议的BooleanAckCommand
     * 
     * @param request
     *            请求头
     * @param responseStatus
     *            响应状态
     * @param errorMsg
     *            错误信息
     * @return
     */
    BooleanAckCommand createBooleanAckCommand(CommandHeader request, ResponseStatus responseStatus,
            String errorMsg);


    /**
     * 创建特定于协议的心跳命令
     * 
     * @return
     */
    HeartBeatRequestCommand createHeartBeatCommand();

}