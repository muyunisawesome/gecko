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
package com.taobao.gecko.core.nio;

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
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import com.taobao.gecko.core.nio.impl.TimerRef;


/**
 * SelectionKey������
 * 
 * @see com.taobao.gecko.core.core.impl.AbstractController
 * @author dennis
 * 
 */
 public interface SelectionKeyHandler {
     void onAccept(SelectionKey sk) throws IOException;


     void closeSelectionKey(SelectionKey key);


     void onWrite(SelectionKey key);


     void onRead(SelectionKey key);


     void onTimeout(TimerRef timerRef);


     void onConnect(SelectionKey key) throws IOException;


     void closeChannel(Selector selector) throws IOException;
}