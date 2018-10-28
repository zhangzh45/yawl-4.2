package org.yawlfoundation.yawl.resourcing.datastore.orgdata;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.yawlfoundation.yawl.resourcing.datastore.HibernateEngine;
import org.yawlfoundation.yawl.resourcing.resource.*;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetExternalSource {
	
	//从组织管理系统加载组织资源
	public static String endpoint = "http://127.0.0.1:8020/demo/EmployeeServerInterfacePort?wsdl";
	
	private HibernateEngine _db ;
	
	
	List<Participant> participants = new ArrayList<Participant>();
	List<Role> roles = new ArrayList<Role>();
	List<Capability> capabilities = new ArrayList<Capability>();
	List<OrgGroup> orgGroups = new ArrayList<OrgGroup>();
	List<Position> positions = new ArrayList<Position>();
	
	public List<Participant> getParticipants() {
		return participants;
	}
	public void setParticipants(List<Participant> participants) {
		this.participants = participants;
	}
	public List<Role> getRoles() {
		return roles;
	}
	public void setRoles(List<Role> roles) {
		this.roles = roles;
	}
	public List<Capability> getCapabilities() {
		return capabilities;
	}
	public void setCapabilities(List<Capability> capabilities) {
		this.capabilities = capabilities;
	}
	public List<OrgGroup> getOrgGroups() {
		return orgGroups;
	}
	public void setOrgGroups(List<OrgGroup> orgGroups) {
		this.orgGroups = orgGroups;
	}
	public List<Position> getPositions() {
		return positions;
	}
	public void setPositions(List<Position> positions) {
		this.positions = positions;
	}
	
	String result;
	
	public String getResult() {
		return result;
	}
	public void setResult(String result) {
		this.result = result;
	}
	
	
	public List<Participant> loadExternalParticipants(){
		participants.clear();
		try
		{
	          Service service = new Service();
	          Call call = (Call) service.createCall();
	          call.setTargetEndpointAddress(endpoint);
	          call.setOperationName(new QName("http://server.com/", "getAllEmployees")); //WSDL里面描述的接口名称
	          call.setReturnType(org.apache.axis.encoding.XMLType.XSD_STRING);//设置返回类型  
	          result = (String)call.invoke(new Object[]{});
	          //给方法传递参数，并且调用方法
	          System.out.println(result+"///22222");
	          
	          JSONArray json = JSONArray.fromObject(result);
			  Map<String ,String> mp=new HashMap<String,String>();
		      if(json.size()>0){
		    	  for(int i=0;i<json.size();i++){// 閬嶅巻 jsonarray 鏁扮粍锛屾妸姣忎竴涓璞¤浆鎴?json 瀵硅薄
		             JSONObject job = json.getJSONObject(i); 
		             if(job.isEmpty() == false){
		            	 Participant p = new Participant();
			             p.setID(job.getString("empid"));
			             p.setUserID(job.getString("userid"));
			             p.setPassword(job.getString("password"));
			             p.setFirstName(job.getString("FirstName"));
			             p.setLastName(job.getString("LastName"));
			             if(job.containsKey("Administrator") && Integer.parseInt(job.getString("Administrator")) == 1){
			            	 p.setAdministrator(true);
			             }else{
			            	 p.setAdministrator(false);
			             }
			             
			             participants.add(p);
					 }
		             
		      	  }
		      }
		}
		catch (Exception e) 
		{
			System.err.println(e.toString());
			return participants;
		}
	    return participants;
	}
	
	public List<Role> loadExternalRoles(){
		roles.clear();
		/*try
		{
	          Service service = new Service();
	          Call call = (Call) service.createCall();
	          call.setTargetEndpointAddress(endpoint);
	          call.setOperationName(new QName("http://server.com/", "getAllRoles")); //WSDL里面描述的接口名称
	          call.setReturnType(org.apache.axis.encoding.XMLType.XSD_STRING);//设置返回类型  
	          result = (String)call.invoke(new Object[]{});
	          //给方法传递参数，并且调用方法
	          System.out.println(result+"///");
	          
	          
	          JSONArray json = JSONArray.fromObject(result);
			  Map<String ,String> mp=new HashMap<String,String>();
		      if(json.size()>0){
		    	  for(int i=0;i<json.size();i++){// 閬嶅巻 jsonarray 鏁扮粍锛屾妸姣忎竴涓璞¤浆鎴?json 瀵硅薄
		             JSONObject job = json.getJSONObject(i); 
		             if(job.isEmpty() == false){
		            	 Role role = new Role();
			             role.setID(job.getString("roleid"));
			             role.setName(job.getString("rolename"));
			             role.setDescription(job.getString("roledesc"));
			 			 roles.add(role);
					 }
		             
		      	  }
		      }
		}
		catch (Exception e) 
		{
			System.err.println(e.toString());
			return roles;
		}*/
        roles = _db.getObjectsForClass(Role.class.getName()) ;
	    return roles;
	}
	
	public List<Capability> loadExternalCapabilities(){
		capabilities.clear();
		try
		{
	          Service service = new Service();
	          Call call = (Call) service.createCall();
	          call.setTargetEndpointAddress(endpoint);
	          call.setOperationName(new QName("http://server.com/", "getAllCapacities")); //WSDL里面描述的接口名称
	          call.setReturnType(org.apache.axis.encoding.XMLType.XSD_STRING);//设置返回类型  
	          result = (String)call.invoke(new Object[]{});
	          //给方法传递参数，并且调用方法
	          System.out.println(result+"///");
	          
	          JSONArray json = JSONArray.fromObject(result);
			  Map<String ,String> mp=new HashMap<String,String>();
			  if(json.size()>0){
				  for(int i=0;i<json.size();i++){// 閬嶅巻 jsonarray 鏁扮粍锛屾妸姣忎竴涓璞¤浆鎴?json 瀵硅薄
					  JSONObject job = json.getJSONObject(i); 
					  if(job.isEmpty() == false){
						  Capability cap = new Capability();
				          cap.setID(job.getString("capid"));
				          cap.setCapability(job.getString("capname"));
				          cap.setDescription(job.getString("capdesc"));
				          capabilities.add(cap);
					  }
			          	 
			      }
	          }
		}
		catch (Exception e) 
		{
			System.err.println(e.toString());
			return capabilities;
		}
	    return capabilities;
	}
	
	public List<OrgGroup> loadExternalOrgGroups(){
		orgGroups.clear();
		try
		{
	          Service service = new Service();
	          Call call = (Call) service.createCall();
	          call.setTargetEndpointAddress(endpoint);
	          call.setOperationName(new QName("http://server.com/", "getAllOrgs")); //WSDL里面描述的接口名称
	          call.setReturnType(org.apache.axis.encoding.XMLType.XSD_STRING);//设置返回类型  
	          result = (String)call.invoke(new Object[]{});
	          //给方法传递参数，并且调用方法
	          System.out.println(result+"///");
	          
	          JSONArray json = JSONArray.fromObject(result);
			  Map<String ,String> mp=new HashMap<String,String>();
		      if(json.size()>0){
		    	  for(int i=0;i<json.size();i++){// 閬嶅巻 jsonarray 鏁扮粍锛屾妸姣忎竴涓璞¤浆鎴?json 瀵硅薄
		             JSONObject job = json.getJSONObject(i); 
		             if(job.isEmpty() == false){
		            	 OrgGroup org = new OrgGroup();
			             org.setID(job.getString("orgid"));
			             org.setGroupName(job.getString("orgname"));
			             org.setGroupType(job.getString("orgtype"));
			             org.setDescription(job.getString("orgdesc"));
			             if(job.containsKey("parentorgid")){
			            	 org.setBelongsTo(job.getString("parentorgid"));
			             }
			             orgGroups.add(org);
					 } 
		      	  }
		      }
		}
		catch (Exception e) 
		{
			e.printStackTrace();
			return orgGroups;
		}
	    return orgGroups;
	}
	
	
	
	public List<Position> loadExternalPositions(){
		positions.clear();
		try
		{
	          Service service = new Service();
	          Call call = (Call) service.createCall();
	          call.setTargetEndpointAddress(endpoint);
	          call.setOperationName(new QName("http://server.com/", "getAllPositions")); //WSDL里面描述的接口名称
	          call.setReturnType(org.apache.axis.encoding.XMLType.XSD_STRING);//设置返回类型  
	          result = (String)call.invoke(new Object[]{});
	          //给方法传递参数，并且调用方法
	          System.out.println(result+"///");
	          
	          
	          JSONArray json = JSONArray.fromObject(result);
			  Map<String ,String> mp=new HashMap<String,String>();
		      if(json.size()>0){
		    	  for(int i=0;i<json.size();i++){// 閬嶅巻 jsonarray 鏁扮粍锛屾妸姣忎竴涓璞¤浆鎴?json 瀵硅薄
		             JSONObject job = json.getJSONObject(i); 
		             if(job.isEmpty() == false){
		            	 Position pos = new Position();
			             pos.setID(job.getString("posid"));
			             pos.setTitle(job.getString("posname"));
			             pos.setDescription(job.getString("posdesc"));
			             //System.out.print(job.getString("orgid"));
			             if(job.containsKey("orgid")){
			            	 pos.setOrgGroup(job.getString("orgid"));
			             }
			             positions.add(pos);
					 } 
		      	  }
		      }
		}
		catch (Exception e) 
		{
			e.printStackTrace();
			return positions;
		}
	    return positions;
	}

}
