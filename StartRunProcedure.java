package com.aexp.cicdmainframe.hpuftintegration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.Map;
import java.util.Properties;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;


import com.aexp.cicdmainframe.hpuftintegration.authetication.AuthenticationDetails;
import com.aexp.cicdmainframe.hpuftintegration.bvssuite.Entity;
import com.aexp.cicdmainframe.hpuftintegration.bvssuite.Field;
import com.aexp.cicdmainframe.hpuftintegration.bvssuite.Fields;
import com.aexp.cicdmainframe.hpuftintegration.bvssuite.StartRunProcedureResponse;
import com.aexp.cicdmainframe.hpuftintegration.exception.HPUFTIntegrationException;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;

public class StartRunProcedure  extends HPUFTIntegrationBase {

	//static final Logger logger = Logger.getLogger(StartRunProcedure.class);
	
	public static void main(String[] args) {
		if (args.length < 2) {
			throw new RuntimeException(
					"Unexpected number of plugin arguments.  Expecting: InputPropertyFile OutputPropertyFile");
		}
		String inputPropertiesPath = args[0];
		String outputPropertiesPath = args[1];
		String certificateDetailsPath = null;
		
		Properties inputProperties = new Properties();
		Properties outputProperties = new Properties();
		StartRunProcedure hpuftintegration = null;
		try {			
			inputProperties.load(new FileInputStream(new File(inputPropertiesPath)));
			if(args.length == 3) {				
				System.setProperty("certificateDetailsPath", args[2]);
			}
			hpuftintegration= new StartRunProcedure();
			String bvsRunId = hpuftintegration.execute(inputProperties, outputProperties);
			System.out.println("Start Run Procedure Completed and BVS RunID....."+bvsRunId);
		} catch (Exception e) {
			outputProperties.put("Start_Run_Procedure_Exception", e.getMessage());
			e.printStackTrace();
		} 
		finally {
			OutputStream outputFile;
			try {
				outputFile = new FileOutputStream(outputPropertiesPath);
				outputProperties.store(outputFile, null);
			} catch (IOException e) {		
				e.printStackTrace();
			}
		}
		
	}
	public String execute(Properties inputProperties, Properties outputProperties)  throws HPUFTIntegrationException {
		PropertiesCache porpCache = PropertiesCache.getInstance();
		String domainName = inputProperties.getProperty(DOMAIN_NAME);	
		String projectName = inputProperties.getProperty(PROJECT_NAME);
		String bvsId = inputProperties.getProperty(BVS_ID);		
		if(domainName!=null && projectName!=null && bvsId!=null ) {
			 System.out.println(" domain :: "+domainName+"\n project :: "+projectName+"\n bvsid :: "+bvsId);
			if(porpCache.containsKey(DOMAIN_NAME) == false){
				porpCache.setProperty(DOMAIN_NAME, domainName);
			}
			if(porpCache.containsKey(PROJECT_NAME) == false){
				porpCache.setProperty(PROJECT_NAME, projectName);
			}
			if(porpCache.containsKey(BVS_ID) == false){
				porpCache.setProperty(BVS_ID, bvsId);
			}
		} else {
			
			throw new HPUFTIntegrationException("Domian or Project or BVSId is null");
			
		}
		String bvsRunId = null;
		try {
			//1. Authentication: get Authentication 
			System.out.println("Step 1. Authentication---------------: ");
			AuthenticationDetails authenticationDetails = AuthenticationDetails.getInstance();		
			Map<String, String> authentcateDetailsMap = authenticationDetails.getAuthenticateDetails();	

			// 6. Invoking Start Run Procedure Service
			StartRunProcedureResponse startRunProcedureResponse = doStartRunProcedure(domainName, projectName, bvsId);
			if(startRunProcedureResponse!=null && startRunProcedureResponse.getEntity()!=null) {
				/* 7. Get the BVS Run Id from the Start Run Procedure service Response  */
				bvsRunId = getBvsRunIdFromStartRunProcedureResponse(startRunProcedureResponse);
				System.out.println("bvsRunId.........."+bvsRunId);				
				if(bvsRunId!=null) {
					outputProperties.put("BVS_RUN_ID", bvsRunId);
				} else {				
					throw new HPUFTIntegrationException("BVS RUN ID is not available..\n"+startRunProcedureResponse.toString());
				}
			}		
		} catch (Exception e) {			
			throw new HPUFTIntegrationException("Exception while processing procedure.."+e.getMessage());
		}
		return bvsRunId;
	}
	
