package org.yawlfoundation.yawl.worklet.rdr;

import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.worklet.support.Persister;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Michael Adams
 * @date 17/09/2014
 */
public class RdrSetLoader {

    public RdrSetLoader() { }


    public RdrSet load(YSpecificationID specID) { return loadSet(specID); }


    public RdrSet load(String processName) { return loadSet(processName); }


    public RdrNode loadNode(long nodeID) {
        return (RdrNode) Persister.getInstance().get(RdrNode.class, nodeID);
    }


    public Set<String> getSetIDs() {
        Set<String> ids = new HashSet<String>();
        for (Object o : Persister.getInstance().getObjectsForClass("RdrSet")) {
            RdrSet rdrSet = (RdrSet) o;
            YSpecificationID specID = rdrSet.getSpecificationID();
            String id = specID != null ? specID.toFullString() : rdrSet.getProcessName();
            if (id != null) {
                ids.add(id);
            }
        }
        Persister.getInstance().commit();
        return ids;
    }


    public RdrSet removeSet(YSpecificationID specID) { return removeSet(loadSet(specID)); }


    public RdrSet removeSet(String processName) { return removeSet(loadSet(processName)); }


    public RdrSet removeSet(RdrSet rdrSet) {
        if (rdrSet != null) {
            Persister.delete(rdrSet);
        }
        return rdrSet;
    }


    private RdrSet loadSet(YSpecificationID specID) {
        String id = specID.getIdentifier();
        return id != null ? loadSet("_specID.identifier", id) :
                loadSet("_specID.uri", specID.getUri());               // pre v2.0
    }


    private RdrSet loadSet(String name) {
        return loadSet("_processName=", name);
    }


    private RdrSet loadSet(String column, String value) {
        Criterion criterion = Restrictions.eq(column, value);
        List list = Persister.getInstance().getByCriteria(RdrSet.class, criterion);
        return ! (list == null || list.isEmpty()) ? (RdrSet) list.get(0) : null;
    }

}
