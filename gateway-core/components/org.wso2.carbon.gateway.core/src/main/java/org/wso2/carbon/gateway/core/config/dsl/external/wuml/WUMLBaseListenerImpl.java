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
package org.wso2.carbon.gateway.core.config.dsl.external.wuml;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.wso2.carbon.gateway.core.config.Parameter;
import org.wso2.carbon.gateway.core.config.ParameterHolder;
import org.wso2.carbon.gateway.core.config.dsl.external.StringParserUtil;
import org.wso2.carbon.gateway.core.config.dsl.external.WUMLConfigurationBuilder;
import org.wso2.carbon.gateway.core.config.dsl.external.wuml.generated.WUMLBaseListener;
import org.wso2.carbon.gateway.core.config.dsl.external.wuml.generated.WUMLParser;
import org.wso2.carbon.gateway.core.flow.Group;
import org.wso2.carbon.gateway.core.flow.Mediator;
import org.wso2.carbon.gateway.core.flow.MediatorProviderRegistry;
import org.wso2.carbon.gateway.core.flow.Pipeline;
import org.wso2.carbon.gateway.core.flow.mediators.builtin.flowcontrollers.filter.Condition;
import org.wso2.carbon.gateway.core.flow.mediators.builtin.flowcontrollers.filter.FilterMediator;
import org.wso2.carbon.gateway.core.flow.mediators.builtin.flowcontrollers.filter.Source;
import org.wso2.carbon.gateway.core.flow.mediators.builtin.flowcontrollers.pipeline.PipelineMediator;
import org.wso2.carbon.gateway.core.inbound.InboundEPProviderRegistry;
import org.wso2.carbon.gateway.core.inbound.InboundEndpoint;
import org.wso2.carbon.gateway.core.outbound.OutboundEPProviderRegistry;
import org.wso2.carbon.gateway.core.outbound.OutboundEndpoint;
import org.wso2.carbon.gateway.core.util.VariableUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Pattern;

/**
 * Implementation class of the ANTLR generated listener class
 */
public class WUMLBaseListenerImpl extends WUMLBaseListener {

    public static final String INBOUND = "INBOUND";
    public static final String OUTBOUND = "OUTBOUND";
    public static final String PIPELINE = "PIPELINE";
    public static final String ROOTENV = "ROOTENV";

    private static final String DOUBLECOLON = "::";
    WUMLConfigurationBuilder.IntegrationFlow integrationFlow;
    Map<String, String> identifierTypeMap = new HashMap<>();

    private Stack<String> pipelineStack = new Stack<>();
    private Map<String, Environment> pipelineEnv = new HashMap<>();
    private String currentPipeline = ROOTENV;

    public WUMLBaseListenerImpl() {
        this.integrationFlow = WUMLConfigurationBuilder.getDefultIntegrationFlow();
        pipelineEnv.put(ROOTENV, new Environment());
    }

    public WUMLBaseListenerImpl(WUMLConfigurationBuilder.IntegrationFlow integrationFlow) {
        this.integrationFlow = integrationFlow;
        pipelineEnv.put(ROOTENV, new Environment());
    }

    public WUMLConfigurationBuilder.IntegrationFlow getIntegrationFlow() {
        return integrationFlow;
    }

    @Override
    public void exitScript(WUMLParser.ScriptContext ctx) {
        super.exitScript(ctx);
    }

    private Environment getEnv(String currentPipelie) {
        return pipelineEnv.get(currentPipelie);
    }

    @Override
    public void exitVariableDeclarationStatement(WUMLParser.VariableDeclarationStatementContext ctx) {
        String varType = ctx.TYPEDEFINITIONX().getText();
        String varIdentifier = ctx.IDENTIFIER().getText();
        String varValue =  StringParserUtil.getValueWithinDoubleQuotes(ctx.COMMENTSTRINGX().getText());

        Mediator mediator = MediatorProviderRegistry.getInstance().getMediator("property");

        ParameterHolder parameterHolder = new ParameterHolder();
        parameterHolder.addParameter(new Parameter("key", varIdentifier));
        parameterHolder.addParameter(new Parameter("value", varValue));
        parameterHolder.addParameter(new Parameter("type", varType));
        parameterHolder.addParameter(new Parameter("assignment", "false"));
        mediator.setParameters(parameterHolder);

        if (getEnv(currentPipeline).getPipelineStack().size() == 0) {
            // ignore, we only accept constants at highest level of mediation flow and these should not be updateable.
            //integrationFlow.getGWConfigHolder().addGlobalConstant(varType, varIdentifier, varValue);
        } else {
            dropMediatorFilterAware(mediator);
        }

        super.exitVariableDeclarationStatement(ctx);
    }

