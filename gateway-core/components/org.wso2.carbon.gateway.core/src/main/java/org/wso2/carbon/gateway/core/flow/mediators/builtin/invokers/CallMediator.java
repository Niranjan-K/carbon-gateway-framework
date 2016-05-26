/*
 * Copyright (c) 2016, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 * <p>
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.gateway.core.flow.mediators.builtin.invokers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.gateway.core.Constants;
import org.wso2.carbon.gateway.core.config.ConfigRegistry;
import org.wso2.carbon.gateway.core.config.ParameterHolder;
import org.wso2.carbon.gateway.core.flow.AbstractMediator;
import org.wso2.carbon.gateway.core.flow.FlowControllerCallback;
import org.wso2.carbon.gateway.core.flow.Invoker;
import org.wso2.carbon.gateway.core.outbound.OutboundEndpoint;
import org.wso2.carbon.gateway.core.util.VariableUtil;
import org.wso2.carbon.messaging.CarbonCallback;
import org.wso2.carbon.messaging.CarbonMessage;
import org.wso2.carbon.messaging.DefaultCarbonMessage;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Stack;

/**
 * Send a Message out from Pipeline to an Outbound Endpoint
 */
public class CallMediator extends AbstractMediator implements Invoker {


    private String outboundEPKey;

    private OutboundEndpoint outboundEndpoint;

    private static final Logger log = LoggerFactory.getLogger(CallMediator.class);

    public CallMediator() {
    }

    public CallMediator(String outboundEPKey) {
        this.outboundEPKey = outboundEPKey;
    }

    public CallMediator(OutboundEndpoint outboundEndpoint) {
        this.outboundEndpoint = outboundEndpoint;
    }

    public void setParameters(ParameterHolder parameterHolder) {
        outboundEPKey = parameterHolder.getParameter("endpointKey").getValue();
    }

    @Override
    public String getName() {
        return "call";
    }

    private void copyProperties(CarbonMessage from, CarbonMessage to) {
        from.getProperties().forEach((k, v) ->
                to.setProperty(k, v));
    }

    private void copyHeaders(CarbonMessage from, CarbonMessage to) {
        from.getHeaders().forEach((k, v) ->
                to.setHeader(k, v));
    }

    @Override
    public boolean receive(CarbonMessage carbonMessage, CarbonCallback carbonCallback)
            throws Exception {

        if (carbonMessage.getProperty("DIRECTION") != null
                && carbonMessage.getProperty("DIRECTION").equals("DIRECTION_RESPONSE")
                    && carbonMessage.getProperty("ORIGINAL_REQUEST") != null) {
            String host = carbonMessage.getProperty("HOST").toString();
            String port = carbonMessage.getProperty("PORT").toString();
            carbonMessage = (CarbonMessage) carbonMessage.getProperty("ORIGINAL_REQUEST");
            Stack<Map<String, Object>> variableStack =
                    (Stack<Map<String, Object>>) carbonMessage.getProperty(Constants.VARIABLE_STACK);
            carbonMessage.setHeader("Host", "localhost:8081");
            carbonMessage.setProperty("PORT", "8081");
            carbonMessage.setProperty("DIRECTION", "DIRECTION_REQUEST");
            DefaultCarbonMessage newcMsg = new DefaultCarbonMessage();
            copyProperties(carbonMessage, newcMsg);
            copyHeaders(carbonMessage, newcMsg);
            carbonMessage = newcMsg;
            newcMsg.addMessageBody(ByteBuffer.wrap("Sample Request".getBytes()));
            newcMsg.setEndOfMsgAdded(true);
            newcMsg.setProperty(Constants.VARIABLE_STACK, variableStack);
//            carbonCallback = (CarbonCallback) carbonMessage.getProperty("ORIGINAL_REQUEST_CALLBACK");
        }

//        carbonMessage.setProperty("ORIGINAL_REQUEST_CALLBACK", carbonCallback);

        super.receive(carbonMessage, carbonCallback);

        OutboundEndpoint endpoint = outboundEndpoint;
        if (endpoint == null) {
            endpoint = ConfigRegistry.getInstance().getOutboundEndpoint(outboundEPKey);

            if (endpoint == null) {
                log.error("Outbound Endpoint : " + outboundEPKey + "not found ");
                return false;
            }
        }

        CarbonCallback callback = new FlowControllerCallback(carbonCallback, this,
                VariableUtil.getVariableStack(carbonMessage));

        endpoint.receive(carbonMessage, callback);
        return false;
    }


}
