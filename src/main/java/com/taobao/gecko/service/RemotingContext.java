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

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import com.taobao.gecko.core.command.CommandFactory;
import com.taobao.gecko.service.config.BaseConfig;


/**
 * 
 * Remoting的全局上下文
 * 
 * @author boyan
 * 
 * @since 1.0, 2009-12-15 下午03:54:01
 */

public interface RemotingContext {

    /**
     * 添加连接到指定分组
     * 
     * @param group
     * @param connection
     * @return
     */
    boolean addConnectionToGroup(String group, Connection connection);


    /**
     * 获取当前的网络层配置对象
     * 
     * @return
     */
    BaseConfig getConfig();


    /**
     * 添加到默认分组
     * 
     * @param connection
     */
    void addConnection(Connection connection);


    /**
     * 从默认分组移除
     * 
     * @param connection
     */
    void removeConnection(Connection connection);


    /**
     * 根据Group得到connection集合
     * 
     * @param group
     * @return
     */
    List<Connection> getConnectionsByGroup(String group);


    /**
     * 移除连接
     * 
     * @param group
     * @param connection
     * @return
     */
    boolean removeConnectionFromGroup(String group, Connection connection);


    Object getAttribute(Object key, Object defaultValue);


    Object getAttribute(Object key);


    Set<Object> getAttributeKeys();


    Object setAttribute(Object key, Object value);


    Object setAttributeIfAbsent(Object key, Object value);


    Object setAttributeIfAbsent(Object key);


    void awaitGroupConnectionsEmpty(String group, long time) throws InterruptedException, TimeoutException;


    /**
     * 获取当前客户端或者服务器的所有分组名称
     * 
     * @return
     */
    Set<String> getGroupSet();


    /**
     * 获取当前使用的协议工厂
     * 
     * @return
     */
    CommandFactory getCommandFactory();

}