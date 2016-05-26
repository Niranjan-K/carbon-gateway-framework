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

package org.wso2.carbon.gateway.core.flow.mediators.builtin.flowcontrollers.pipeline;

import org.wso2.carbon.gateway.core.flow.AbstractFlowController;
import org.wso2.carbon.gateway.core.flow.FlowControllerCallback;
import org.wso2.carbon.gateway.core.flow.Pipeline;
import org.wso2.carbon.gateway.core.util.VariableUtil;
import org.wso2.carbon.messaging.CarbonCallback;
import org.wso2.carbon.messaging.CarbonMessage;

public class PipelineMediator extends AbstractFlowController {

    private Pipeline parentPipeline;
    private Pipeline pipeline;

    public PipelineMediator(Pipeline parentPipeline, Pipeline pipeline) {
        this.pipeline = pipeline;
        this.parentPipeline = parentPipeline;
    }

    @Override
    public String getName() {
        return "pipelineMediator";
    }

    @Override
    public boolean receive(CarbonMessage cMsg, CarbonCallback carbonCallback) throws Exception {
        super.receive(cMsg, carbonCallback);

        parentPipeline.setCurrentPosition(cMsg, parentPipeline.getCurrentPosition(cMsg) + 1);

        pipeline.getMediators().getMediators().get(pipeline.getCurrentPosition(cMsg)).
                receive(cMsg, new FlowControllerCallback(carbonCallback, this,
                        VariableUtil.getVariableStack(cMsg)));

        //// skip 1 in parent pipeline
        parentPipeline.setCurrentPosition(cMsg, parentPipeline.getCurrentPosition(cMsg) + 1);
        parentPipeline.receive(cMsg, carbonCallback);

        return true;
    }

}