    @Override
    public void exitVariableAssignmentStatement(WUMLParser.VariableAssignmentStatementContext ctx) {
        String varIdentifier = ctx.VAR_IDENTIFIER().getText().replace("=", "").trim().substring(1);
        String varValue = StringParserUtil.getValueWithinDoubleQuotes(ctx.COMMENTSTRINGX().getText());
        Mediator mediator = MediatorProviderRegistry.getInstance().getMediator("property");
        ParameterHolder parameterHolder = new ParameterHolder();
        parameterHolder.addParameter(new Parameter("key", varIdentifier));
        parameterHolder.addParameter(new Parameter("value", varValue));
        parameterHolder.addParameter(new Parameter("type", null));
        parameterHolder.addParameter(new Parameter("assignment", "true"));
        mediator.setParameters(parameterHolder);

        if (getEnv(currentPipeline).getPipelineStack().size() == 0) {
            // Only constant declarations allowed at the highest level.
            //integrationFlow.getGWConfigHolder().updateGlobalConstant(varIdentifier, varValue);
        } else {
            dropMediatorFilterAware(mediator);
        }
        super.exitVariableAssignmentStatement(ctx);
    }

    @Override
    public void exitConstStatement(WUMLParser.ConstStatementContext ctx) {
        String constType = ctx.TYPEDEFINITIONX().getText();

        String constIdentifier = ctx.IDENTIFIER().getText();

        String constValue = null;
        if (!constIdentifier.toLowerCase(Locale.ROOT).equals("string")) {
            constValue = ctx.COMMENTSTRINGX().getText();
        } else {
            constValue = StringParserUtil.getValueWithinDoubleQuotes(ctx.COMMENTSTRINGX().getText());
        }

        if (getEnv(currentPipeline).getPipelineStack().size() == 0) {
            integrationFlow.getGWConfigHolder().addGlobalConstant(
                    VariableUtil.getType(constType), constIdentifier, constValue);
        } // constants only allowed at the highest level

        super.exitConstStatement(ctx);
    }

    @Override
    public void exitHandler(WUMLParser.HandlerContext ctx) {
        super.exitHandler(ctx);
    }

    @Override
    public void exitStatementList(WUMLParser.StatementListContext ctx) {
        super.exitStatementList(ctx);
    }

    @Override
    public void exitStatement(WUMLParser.StatementContext ctx) {
        super.exitStatement(ctx);
    }

    @Override
    public void exitParticipantStatement(WUMLParser.ParticipantStatementContext ctx) {
        super.exitParticipantStatement(ctx);
    }

    @Override
    public void exitIntegrationFlowDefStatement(WUMLParser.IntegrationFlowDefStatementContext ctx) {
        //Create the integration flow when definition is found
        integrationFlow = new WUMLConfigurationBuilder.IntegrationFlow(ctx.IDENTIFIER().getText());
        super.exitIntegrationFlowDefStatement(ctx);
    }

    @Override
    public void exitTitleStatement(WUMLParser.TitleStatementContext ctx) {
        //Create the integration flow when definition is found
        integrationFlow = new WUMLConfigurationBuilder.IntegrationFlow(ctx.IDENTIFIER().getText());
        super.exitTitleStatement(ctx);
    }

