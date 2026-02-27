package org.yamcs.scos2k;

import java.util.List;

import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.ValueEnumeration;

/**
 * In SCOS2k MIB, the CPC_CATEG='p' means that an on-board parameter id can be specified as argument to the command. In
 * this case the SCOS2k loader will create this argument as enumeration with the possible values all parameters that
 * have a pid set.
 * <p>
 * This interface allows changing the behaviour - sometime it is necessary to restrict the number of parameters which
 * can be set (some of them may be read only) or maybe there are parameters which for some reason are not defined in the
 * MIB.
 */
public interface ParameterIdEnumerationsProvider {
    List<ValueEnumeration> getParameterIdEnumerations(TcMibLoader mib, MetaCommand mc);
}
