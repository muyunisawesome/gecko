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
 *Copyright [2008-2009] [dennis zhuang]
 *Licensed under the Apache License, Version 2.0 (the "License");
 *you may not use this file except in compliance with the License.
 *You may obtain a copy of the License at
 *             http://www.apache.org/licenses/LICENSE-2.0
 *Unless required by applicable law or agreed to in writing,
 *software distributed under the License is distributed on an "AS IS" BASIS,
 *WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *either express or implied. See the License for the specific language governing permissions and limitations under the License
 */

package com.taobao.gecko.core.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import com.taobao.gecko.core.config.Configuration;
import com.taobao.gecko.core.core.CodecFactory;
import com.taobao.gecko.core.core.EventType;
import com.taobao.gecko.core.core.Handler;
import com.taobao.gecko.core.core.ServerController;
import com.taobao.gecko.core.core.Session;
import com.taobao.gecko.core.core.impl.StandardSocketOption;
import com.taobao.gecko.core.nio.impl.SocketChannelController;


/**
 * Controller for tcp server
 * 
 * @author dennis
 */
public class TCPController extends SocketChannelController implements ServerController {

    private ServerSocketChannel serverSocketChannel;

    /**
     * 积压队列
     * Accept backlog queue size
     */
    private int backlog = 500; // default 500


    public int getBacklog() {
        return this.backlog;
    }


    public void setBacklog(final int backlog) {
        if (this.isStarted()) {
            throw new IllegalStateException();
        }
        if (backlog < 0) {
            throw new IllegalArgumentException("backlog<0");
        }
        this.backlog = backlog;
    }


    public TCPController() {
        super();
    }


    public TCPController(final Configuration configuration) {
        super(configuration, null, null);

    }


    public TCPController(final Configuration configuration, final CodecFactory codecFactory) {
        super(configuration, null, codecFactory);
    }


    public TCPController(final Configuration configuration, final Handler handler, final CodecFactory codecFactory) {
        super(configuration, handler, codecFactory);
    }

    private int connectionTime, latency, bandwidth;


    public void setPerformancePreferences(final int connectionTime, final int latency, final int bandwidth) {
        this.connectionTime = connectionTime;
        this.latency = latency;
        this.bandwidth = bandwidth;
    }


    @Override
    protected void doStart() throws IOException {
        //打开severSocketChannel
        this.serverSocketChannel = ServerSocketChannel.open();
        //设置socket超时时间
        this.serverSocketChannel.socket().setSoTimeout(this.soTimeout);
        //设置socket执行性能的一些参数，连接时间，延迟，带宽
        if (this.connectionTime != 0 || this.latency != 0 || this.bandwidth != 0) {
            this.serverSocketChannel.socket().setPerformancePreferences(this.connectionTime, this.latency,
                this.bandwidth);
        }
        //设置severSocketChannel无阻塞
        this.serverSocketChannel.configureBlocking(false);

        //SO_REUSEADDR是让端口释放后立即就可以被再次使用。
        if (this.socketOptions.get(StandardSocketOption.SO_REUSEADDR) != null) {
            this.serverSocketChannel.socket().setReuseAddress(
                StandardSocketOption.SO_REUSEADDR.type()
                    .cast(this.socketOptions.get(StandardSocketOption.SO_REUSEADDR)));
        }
        //SO_RCVBUF 设置socket接收缓冲器大小
        if (this.socketOptions.get(StandardSocketOption.SO_RCVBUF) != null) {
            this.serverSocketChannel.socket().setReceiveBufferSize(
                StandardSocketOption.SO_RCVBUF.type().cast(this.socketOptions.get(StandardSocketOption.SO_RCVBUF)));

        }
        //socket绑定ip并设置积压大小
        if (this.localSocketAddress != null) {//如果设置了ip就用绑定该ip
            this.serverSocketChannel.socket().bind(this.localSocketAddress, this.backlog);
        }
        else { //如果没有设置ip，那就用本地ip端口号0绑定
            this.serverSocketChannel.socket().bind(new InetSocketAddress("localhost", 0), this.backlog);
        }
        //设置保存最终成功绑定的ip
        this.setLocalSocketAddress((InetSocketAddress) this.serverSocketChannel.socket().getLocalSocketAddress());
        //注册serverChannel到reactor[0]上，监听accept
        this.selectorManager.registerChannel(this.serverSocketChannel, SelectionKey.OP_ACCEPT, null);
    }


    @Override
    public void onAccept(final SelectionKey selectionKey) throws IOException {
        // 再次确认server是否关闭，Server已经关闭，直接返回
        if (!this.serverSocketChannel.isOpen()) {
            selectionKey.cancel();
            return;
        }
        SocketChannel sc = null;
        try {
            sc = this.serverSocketChannel.accept();//获取收到的通信socketChannel
            if (sc != null) {
                this.configureSocketChannel(sc);//配置socketChannel
                final Session session = this.buildSession(sc);//创建session
                // enable read 启动读
                this.selectorManager.registerSession(session, EventType.ENABLE_READ);
                session.start();
                super.onAccept(selectionKey); // for statistics
            }
            else {
                log.debug("Accept fail");
            }
        }
        catch (final IOException e) {
            this.closeAcceptChannel(selectionKey, sc);
            this.notifyException(e);
        }
    }


    /**
     * 
     * @param sk
     * @param sc
     * @throws IOException
     * @throws SocketException
     */
    private void closeAcceptChannel(final SelectionKey sk, final SocketChannel sc) throws IOException, SocketException {
        if (sk != null) {
            sk.cancel();
        }
        if (sc != null) {
            sc.socket().setSoLinger(true, 0); // await TIME_WAIT status
            sc.socket().shutdownOutput();
            sc.close();
        }
    }


    @Override
    protected void stop0() throws IOException {
        this.closeServerChannel();
        super.stop0();
    }


    public void closeChannel(final Selector selector) throws IOException {
        this.closeServerChannel();
    }


    private void closeServerChannel() throws IOException {
        if (this.serverSocketChannel != null && this.serverSocketChannel.isOpen()) {
            this.serverSocketChannel.close();
        }
    }


    public void unbind() throws IOException {
        this.stop();
    }

}