    @Override
    public void exitInboundEndpointDefStatement(WUMLParser.InboundEndpointDefStatementContext ctx) {
        identifierTypeMap.put(ctx.IDENTIFIER().getText(), INBOUND);
        String protocolName = getValue(StringParserUtil.
                getValueWithinDoubleQuotes(ctx.inboundEndpointDef().PROTOCOLDEF().getText())).toString();

        ParameterHolder parameterHolder = new ParameterHolder();

        for (TerminalNode terminalNode : ctx.inboundEndpointDef().PARAMX()) {
            String keyValue = terminalNode.getSymbol().getText();
            String key = keyValue.substring(1, keyValue.indexOf("("));
            String value =
                    getValue(keyValue.substring(keyValue.indexOf("\"") + 1, keyValue.lastIndexOf("\""))).toString();

            parameterHolder.addParameter(new Parameter(key, value));
        }

        InboundEndpoint inboundEndpoint = InboundEPProviderRegistry.getInstance().getProvider(protocolName)
                .getInboundEndpoint();
        inboundEndpoint.setParameters(parameterHolder);

        integrationFlow.getGWConfigHolder().setInboundEndpoint(inboundEndpoint);
        super.exitInboundEndpointDefStatement(ctx);
    }

    @Override
    public void exitPipelineDefStatement(WUMLParser.PipelineDefStatementContext ctx) {
        String pipeId = getValue(ctx.IDENTIFIER().getText()).toString();
        identifierTypeMap.put(pipeId, PIPELINE);
        integrationFlow.getGWConfigHolder().addPipeline(new Pipeline(pipeId));
        super.exitPipelineDefStatement(ctx);
    }

    @Override
    public void exitOutboundEndpointDefStatement(WUMLParser.OutboundEndpointDefStatementContext ctx) {
        identifierTypeMap.put(ctx.IDENTIFIER().getText(), OUTBOUND);
        String protocolName = getValue(StringParserUtil.getValueWithinDoubleQuotes(ctx.outboundEndpointDef().
                PROTOCOLDEF().getText())).toString();

        ParameterHolder parameterHolder = new ParameterHolder();

        for (TerminalNode terminalNode : ctx.outboundEndpointDef().PARAMX()) {
            String keyValue = terminalNode.getSymbol().getText();
            String key = keyValue.substring(1, keyValue.indexOf("("));
            String value =
                    getValue(keyValue.substring(keyValue.indexOf("\"") + 1, keyValue.lastIndexOf("\""))).toString();

            parameterHolder.addParameter(new Parameter(key, value));
        }

        OutboundEndpoint outboundEndpoint = OutboundEPProviderRegistry.getInstance().getProvider(protocolName)
                .getEndpoint();
        outboundEndpoint.setName(ctx.IDENTIFIER().getText());
        outboundEndpoint.setParameters(parameterHolder);

        integrationFlow.getGWConfigHolder().addOutboundEndpoint(outboundEndpoint);
        super.exitOutboundEndpointDefStatement(ctx);
    }

    @Override
    public void exitInboundEndpointDef(WUMLParser.InboundEndpointDefContext ctx) {
        super.exitInboundEndpointDef(ctx);
    }

    @Override
    public void exitPipelineDef(WUMLParser.PipelineDefContext ctx) {
        super.exitPipelineDef(ctx);
    }

    @Override
    public void exitOutboundEndpointDef(WUMLParser.OutboundEndpointDefContext ctx) {
        super.exitOutboundEndpointDef(ctx);
    }

    @Override
    public void exitIntegrationFlowDef(WUMLParser.IntegrationFlowDefContext ctx) {
        super.exitIntegrationFlowDef(ctx);
    }

    @Override
    public void exitMediatorStatement(WUMLParser.MediatorStatementContext ctx) {
        super.exitMediatorStatement(ctx);
    }

    @Override
    public void exitMediatorStatementDef(WUMLParser.MediatorStatementDefContext ctx) {
        String mediatorDefinition = ctx.MEDIATORDEFINITIONX().getText();
        String mediatorName = mediatorDefinition.split(DOUBLECOLON)[1];

        String pipeline = ctx.MEDIATORDEFINITIONX().getText().split("::")[0];
        String configurations = StringParserUtil.getValueWithinDoubleQuotes(ctx.ARGUMENTLISTDEF().getText());
        Mediator mediator = MediatorProviderRegistry.getInstance().getMediator(mediatorName);

        ParameterHolder parameterHolder = new ParameterHolder();
        parameterHolder.addParameter(new Parameter("parameters", configurations));
        mediator.setParameters(parameterHolder);

        // mediator.setParameters(configurations);
        currentPipeline = pipeline;
        if (getEnv(currentPipeline) == null) {
            pipelineEnv.put(currentPipeline, new Environment());
            getEnv(currentPipeline).getPipelineStack().push(currentPipeline);
        }
        dropMediatorFilterAware(mediator);
        super.exitMediatorStatementDef(ctx);
    }

