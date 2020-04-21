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
package com.taobao.gecko.core.statistics;

/**
 * Í³¼ÆÆ÷
 * 
 * 
 * 
 * @author boyan
 * 
 * @since 1.0, 2009-12-16 ÏÂÎç06:19:27
 */
 public interface Statistics {

     void start();


     void stop();


     double getReceiveBytesPerSecond();


     double getSendBytesPerSecond();


     void statisticsProcess(long n);


     long getProcessedMessageCount();


     double getProcessedMessageAverageTime();


     void statisticsRead(long n);


     void statisticsWrite(long n);


     long getRecvMessageCount();


     long getRecvMessageTotalSize();


     long getRecvMessageAverageSize();


     long getWriteMessageTotalSize();


     long getWriteMessageCount();


     long getWriteMessageAverageSize();


     double getRecvMessageCountPerSecond();


     double getWriteMessageCountPerSecond();


     void statisticsAccept();


     double getAcceptCountPerSecond();


     long getStartedTime();


     void reset();


     void restart();


     boolean isStatistics();


     void setReceiveThroughputLimit(double receiveThroughputLimit);


    /**
     * Check session if receive bytes per second is over flow controll
     * 
     * @return
     */
     boolean isReceiveOverFlow();


     double getReceiveThroughputLimit();

}