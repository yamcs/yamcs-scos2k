package org.yamcs.scos2k.ol;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import org.codehaus.janino.SimpleCompiler;
import org.yamcs.algorithms.AbstractAlgorithmExecutor;
import org.yamcs.algorithms.AlgorithmException;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.events.EventProducer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.DataEncodingDecoder;
import org.yamcs.xtceproc.ParameterTypeProcessor;
import org.yamcs.xtceproc.ProcessorData;

public class OLExecutor extends AbstractAlgorithmExecutor {
    private static final String OL_GV_KEY = "SCOS2K_OL_GLOBAL_VARS";

    final OLEvaluator olEvaluator;
    final GlobalVariables globalVars;
    final OutputParameter outputParameter;
    final EventProducer eventProducer;
    ParameterTypeProcessor parameterTypeProcessor;

    public OLExecutor(Algorithm algorithmDef, AlgorithmExecutionContext execCtx) throws AlgorithmException {
        super(algorithmDef, execCtx);
        CustomAlgorithm calg = (CustomAlgorithm) algorithmDef;
        outputParameter = calg.getOutputList().get(0);
        ProcessorData pdata = execCtx.getProcessorData();
        globalVars = getGlobalVars(pdata);
        this.eventProducer = execCtx.getProcessorData().getEventProducer();
        XtceDb xtcedb = execCtx.getXtceDb();
        String formula = calg.getAlgorithmText();
        this.parameterTypeProcessor = new ParameterTypeProcessor(execCtx.getProcessorData());
        String name = algorithmDef.getName();

        OLParser parser = new OLParser(new StringReader(formula));
        String code;
        try {
            code = parser.generateCodeStandalone(name,
                    pname -> xtcedb.getParameter(algorithmDef.getSubsystemName(), pname).getParameterType());
        } catch (Exception e) {
            log.warn("Failed to parse formula {}", formula, e);
            execCtx.getProcessorData().getEventProducer()
                    .sendCritical("Unable to parse the OL Formula for '" + name + "': " + e.getMessage());
            throw new AlgorithmException("Failed to compile OL formula", e);
        }
        try {
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.cook(code);
            Class<?> cexprClass = compiler.getClassLoader().loadClass("org.yamcs.scos2k.ol.generated." + name);
            olEvaluator = (OLEvaluator) cexprClass.newInstance();
        } catch (Exception e) {
            log.warn("Failed to compile code "
                    + "\n---------\n{}\n--------\n"
                    + " generated from formula"
                    + "\n---------\n{}\n--------\n", code, formula, e);
            execCtx.getProcessorData().getEventProducer().sendCritical(algorithmDef.getName(),
                    "Unable to compile the OL Formula: " + e.getMessage());
            throw new AlgorithmException("Failed to compile OL formula", e);
        }
    }

    private GlobalVariables getGlobalVars(ProcessorData pdata) {
        synchronized (pdata) {
            GlobalVariables gv = pdata.getUserData(OL_GV_KEY);
            if (gv == null) {
                gv = new GlobalVariables();
                pdata.setUserData(OL_GV_KEY, gv);
            }
            return gv;
        }
    }

    @Override
    public List<ParameterValue> runAlgorithm(long acqTime, long genTime) {
        ParameterValue out = new ParameterValue(outputParameter.getParameter());
        Object value = olEvaluator.evaluate(globalVars, inputValues);
        BaseDataType bdt = (BaseDataType)outputParameter.getParameter().getParameterType();
        DataEncoding de = bdt.getEncoding();
        Value rawValue = DataEncodingDecoder.getRawValue(de, value);
        if (rawValue == null) {
            execCtx.getProcessorData().getEventProducer()
                    .sendWarning(getAlgorithm().getName(), "Cannot convert raw value from algorithm output "
                            + "'" + value + "' of type " + value.getClass() + " into " + de);
            out.setAcquisitionStatus(AcquisitionStatus.INVALID);
        } else {
            out.setRawValue(rawValue);
            parameterTypeProcessor.calibrate(out);
        }
        return Arrays.asList(out);
    }
}
