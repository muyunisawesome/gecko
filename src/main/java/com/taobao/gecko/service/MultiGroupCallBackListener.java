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

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * 多分组所有请求的回调（应答）监听器
 *
 * @author boyan
 * @since 1.0, 2009-12-15 下午04:41:37
 */

public interface MultiGroupCallBackListener {

    void onResponse(Map<String/* group */, ResponseCommand> groupResponses, Object... args);


    ThreadPoolExecutor getExecutor();
}