    @Override
    public void exitConditionDef(WUMLParser.ConditionDefContext ctx) {
        super.exitConditionDef(ctx);
    }

    @Override
    public void exitRoutingStatement(WUMLParser.RoutingStatementContext ctx) {
        super.exitRoutingStatement(ctx);
    }

    @Override
    public void exitParallelStatement(WUMLParser.ParallelStatementContext ctx) {
        super.exitParallelStatement(ctx);
    }

    @Override
    public void exitParMultiThenBlock(WUMLParser.ParMultiThenBlockContext ctx) {
        super.exitParMultiThenBlock(ctx);
    }

    @Override
    public void exitParElseBlock(WUMLParser.ParElseBlockContext ctx) {
        super.exitParElseBlock(ctx);
    }

    @Override
    public void exitIfStatement(WUMLParser.IfStatementContext ctx) {
        //ctx.expression().EXPRESSIONX()
        getEnv(currentPipeline).setIfMultiThenBlockStarted(false);
        getEnv(currentPipeline).setIfElseBlockStarted(false);
        if (!getEnv(currentPipeline).getFilterMediatorStack().isEmpty()) {
            getEnv(currentPipeline).getFilterMediatorStack().pop();
        }
        super.exitIfStatement(ctx);
    }

    @Override
    public void exitConditionStatement(WUMLParser.ConditionStatementContext ctx) {
        String sourceDefinition = StringParserUtil.getValueWithinDoubleQuotes(ctx.conditionDef().SOURCEDEF().getText());
        Source source = new Source(sourceDefinition);
        String conditionValue = null;

        for (TerminalNode terminalNode : ctx.conditionDef().PARAMX()) {
            String keyValue = terminalNode.getSymbol().getText();
            String key = keyValue.substring(1, keyValue.indexOf("("));
            String value = keyValue.substring(keyValue.indexOf("\"") + 1, keyValue.lastIndexOf("\""));

            if ("pattern".equals(key)) {
                conditionValue = value;
            }
        }

        Condition condition = new Condition(source, Pattern.compile(conditionValue));

        FilterMediator filterMediator = new FilterMediator(condition);
        filterMediator.setPipeline(getIntegrationFlow().getGWConfigHolder().getPipeline(getEnv(currentPipeline).getPipelineStack().peek()));
        integrationFlow.getGWConfigHolder().getPipeline(getEnv(currentPipeline).getPipelineStack().peek()).addMediator(filterMediator);
        getEnv(currentPipeline).getFilterMediatorStack().push(filterMediator);
        super.exitConditionStatement(ctx);
    }

    @Override
    public void enterIfMultiThenBlock(WUMLParser.IfMultiThenBlockContext ctx) {
        getEnv(currentPipeline).setIfMultiThenBlockStarted(true);
        super.enterIfMultiThenBlock(ctx);
    }

    @Override
    public void enterIfElseBlock(WUMLParser.IfElseBlockContext ctx) {
        getEnv(currentPipeline).setIfMultiThenBlockStarted(false);
        getEnv(currentPipeline).setIfElseBlockStarted(true);
        super.enterIfElseBlock(ctx);
    }

    @Override
    public void exitIfMultiThenBlock(WUMLParser.IfMultiThenBlockContext ctx) {
        getEnv(currentPipeline).setIfMultiThenBlockStarted(false);
        super.exitIfMultiThenBlock(ctx);
    }

    @Override
    public void exitIfElseBlock(WUMLParser.IfElseBlockContext ctx) {
        getEnv(currentPipeline).setIfElseBlockStarted(false);
        super.exitIfElseBlock(ctx);
    }

