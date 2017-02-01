package com.aexp.cicdmainframe.hpuftintegration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.apache.http.HttpStatus;

import com.aexp.cicdmainframe.hpuftintegration.authetication.AuthenticationDetails;
import com.aexp.cicdmainframe.hpuftintegration.hpalmdomain.BuildVerificationRunDetails;
import com.aexp.cicdmainframe.hpuftintegration.hpalmdomain.BuildVerificationRunDetailsResponse;
import com.aexp.cicdmainframe.hpuftintegration.hpalmdomain.BuildVerificationSuiteExecution;
import com.aexp.cicdmainframe.hpuftintegration.hpalmdomain.BuildVerificationSuiteExecutionResponse;
import com.aexp.cicdmainframe.hpuftintegration.hpalmdomain.Entities;
import com.aexp.cicdmainframe.hpuftintegration.hpalmdomain.Entity;
import com.aexp.cicdmainframe.hpuftintegration.hpalmdomain.Field;
import com.aexp.cicdmainframe.hpuftintegration.hpalmdomain.Fields;
import com.aexp.cicdmainframe.hpuftintegration.hpalmdomain.StartRunProcedureResponse;
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
		try {
			inputProperties.load(new FileInputStream(new File(inputPropertiesPath)));
			if(args.length == 3) {				
				System.setProperty("certificateDetailsPath", args[2]);
			}
			StartRunProcedure hpuftintegration = new StartRunProcedure();
			String bvsExecutionStatus = hpuftintegration.execute(inputProperties, outputProperties);
			System.out.println("build Verification Run Status....."+bvsExecutionStatus);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} 
		OutputStream outputFile;
		try {
			outputFile = new FileOutputStream(outputPropertiesPath);
			outputProperties.store(outputFile, null);
		} catch (IOException e) {		
			e.printStackTrace();
		}
	}
	public String execute(Properties inputProperties, Properties outputProperties)  throws RuntimeException {
		PropertiesCache porpCache = PropertiesCache.getInstance();
		String domainName = inputProperties.getProperty(DOMAIN_NAME);	
		String projectName = inputProperties.getProperty(PROJECT_NAME);
		String bvsId = inputProperties.getProperty("bvsid");		
		if(domainName!=null && projectName!=null && bvsId!=null ) {
			 System.out.println(" domain :: "+domainName+"\n project :: "+projectName+"\n bvsid :: "+bvsId);
			if(porpCache.containsKey(DOMAIN_NAME) == false){
				porpCache.setProperty(DOMAIN_NAME, domainName);
			}
			if(porpCache.containsKey(PROJECT_NAME) == false){
				porpCache.setProperty(PROJECT_NAME, projectName);
			}
			if(porpCache.containsKey("bvsId") == false){
				porpCache.setProperty("bvsId", bvsId);
			}
		} else {
			System.out.println(" Setting domain, project and BvsId From Else..");
			domainName ="DEVOPS";
			projectName ="DevOps_LARA_Integration";
			bvsId = "1004";			
		}		
		//1. Authentication: get Authentication 
		System.out.println("Step 1. Authentication---------------: ");
		AuthenticationDetails authenticationDetails = AuthenticationDetails.getInstance();		
		Map<String, String> authentcateDetailsMap = authenticationDetails.getAuthenticateDetails();		
		String bvsExecutionStatus = null;	
		// 6. Invoking Start Run Procedure Service
		StartRunProcedureResponse startRunProcedureResponse = doStartRunProcedure(domainName, projectName, bvsId);
		
		/* 7. Invoking the  Build Verification Suite Execution  Service */
		
		BuildVerificationSuiteExecutionResponse buildVerificationSuiteExecutionResponse = null;		
		if(startRunProcedureResponse!=null && startRunProcedureResponse.getEntity()!=null) {			 
			String bvsRunId =null;
			/* 7. Get the BVS Run Id from the Start Run Procedure service Response  */
			bvsRunId = getBvsRunIdFromStartRunProcedureResponse(startRunProcedureResponse);
			 System.out.println("bvsRunId.........."+bvsRunId);
			/* Get Status of Build Verification Suite Execution 
			 * <Field Name="state"><Value>Finished</Value>
			 * <Field Name="completed-successfully"><Value>N</Value>
			 *  */
			BuildVerificationSuiteExecution bvsExecution = BuildVerificationSuiteExecution.getInstance();
			if(bvsRunId!=null) {
				buildVerificationSuiteExecutionResponse = bvsExecution.doGetBuildVerificationSuiteExecutionStatus(domainName, projectName, bvsRunId);// passing bvsRun id 
			} else {
				buildVerificationSuiteExecutionResponse = bvsExecution.doGetBuildVerificationSuiteExecutionStatus(domainName, projectName, bvsId);
			}
			/* Read the Status of the Procedure/BVS runId from the Response received from buildVerificationSuiteExecution Service.
			 * And Store in the Output properties file: ("BVS_EXECUTION_STATUS", statusValue)
			 *   */
			BuildVerificationRunDetailsResponse buildVerificationRunDetailsResponse = null;
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
							outputProperties.put("BVS_EXECUTION_STATUS", field.getValue());
						}
					}
				}
			}
			
			/*  8. Invoking the Get Build Verification Run DETAILS Service... */
			String buildVerificationRun_status = null;	
			BuildVerificationRunDetails buildVerificationRunDetails = BuildVerificationRunDetails.getInstance();
			if(bvsRunId!=null) {
				buildVerificationRunDetailsResponse = buildVerificationRunDetails.doGetBuildVerificationRunDetails(domainName, projectName, bvsRunId);
			} else {
				buildVerificationRunDetailsResponse = buildVerificationRunDetails.doGetBuildVerificationRunDetails(domainName, projectName, bvsId);
			}
			/* Read the RUN Status of the Procedure/BVS runId from the Response received from Build Verification Run DETAILS Service.
			 * And Store in the Output properties file: ("BVS_RUN_STATUS", statusValue)
			 *   */
			if(buildVerificationRunDetailsResponse!=null) {
				Entities enties = buildVerificationRunDetailsResponse.getEntities();
				System.out.println("Total Results   "+enties.getTotalResults());
				List<Entity> entity = enties.getEntity();
				if (entity!=null) {
					for(Entity e : entity) {
						System.out.println("Entity Type  "+e.getType());
						if(e.getFields()!=null) {
							Fields fields = e.getFields();								
							for(Field field : fields.getField()) {
								if(field.getName().equalsIgnoreCase("state")) {
									System.out.println(" Build Verification Run  field name "+field.getName());
									System.out.println(" Build Verification Run field value "+field.getValue());
									buildVerificationRun_status = field.getValue();
									bvsExecutionStatus = buildVerificationRun_status;
									outputProperties.put("BVS_RUN_STATUS", field.getValue());
								}										
							}
						}							
					}
				}
			}
		}
		return bvsExecutionStatus;
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
	public StartRunProcedureResponse doStartRunProcedure(String domainName,String projectName,String bvsId) {
		System.out.println("====== 6. StartRunProcedure for Domain, Project and Entity/BVSId=====");
		StartRunProcedureResponse startRunProcedureResponse = null;
		String startRunProcedureURL = PropertiesCache.getInstance().getProperty(START_RUN_PROCEDURE_URL);
		String cookieValue = authenticationMap.get(LWSSO_COOKIE_VALUE);			
		String credentials = authenticationMap.get(CREDENTIALS);
		System.out.println("StartRunProcedure->domainName "+domainName);
		System.out.println("StartRunProcedure->projectName "+projectName);
		System.out.println("StartRunProcedure->bvsId "+bvsId);
		
		Entity requestEntity = HPUFTIntegrationUtil.getInstance().createRequestEntity();
		//requestEntity.setType(bvsId);
		
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

		if(response.getStatus() != HttpStatus.SC_CREATED){
			System.err.println("Unable to connect to the server");
		}
		//read the response data 
		if(response.getStatus()==201 || response.getStatus()== HttpStatus.SC_OK) {
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
