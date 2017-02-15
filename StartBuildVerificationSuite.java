package com.aexp.cicdmainframe.hpuftintegration.bvssuite;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import com.aexp.cicdmainframe.hpuftintegration.HPUFTIntegrationBase;
import com.aexp.cicdmainframe.hpuftintegration.HPUFTIntegrationUtil;
import com.aexp.cicdmainframe.hpuftintegration.PropertiesCache;
import com.aexp.cicdmainframe.hpuftintegration.authetication.AuthenticationDetails;
import com.aexp.cicdmainframe.hpuftintegration.exception.HPUFTIntegrationException;
import com.sun.jersey.api.client.AsyncWebResource;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;

public class StartBuildVerificationSuite extends HPUFTIntegrationBase {
	
		
	private static StartBuildVerificationSuite buildVerificationSuite;
	
	
	
	private String execute(Properties inputProperties, Properties outputProperties) {	
		
		PropertiesCache porpCache = PropertiesCache.getInstance();		
		String domainName = inputProperties.getProperty(DOMAIN_NAME);	
		String projectName = inputProperties.getProperty(PROJECT_NAME);
				
		if(domainName!=null && projectName!=null ) {
			 System.out.println(" domain :: "+domainName+"\n project :: "+projectName);
			if(porpCache.containsKey(DOMAIN_NAME) == false){
				porpCache.setProperty(DOMAIN_NAME, domainName);
			}
			if(porpCache.containsKey(PROJECT_NAME) == false){
				porpCache.setProperty(PROJECT_NAME, projectName);
			}
			
		} else {			
			throw new HPUFTIntegrationException("Domian or Project is null");			
		}
		String bvsRunStatus = null;
		
		try {
			//1. Authentication: get Authentication 
			System.out.println("Step 1. Authentication---------------: ");
			AuthenticationDetails authenticationDetails = AuthenticationDetails.getInstance();
			Map<String, String> authentcateDetailsMap = authenticationDetails.getAuthenticateDetails();	
			
			String bvsRunId =null;
			/* 7. Get the BVS Run Id from the Output Properties  */
			bvsRunId = (String) outputProperties.get("BVS_RUN_ID");
			System.out.println("bvsRunId.........."+bvsRunId);
			
			/* 7. Invoking the  Build Verification Suite Execution  Service */		
			BuildVerificationSuiteExecutionResponse buildVerificationSuiteExecutionResponse = null;	
			BuildVerificationSuiteExecution bvsExecution = BuildVerificationSuiteExecution.getInstance();
			/* Get Status of Build Verification Suite Execution 
			 * <Field Name="state"><Value>Finished</Value>
			 * <Field Name="completed-successfully"><Value>N</Value>
			 *  */
			if(bvsRunId!=null) {
				buildVerificationSuiteExecutionResponse = bvsExecution.doGetBuildVerificationSuiteExecutionStatus(domainName, projectName, bvsRunId);
				/* Read the Status of the Procedure/BVS runId from the Response received from buildVerificationSuiteExecution Service.
				 * And Store in the Output properties file: ("BVS_EXECUTION_STATUS", statusValue)
				 *   */
				if(buildVerificationSuiteExecutionResponse!=null && buildVerificationSuiteExecutionResponse.getEntity()!=null) {
					String buildVerificationSuite_status = null;
					Entity entity = buildVerificationSuiteExecutionResponse.getEntity();
					Fields fields = entity.getFields();
					if (fields!=null) {
						for(Field field : fields.getField()) {
							if(field.getName().equalsIgnoreCase("state")) {
								buildVerificationSuite_status = field.getValue();							
								System.out.println("buildVerificationSuiteExecutionStatus >> state >> field name "+field.getName());
								System.out.println("buildVerificationSuiteExecutionStatus >> state >> field value "+field.getValue());							
								outputProperties.put("BVS_EXECUTION_STATE", field.getValue());							
							} else if(field.getName().equalsIgnoreCase("completed-successfully")) {
								buildVerificationSuite_status = field.getValue();							
								System.out.println("buildVerificationSuiteExecutionStatus >> status >> field name "+field.getName());
								System.out.println("buildVerificationSuiteExecutionStatus >> status >> field value "+field.getValue());
								outputProperties.put("BVS_EXECUTION_STATUS", buildVerificationSuite_status);
							}
						}
					}
				}
				BuildVerificationRunDetailsResponse buildVerificationRunDetailsResponse = null;			
				String buildVerificationRun_status = null;	
				BuildVerificationRunDetails buildVerificationRunDetails = BuildVerificationRunDetails.getInstance();
				
				buildVerificationRunDetailsResponse = buildVerificationRunDetails.doGetBuildVerificationRunDetails(domainName, projectName, bvsRunId);
				/* Read the RUN Status of the Procedure/BVS runId from the Response received from Build Verification Run DETAILS Service.
				 * And Store in the Output properties file: ("BVS_RUN_STATUS", statusValue)
				 *   */
				if(buildVerificationRunDetailsResponse!=null) {
					Entities entities = buildVerificationRunDetailsResponse.getEntities();
					System.out.println("Total Results   "+entities.getTotalResults());
					outputProperties.put("TOTAL_ENTITIES", entities.getTotalResults());
					List<Entity> entityList = entities.getEntity();
					if (entityList!=null) {
						for(int i =0; i<entityList.size(); i++) {
							Entity e = entityList.get(i);
							System.out.println("Entity Type  "+e.getType());
							if(e.getFields()!=null) {
								Fields fields = e.getFields();								
								for(Field field : fields.getField()) {    
							        System.out.println(" Build Verification Run  field name "+field.getName());
							        System.out.println(" Build Verification Run field value "+field.getValue());
							        buildVerificationRun_status = field.getValue();
							        if(field.getName().equalsIgnoreCase("state")) {
							        bvsRunStatus = buildVerificationRun_status;								        
							        outputProperties.put("ENTITY_"+(i+1)+"_BVS_RUN_STATUS", field.getValue());
							        break;
							        }										
								}
							}							
						}
					}
				}
			} else {				
				throw new HPUFTIntegrationException("BVS RUN ID is not available..");
			}
			
		}catch(Exception e) {
			throw new HPUFTIntegrationException("Exception while processing execute() in StartBuildVerificationSuite.."+e);
		
		}
		return bvsRunStatus;
	}
	
	

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
		try {			
			if(args.length == 3) {				
				System.setProperty("certificateDetailsPath", args[2]);
			}
			PropertiesCache porpCache = PropertiesCache.getInstance();
			
			
			inputProperties.load(new FileInputStream(new File(inputPropertiesPath)));
			outputProperties = porpCache.readPropertiesFile(outputPropertiesPath);
			
			buildVerificationSuite = new StartBuildVerificationSuite();	
			buildVerificationSuite.execute(inputProperties, outputProperties);
			
			System.out.println("StartBuildVerificationSuite Main Ends..");
		} catch (Exception e) {	
			outputProperties.put("StartBuildVerificationSuiteException", e.getMessage());
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


	/**
	 * BuildVerificationSuiteExecutionResponse doGetBuildVerificationSuiteExecutionStatus(String domainName, String projectName, String bvsId)  
	 * throws RuntimeException 
	 * This method  takes DomainName, ProjectName and BvsRunId
	 * and Invokes the Build Verification Suite Execution Service  
	 * Response contains Entities and its Fields
	 * Value of Field “completed-successfully” can be used to get the status of Build Verification Suite execution 
	 * 
	 * 
	 * @param domainName
	 * @param projectName
	 * @param bvsRunId
	 * @return BuildVerificationSuiteExecutionResponse
	 * @throws RuntimeException
	 */
	public BuildVerificationSuiteExecutionResponse doGetBuildVerificationSuiteExecutionStatus(String domainName, String projectName, String bvsRunId)  throws RuntimeException {		
		System.out.println("=======Step 7. BuildVerificationSuiteExecution==================");
		BuildVerificationSuiteExecutionResponse buildVerificationSuiteResponse = null;
		String bvsURL = PropertiesCache.getInstance().getProperty(BUILD_VERIFICATION_URL);		
		String cookieValue = authenticationMap.get(LWSSO_COOKIE_VALUE);			
		String credentials = authenticationMap.get(CREDENTIALS);
		System.out.println("BuildVerificationSuiteExecution->domainName "+domainName);
		System.out.println("BuildVerificationSuiteExecution->projectName "+projectName);
		System.out.println("BuildVerificationSuiteExecution->bvsRunId "+bvsRunId);	

		//Client restClient = Client.create();
		ClientConfig clientConfig = new DefaultClientConfig();
		
		/* get the  SSL context 
		 * and set the SSLContext rest client*/
		SSLContext context =HPUFTIntegrationUtil.getInstance().getSSLContext();
		HostnameVerifier hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
		clientConfig.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, new HTTPSProperties(hostnameVerifier, context));		
		Client restClient = Client.create(clientConfig);		
		
		/*Invoking the Service.. */
		AsyncWebResource webResource = restClient.asyncResource(bvsURL)
				.path("domains").path(domainName)
				.path("projects").path(projectName)
				.path("procedure-runs").path(bvsRunId);
				
		System.out.println("BuildVerificationSuiteExecution URL: " + webResource.getURI().toString());  
		/*try {
			Thread.sleep(100000);
		} catch (InterruptedException e2) {			
			e2.printStackTrace();
		}*/
		ClientResponse response;
		try {
			response = webResource.accept("application/xml")
					.header("Authorization", "Basic " + credentials)
					.header("Cookie",cookieValue)
					.get(ClientResponse.class).get();
		
		System.out.println("response.getStatus()..."+response.getStatus());
		/*Read the response from  Service.. */
		if(response.getStatus() != 200){
			System.err.println("Unable to connect to the server: response status:"+response.getStatus());
			//logger.error("Unable to connect to the server"+response.getStatus());
			throw new RuntimeException("BuildVerificationSuiteExecution Unable to connect to the server"+response.getStatus());
		}
		//read the data 
		if(response.getStatus()== ClientResponse.Status.OK.getStatusCode()) {
			try {
				String responseInXMLString = response.getEntity(String.class);
				Entity entity = null;
				try {
					JAXBContext jaxbContext = JAXBContext.newInstance(Entity.class);
					Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
					StringReader reader = new StringReader(responseInXMLString);
					StreamSource streamSource = new StreamSource(reader);
					JAXBElement<Entity> je = unmarshaller.unmarshal(streamSource, Entity.class);
					entity = (Entity) je.getValue();
				} catch (JAXBException jaxbe) {					
					//logger.error("Exception while unmarshalling the response",jaxb);
				}
				System.out.println("BuildVerificationSuiteExecution entity....."+entity);
				if(entity!=null) {
					System.out.println("entity type....."+entity.getType());
					buildVerificationSuiteResponse = new BuildVerificationSuiteExecutionResponse();
					buildVerificationSuiteResponse.setEntity(entity);
					Fields fields = entity.getFields();
					
					if (fields!=null) {
						for(Field field : fields.getField()) {
							System.out.println("field name "+field.getName());
							System.out.println("field value "+field.getValue());
						}
					}
				}
			} catch (Exception e) {
				
			}
		}
		} catch (InterruptedException e1) {			
			e1.printStackTrace();
		} catch (ExecutionException e1) {			
			e1.printStackTrace();
		}
		return buildVerificationSuiteResponse;		
	}

}