    @Override
    public void exitLoopStatement(WUMLParser.LoopStatementContext ctx) {
        super.exitLoopStatement(ctx);
    }

    @Override
    public void exitRefStatement(WUMLParser.RefStatementContext ctx) {
        getEnv(currentPipeline).getPipelineStack().push(ctx.IDENTIFIER().getText());
        pipelineStack.push(ctx.IDENTIFIER().getText());

        super.exitRefStatement(ctx);
    }

    @Override
    public void exitExpression(WUMLParser.ExpressionContext ctx) {
        super.exitExpression(ctx);
    }

    @Override
    public void exitRoutingStatementDef(WUMLParser.RoutingStatementDefContext ctx) {
        String firstIdentifier = ctx.IDENTIFIER(0).getText();
        String secondIdentifier = ctx.IDENTIFIER(1).getText();
        String identifierType;

        String firstType = identifierTypeMap.get(firstIdentifier);
        String secondType = identifierTypeMap.get(secondIdentifier);

        if (firstType != null && firstType != PIPELINE) {
            if (INBOUND.equals(firstType)) {
                identifierType = "invokeFromSource";
            } else {
                identifierType = "invokeFromTarget";
            }

        } else {
            if (INBOUND.equals(secondType)) {
                identifierType = "invokeToSource";
            } else {
                identifierType = "invokeToTarget";
            }
        }

        if (firstType != null & secondType != null && firstType.equals(PIPELINE) && secondType.equals(PIPELINE)) {
            identifierType = "pipelineToPipeline";
        }

        String pipelineName = ctx.IDENTIFIER(1).getText();

        if (currentPipeline.equals(ROOTENV)) {
            pipelineEnv.put(pipelineName, new Environment());
            currentPipeline = pipelineName;
        }

        switch (identifierType) {
        case "invokeFromSource":
            if (getEnv(ROOTENV).isInsideGroup()) {
                integrationFlow.getGWConfigHolder().getGroup(getEnv(ROOTENV).getGroupPath()).setPipeline(pipelineName);
            } else {
                integrationFlow.getGWConfigHolder().getInboundEndpoint().setPipeline(pipelineName);
            }

            getEnv(currentPipeline).getPipelineStack().push(pipelineName);
            pipelineStack.push(pipelineName);
            break;
        case "invokeFromTarget":
            getEnv(currentPipeline).getPipelineStack().push(pipelineName);
            pipelineStack.push(pipelineName);
            break;
        case "invokeToSource":
            Mediator respondMediator = MediatorProviderRegistry.getInstance().getMediator("respond");
            dropMediatorFilterAware(respondMediator);
            getEnv(currentPipeline).getPipelineStack().pop();
            pipelineStack.pop();
            break;
        case "invokeToTarget":
            Mediator callMediator = MediatorProviderRegistry.getInstance().getMediator("call");

            ParameterHolder parameterHolder = new ParameterHolder();
            parameterHolder.addParameter(new Parameter("endpointKey", ctx.IDENTIFIER(1).getText()));

            callMediator.setParameters(parameterHolder);
            dropMediatorFilterAware(callMediator);
            getEnv(currentPipeline).getPipelineStack().pop();
            pipelineStack.pop();
            break;

        case "pipelineToPipeline":
            if (pipelineEnv.get(secondIdentifier) == null) {
                Environment env = new Environment();
                env.getPipelineStack().push(secondIdentifier);
                pipelineEnv.put(secondIdentifier, env);
            }

            PipelineMediator pipelineInvoker = new PipelineMediator(
                    getIntegrationFlow().getGWConfigHolder().getPipeline(ctx.IDENTIFIER(0).getText()),
                    getIntegrationFlow().getGWConfigHolder().getPipeline(ctx.IDENTIFIER(1).getText()));

            dropMediatorFilterAware(pipelineInvoker);

            currentPipeline = secondIdentifier;

            break;

//            Stack<String> toPipeStack;
//            if (pipelineStack.peek().equals(firstIdentifier) &&
//                    openPipeStacks.get(secondIdentifier) != null) {
//
//            }
//
//            if (pipelineStack.peek().equals(firstIdentifier) &&
//                    openPipeStacks.get(secondIdentifier) == null) {
//                Stack<String> pipeStack = new Stack<String>();
//                pipeStack.push(secondIdentifier);
//                openPipeStacks.put(secondIdentifier, pipeStack);
//            }
//
//            break;


//            if (pipelineStack.peek().equals(firstIdentifier) &&
//                    (openPipelines.get(secondIdentifier) != null && openPipelines.get(secondIdentifier))) {
//                pipelineStack.pop();
//                pipelineToPipelineStack.pop();
//                openPipelines.remove(secondIdentifier);
//            } else {
//                pipelineToPipelineStack.push(pipelineStack.peek());
//                openPipelines.put(pipelineStack.peek(), true);
//                PipelineMediator pipelineInvoker = new PipelineMediator(
//                        getIntegrationFlow().getGWConfigHolder().getPipeline(ctx.IDENTIFIER(0).getText()),
//                        getIntegrationFlow().getGWConfigHolder().getPipeline(ctx.IDENTIFIER(1).getText()));
//
//                dropMediatorFilterAware(pipelineInvoker);
//                pipelineStack.push(ctx.IDENTIFIER(1).getText());
//            }
//            break;


        default:
            break;
        }

        super.exitRoutingStatementDef(ctx);
    }

