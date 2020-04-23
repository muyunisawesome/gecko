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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import com.taobao.gecko.core.util.MBeanUtils;
import com.taobao.gecko.service.Connection;


/**
 * 
 * 分组管理器,管理分组到连接的映射关系
 * 
 * @author boyan
 * 
 * @since 1.0, 2009-12-15 下午02:38:09
 */

public class GroupManager implements GroupManagerMBean {

    //group到connection的映射，one-more关系
    private final ConcurrentHashMap<String/* group */, List<Connection>> group2ConnectionMap =
            new ConcurrentHashMap<String, List<Connection>>();


    public GroupManager() {
        MBeanUtils.registerMBeanWithIdPrefix(this, null);
    }

    /**
     * 添加connection到指定group
     */
    public boolean addConnection(final String group, final Connection connection) {
        synchronized (group.intern()) {//返回字符串对象的规范化表示形式, 于任意两个字符串 s 和 t，当且仅当 s.equals(t) 为 true 时，s.intern() == t.intern() 才为 true
            //一个字符串，内容与此字符串相同，但一定取自具有唯一字符串的池。
            List<Connection> connections = this.group2ConnectionMap.get(group);
            if (connections == null) {
                // 采用copyOnWrite主要是考虑遍历connection的操作会多一些，在发送消息的时候
                connections = new CopyOnWriteArrayList<Connection>();
                final List<Connection> oldList = this.group2ConnectionMap.putIfAbsent(group, connections);
                if (oldList != null) {
                    connections = oldList;
                }
            }
            // 已经包含，即认为添加成功
            if (connections.contains(connection)) {
                return true;
            }
            else {
                ((DefaultConnection) connection).addGroup(group);
                return connections.add(connection);
            }
        }
    }

    /**
     * 删除指定group下的connection
     */
    public boolean removeConnection(final String group, final Connection connection) {
        synchronized (group.intern()) {
            final List<Connection> connections = this.group2ConnectionMap.get(group);
            if (connections == null) {
                return false;
            }
            else {
                //连接管理器删除group下的conncetion，同时要删除connection中所述group的set
                final boolean result = connections.remove(connection);
                if (result) {
                    ((DefaultConnection) connection).removeGroup(group);
                }
                if (connections.isEmpty()) {
                    this.group2ConnectionMap.remove(group);
                    group.intern().notifyAll();
                }
                return result;
            }
        }

    }

    /**
     * 根据group获取connection数量
     */
    public int getGroupConnectionCount(final String group) {
        synchronized (group.intern()) {
            final List<Connection> connections = this.group2ConnectionMap.get(group);
            if (connections == null) {
                return 0;
            }
            else {

                return connections.size();
            }
        }
    }

    /**
     * 获取groupManager里的所有group
     */
    public Set<String> getGroupSet() {
        return this.group2ConnectionMap.keySet();
    }

    public void awaitGroupConnectionsEmpty(String group, long time) throws InterruptedException, TimeoutException {
        long start = System.currentTimeMillis();
        synchronized (group.intern()) {
            while (this.group2ConnectionMap.get(group) != null) {
                if (System.currentTimeMillis() - start > time) {
                    throw new TimeoutException("Timeout to wait connections closed.");
                }
                group.intern().wait(time);
            }
        }
    }

    @Override
    public Map<String, Set<String>> getGroupConnectionInfo() {
        final Map<String, Set<String>> result = new HashMap<String, Set<String>>();
        for (final Map.Entry<String, List<Connection>> entry : this.group2ConnectionMap.entrySet()) {
            final Set<String> set = new HashSet<String>();
            if (entry.getValue() != null) {
                for (final Connection conn : entry.getValue()) {
                    set.add(conn.toString());
                }
            }
            result.put(entry.getKey(), set);
        }
        return result;
    }

    /**
     *  获取指定group下的所有connection
     */
    public List<Connection> getConnectionsByGroup(final String group) {
        return this.group2ConnectionMap.get(group);
    }

    public void clear() {
        this.group2ConnectionMap.clear();
    }
}