	/**
	 * doStartRunProcedure(String domainName,String projectName,String bvsId)
	 * This method takes domainName, projectName and BVSId,
	 * Invokes the startrunprocedure service to start the procedure 
	 * @param domainName
	 * @param projectName
	 * @param bvsId
	 * @return StartRunProcedureResponse
	 */
	public StartRunProcedureResponse doStartRunProcedure(String domainName,String projectName,String bvsId) throws HPUFTIntegrationException {
		System.out.println("====== 6. StartRunProcedure for Domain, Project and BVSId=====");
		StartRunProcedureResponse startRunProcedureResponse = null;
		String startRunProcedureURL = PropertiesCache.getInstance().getProperty(START_RUN_PROCEDURE_URL);
		String cookieValue = authenticationMap.get(LWSSO_COOKIE_VALUE);			
		String credentials = authenticationMap.get(CREDENTIALS);
		System.out.println("StartRunProcedure->domainName "+domainName);
		System.out.println("StartRunProcedure->projectName "+projectName);
		System.out.println("StartRunProcedure->bvsId "+bvsId);
		
		Entity requestEntity = HPUFTIntegrationUtil.getInstance().createRequestEntity();
	
		ClientConfig clientConfig = new DefaultClientConfig();
	
		/* get the  SSL context 
		 * and set the SSLContext rest client*/
		SSLContext context = HPUFTIntegrationUtil.getInstance().getSSLContext();		
		HostnameVerifier hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();			
		clientConfig.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, new HTTPSProperties(hostnameVerifier, context));		
		
		Client restClient = Client.create(clientConfig);
		/*Invoking the Service.. */
		WebResource webResource = restClient.resource(startRunProcedureURL)
				.path("domains").path(domainName)
				.path("projects").path(projectName)
				.path("procedures").path(bvsId)
				.path("startrunprocedure"); 
		System.out.println("StartRunProcedure URL: " + webResource.getURI().toString());
		System.out.println("StartRunProcedure request: " + requestEntity.toString());
		
		/*Read the response from  Service.. */
		ClientResponse response = webResource.accept("application/xml")
				.header("Authorization", "Basic " + credentials)
				.header("Cookie",cookieValue)
				.post(ClientResponse.class, requestEntity);	

		if(response.getStatus() != ClientResponse.Status.CREATED.getStatusCode()){
			throw new RuntimeException(" StartRunProcedure Unable to connect to the server"+response.getStatus());
		}
		//read the response data 
		if(response.getStatus()==201 || response.getStatus()== ClientResponse.Status.OK.getStatusCode()) {
			try {	
				//unmarshalling the response(Entity) xml into Java Object;
				String responseInXMLString = response.getEntity(String.class);				
				System.out.println("Get StartRunProcedure response in String.. "+responseInXMLString);
				JAXBContext jaxbContext = JAXBContext.newInstance(Entity.class);
				Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
				StringReader reader = new StringReader(responseInXMLString);
				StreamSource streamSource = new StreamSource(reader);
				JAXBElement<Entity> je = unmarshaller.unmarshal(streamSource, Entity.class);
				Entity entityResponse  = (Entity) je.getValue();			
				System.out.println("StartRunProcedure Response....."+entityResponse.getType());				
				
				if(entityResponse!=null) {
					//System.out.println("entity type....."+entityResponse.getType());					
					startRunProcedureResponse = new StartRunProcedureResponse();
					startRunProcedureResponse.setEntity(entityResponse);
					Fields fields = entityResponse.getFields();
					
					if (fields!=null) {
						fields.getField().size();					
						for(Field field : fields.getField()) {
							System.out.println("field name "+field.getName());
							System.out.println("field value "+field.getValue());
							
						}
					}
				}	
			} catch (Exception e) {
				System.out.println("Exception in Start Run Procedure..");
				e.printStackTrace();
			}
		}
		return startRunProcedureResponse;
	}
	
	/**
	 * get the BVS Run Id from the StartRunProcedureResponse
	 * It iterates the entities available in StartRunProcedureResponse and find the BvsRunId
	 * @param startRunProcedureResponse
	 * @return bvsRunId
	 */
	private String getBvsRunIdFromStartRunProcedureResponse(StartRunProcedureResponse startRunProcedureResponse) {
		String bvsRunId = null;
		if(startRunProcedureResponse!=null && startRunProcedureResponse.getEntity()!=null) {
			Entity entity = startRunProcedureResponse.getEntity();
			if(entity!=null) {
				Fields fields = entity.getFields();			
				if (fields!=null) {
					fields.getField().size();
					for(Field field : fields.getField()) {
						if("info".equalsIgnoreCase(field.getName())) {
							bvsRunId = field.getValue();
							break;
						}					
					}
				}
			}
		}
		return bvsRunId;
	}
	
}