    @Override
    public void enterGroupStatement(WUMLParser.GroupStatementContext ctx) {
        getEnv(ROOTENV).setInsideGroup(true);
        super.enterGroupStatement(ctx);
    }

    @Override
    public void exitGroupDefStatement(WUMLParser.GroupDefStatementContext ctx) {
        String path = StringParserUtil.getValueWithinDoubleQuotes(ctx.GROUP_PATH_DEF().getText().split("path=")[1]);
        Group group = new Group(path);
        getEnv(ROOTENV).setGroupPath(path);
        group.setMethod(
                StringParserUtil.getValueWithinDoubleQuotes(ctx.GROUP_METHOD_DEF().getText().split("method=")[1]));

        integrationFlow.getGWConfigHolder().addGroup(group);
        super.exitGroupDefStatement(ctx);
    }

    @Override
    public void exitGroupStatement(WUMLParser.GroupStatementContext ctx) {
        getEnv(currentPipeline).setInsideGroup(false);
        super.exitGroupStatement(ctx);
    }

    /**
     * Helper method to place mediator in correct stack when filter mediator is in use in mediation flow.
     * @param mediator
     */
    private void dropMediatorFilterAware(Mediator mediator) {
        // mediator.setParameters(configurations);
        if (getEnv(currentPipeline).getPipelineStack().size() <= 0) {
            getEnv(currentPipeline).getPipelineStack().push(currentPipeline);
            pipelineStack.push(currentPipeline);
        }

        mediator.setPipeline(getIntegrationFlow().getGWConfigHolder().getPipeline(getEnv(currentPipeline).getPipelineStack().peek()));
        if (getEnv(currentPipeline).isIfMultiThenBlockStarted()) {
            getEnv(currentPipeline).getFilterMediatorStack().peek().addThenMediator(mediator);

        } else if (getEnv(currentPipeline).isIfElseBlockStarted()) {
            getEnv(currentPipeline).getFilterMediatorStack().peek().addOtherwiseMediator(mediator);

        } else {
            integrationFlow.getGWConfigHolder().getPipeline(getEnv(currentPipeline).getPipelineStack().peek()).addMediator(mediator);
        }
    }


    /**
     * If variable detected, return value from global constants if it exists, in all other cases return key back.
     * @param key
     * @return Variable value or key
     */
    private Object getValue(String key) {
        if (key.startsWith("$")) {
            if (integrationFlow.getGWConfigHolder().getGlobalConstant(key.substring(1)) != null) {
                Object constVar = integrationFlow.getGWConfigHolder().getGlobalConstant(key.substring(1));
                if (constVar instanceof String) {
                    return StringParserUtil.getValueWithinDoubleQuotes((String) constVar);
                } else {
                    return constVar;
                }
            }
        }

        return key;
    }
}
