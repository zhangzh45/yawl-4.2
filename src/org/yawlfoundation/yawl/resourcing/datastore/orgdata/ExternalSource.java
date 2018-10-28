package org.yawlfoundation.yawl.resourcing.datastore.orgdata;

import org.yawlfoundation.yawl.exceptions.YAuthenticationException;
import org.yawlfoundation.yawl.resourcing.datastore.HibernateEngine;
import org.yawlfoundation.yawl.resourcing.resource.*;
import org.yawlfoundation.yawl.resourcing.resource.nonhuman.NonHumanCategory;
import org.yawlfoundation.yawl.resourcing.resource.nonhuman.NonHumanResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ExternalSource extends DataSource{
	
	
	public static GetExternalSource externalsource = new GetExternalSource();
	private HibernateEngine _db ;
	
	// persistence actions
    private final int _UPDATE = HibernateEngine.DB_UPDATE;
    private final int _DELETE = HibernateEngine.DB_DELETE;
    private final int _INSERT = HibernateEngine.DB_INSERT;
	
	public ExternalSource(){	
		 _db = HibernateEngine.getInstance(true) ;
	}
	
	/**
     * Override of super.getNextID() to apply an appropriate prefix to the id
     * @param obj the object to generate a unique id for
     * @return a unique identifier appropriately prefixed
     */
    private String getNextID(Object obj) {
        String prefix = "";

        if (obj instanceof OrgGroup) prefix = "OG" ;
        else if (obj instanceof Capability) prefix = "CA" ;
        else if (obj instanceof Position) prefix = "PO" ;
        else if (obj instanceof Role) prefix = "RO" ;
        else if (obj instanceof Participant) prefix = "PA";
        else if (obj instanceof NonHumanResource) prefix = "NH";
        else if (obj instanceof NonHumanCategory) prefix = "NC";

        return getNextID(prefix);
    }
	
	
	public HashMap<String,Participant> loadParticipants() {
        HashMap<String,Participant> participantMap = new HashMap<String,Participant>();
        List<Participant> participantList = externalsource.loadExternalParticipants();

		 System.out.print(participantList.size()+"????");
		 for(int i = 0; i < participantList.size(); i++){
			 System.out.print(participantList.get(i).getUserID()+"????"+participantList.get(i).getPassword());
			 
		 }
        
        for (Participant p : participantList) participantMap.put(p.getID(), p);
        return participantMap ;
    }
	
	public HashMap<String,Capability> loadCapabilities() {
        HashMap<String,Capability> capabilityMap = new HashMap<String,Capability>();
        List<Capability> capabilityList = externalsource.loadExternalCapabilities();
        for (Capability c : capabilityList) capabilityMap.put(c.getID(), c);
        return capabilityMap ;
    }
	
	public HashMap<String,OrgGroup> loadOrgGroups() {
        HashMap<String,OrgGroup> OrgGroupMap = new HashMap<String,OrgGroup>();
        List<OrgGroup> OrgGroupList = externalsource.loadExternalOrgGroups();
        for (OrgGroup o : OrgGroupList) OrgGroupMap.put(o.getID(), o);
        return OrgGroupMap;
    }
	
	public HashMap<String,Position> loadPositions() {
        HashMap<String,Position> positionMap = new HashMap<String,Position>();
        List<Position> positionList = externalsource.loadExternalPositions();
        for (Position p : positionList) positionMap.put(p.getID(), p);
        return positionMap ;
    }
	
	public HashMap<String,Role> loadRoles() {
        HashMap<String,Role> roleMap = new HashMap<String,Role>() ;
        List<Role> roleList = _db.getObjectsForClass(Role.class.getName()) ;
        for (Role r : roleList) roleMap.put(r.getID(), r) ;
        _db.commit();
        return roleMap ;
    }
	
	public HashMap<String,NonHumanResource> loadNonHumanResources() {
        HashMap<String,NonHumanResource> nhMap = new HashMap<String,NonHumanResource>();
        List<NonHumanResource> nhList = _db.getObjectsForClass(NonHumanResource.class.getName()) ;
        for (NonHumanResource r : nhList) nhMap.put(r.getID(), r) ;
        _db.commit();
        return nhMap ;
    }

    public HashMap<String, NonHumanCategory> loadNonHumanCategories() {
        HashMap<String, NonHumanCategory> nhCategoryMap =
                new HashMap<String, NonHumanCategory>();
        List<NonHumanCategory> nhList = _db.getObjectsForClass(NonHumanCategory.class.getName()) ;
        for (NonHumanCategory r : nhList) nhCategoryMap.put(r.getID(), r) ;
        _db.commit();
        return nhCategoryMap ;
    }

	@Override
	public ResourceDataSet loadResources() {
		// TODO Auto-generated method stub
		 ResourceDataSet ds = new ResourceDataSet(this) ;
		 
		 List<Participant> participants = new ArrayList<Participant>();
		 List<Role> roles = new ArrayList<Role>();
		// HashMap<String,Role> roleMap = new HashMap<String,Role>();
		 List<Capability> capabilities = new ArrayList<Capability>();
		 List<OrgGroup> orgGroups = new ArrayList<OrgGroup>();
		 List<Position> positions = new ArrayList<Position>();
		 List<NonHumanResource> resList = new ArrayList<NonHumanResource>();
		 List<NonHumanCategory> catList = new ArrayList<NonHumanCategory>();
		 
		 participants = externalsource.loadExternalParticipants();
		 System.out.print(participants.size()+"????");
		 for(int i = 0; i < participants.size(); i++){
			 System.out.print(participants.get(i).getUserID()+"????"+participants.get(i).getPassword());
			 ds.putParticipant(participants.get(i));
		 }
		 
		/* roles = externalsource.loadExternalRoles();
		 for(int i = 0; i < roles.size(); i++){
			 ds.putRole(roles.get(i));
		 }*/
	     roles = _db.getObjectsForClass(Role.class.getName()) ;   //role从本地数据库获取
	     for(int i = 0; i < roles.size(); i++){
			 ds.putRole(roles.get(i));
		 }
		 
		 capabilities = externalsource.loadExternalCapabilities();
		 for(int i = 0; i < capabilities.size(); i++){
			 ds.putCapability(capabilities.get(i));
		 }
		 
		 
		 orgGroups = externalsource.loadExternalOrgGroups();
		 for(int i = 0; i < orgGroups.size(); i++){
			 ds.putOrgGroup(orgGroups.get(i));
		 }
		 
		 positions = externalsource.loadExternalPositions();
		 for(int i = 0; i < positions.size(); i++){
			 ds.putPosition(positions.get(i));
		 }
		 
		 resList = _db.getObjectsForClass(NonHumanResource.class.getName()) ;
	     if (resList != null) for (NonHumanResource res : resList) ds.putNonHumanResource(res) ;

	     catList = _db.getObjectsForClass(NonHumanCategory.class.getName()) ;
	     if (catList != null) for (NonHumanCategory cat : catList)
	           ds.putNonHumanCategory(cat) ;
		 
		 return ds;
	}

	@Override
	public void update(Object obj) {
		// TODO Auto-generated method stub
		if (obj instanceof Role || obj instanceof NonHumanResource || obj instanceof NonHumanCategory) {
			_db.exec(obj, _UPDATE);
		}
	}

	@Override
	public boolean delete(Object obj) {
		// TODO Auto-generated method stub
		if (obj instanceof Role || obj instanceof NonHumanResource || obj instanceof NonHumanCategory) {
			return _db.exec(obj, _DELETE);
		}
		return false;
	}

	@Override
	public String insert(Object obj) {
		// TODO Auto-generated method stub
		if (obj instanceof Role || obj instanceof NonHumanResource || obj instanceof NonHumanCategory) {
			String id = getNextID(obj);

	        // set the newly generated id
	        if (obj instanceof AbstractResource) {
	            ((AbstractResource) obj).setID(id);

	            // if a Participant, pre-insert the user privileges
	            /*if (obj instanceof Participant) {
	                _db.exec(((Participant) obj).getUserPrivileges(), _INSERT);
	            }*/
	        }
	        else if (obj instanceof AbstractResourceAttribute) {
	            ((AbstractResourceAttribute) obj).setID(id);
	        }
	        else {
	            ((NonHumanCategory) obj).setID(id);
	        }
	        _db.exec(obj, _INSERT);

	        return id ;
		}
		return null;
	}

	@Override
	public void importObj(Object obj) {
		// TODO Auto-generated method stub
		if (obj instanceof Role || obj instanceof NonHumanResource || obj instanceof NonHumanCategory) {
			_db.exec(obj, _INSERT);
		}
		
	}

	@Override
	public int execUpdate(String query) {
		// TODO Auto-generated method stub
		return _db.execUpdate(query);
	}

	@Override
	public boolean authenticate(String userid, String password)
			throws YAuthenticationException {
		// TODO Auto-generated method stub
		System.out.print(userid+":"+password+"\n");
		List<Participant> ps = new ArrayList<Participant>();
		ps = externalsource.loadExternalParticipants();
		for(int i = 0; i < ps.size(); i++){
			Participant p = new Participant();
			p = ps.get(i);
			if(p.getUserID() != null && p.getUserID().equals(userid)){
				if(p.getPassword() != null && p.getPassword().equals(password)){
					return true;
				}
			}
		}
		return false;
	}
		
}
