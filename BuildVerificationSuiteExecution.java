package com.cicdmainframe.hpuftintegration.hpalmdomain;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.apache.http.HttpStatus;
 import org.apache.log4j.Logger;
 import org.apache.log4j.Logger;

import com.cicdmainframe.hpuftintegration.HPUFTIntegrationBase;
import com.cicdmainframe.hpuftintegration.HPUFTIntegrationUtil;
import com.cicdmainframe.hpuftintegration.PropertiesCache;
import com.cicdmainframe.hpuftintegration.authetication.AuthenticationDetails;
import com.sun.jersey.api.client.AsyncWebResource;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;

public class BuildVerificationSuiteExecution extends HPUFTIntegrationBase{

	private static BuildVerificationSuiteExecution buildVerificationSuite;
	//static final Logger logger = Logger.getLogger(BuildVerificationSuiteExecution.class);

	private BuildVerificationSuiteExecution() {
		
	}
	public static BuildVerificationSuiteExecution getInstance() {
		if(buildVerificationSuite==null)
			buildVerificationSuite= new BuildVerificationSuiteExecution();
		return buildVerificationSuite;
	}

	
	
	public BuildVerificationSuiteExecutionResponse doBuildVerification(String domainName,String projectName,Entity reqEntity) {
		
		String bvsId = HPUFTIntegrationUtil.getInstance().getBVSId(reqEntity);
		BuildVerificationSuiteExecutionResponse buildVerificationSuiteResponse = doGetBuildVerificationSuiteExecutionStatus(domainName,projectName, bvsId);		
		return buildVerificationSuiteResponse;		
	}
	
	public BuildVerificationSuiteExecutionResponse doGetBuildVerificationSuiteExecutionStatus(String domainName, String projectName, String bvsId)  throws RuntimeException {		
		System.out.println("=======Step 7. BuildVerificationSuiteExecution==================");
		BuildVerificationSuiteExecutionResponse buildVerificationSuiteResponse = null;
		String bvsURL = PropertiesCache.getInstance().getProperty(BUILD_VERIFICATION_URL);		
		String cookieValue = authenticationMap.get(LWSSO_COOKIE_VALUE);			
		String credentials = authenticationMap.get(CREDENTIALS);
		//System.out.println("Base64 encoded auth string: " + credentials);
		//System.out.println("BuildVerificationSuiteExecution->base64CredBytes "+credentials);
		//System.out.println("BuildVerificationSuiteExecution->cookieValue "+cookieValue);
		System.out.println("BuildVerificationSuiteExecution->domainName "+domainName);
		System.out.println("BuildVerificationSuiteExecution->projectName "+projectName);
		System.out.println("BuildVerificationSuiteExecution->bvsId "+bvsId);	

		//Client restClient = Client.create();
		ClientConfig clientConfig = new DefaultClientConfig();			
		SSLContext context =HPUFTIntegrationUtil.getInstance().getSSLContext();
		HostnameVerifier hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
		clientConfig.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, new HTTPSProperties(hostnameVerifier, context));
		Client restClient = Client.create(clientConfig);		
		// connection Timeout 
				//restClient.setConnectTimeout(100000);
				//restClient.setReadTimeout(1000000);
		
		AsyncWebResource webResource = restClient.asyncResource(bvsURL)
				.path("domains").path(domainName)
				.path("projects").path(projectName)
				.path("procedure-runs").path(bvsId);
				
		System.out.println("BuildVerificationSuiteExecution URL: " + webResource.getURI().toString());  
		try {
			Thread.sleep(100000);
		} catch (InterruptedException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		ClientResponse response;
		try {
			response = webResource.accept("application/xml")
					.header("Authorization", "Basic " + credentials)
					.header("Cookie",cookieValue)
					.get(ClientResponse.class).get();
		
		System.out.println("response.getStatus()..."+response.getStatus())
		if(response.getStatus() != 200){
			System.err.println("Unable to connect to the server: response status:"+response.getStatus());
			//logger.error("Unable to connect to the server"+response.getStatus());
			throw new RuntimeException("Unable to connect to the server"+response.getStatus());
		}
		//read the data 
		if(response.getStatus()== HttpStatus.SC_OK) {
			try {
				String responseInXMLString = response.getEntity(String.class);
				//responseInXMLString="<Entities TotalResults=\"9\"><Entity Type=\"Procedure\"><Fields><Field Name=\"is-valid\"><Value>N</Value></Field></Fields></Entity></Entities>";
				//System.out.println("BuildVerificationSuiteExecution response in String.. "+responseInXMLString);

				Entity entity = null;
				try {
					JAXBContext jaxbContext = JAXBContext.newInstance(Entity.class);
					Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
					StringReader reader = new StringReader(responseInXMLString);
					StreamSource streamSource = new StreamSource(reader);
					JAXBElement<Entity> je = unmarshaller.unmarshal(streamSource, Entity.class);
					entity = (Entity) je.getValue();
				} catch (JAXBException jaxbe) {					
					//logger.error("Exception while unmarshalling the response",jaxbe);
				}
				System.out.println("BuildVerificationSuiteExecution entity....."+entity);

				//Entity entity = (Entity)response.getEntity(Entity.class); //try commenting JAXB
				if(entity!=null) {
					System.out.println("entity type....."+entity.getType());
					buildVerificationSuiteResponse = new BuildVerificationSuiteExecutionResponse();
					buildVerificationSuiteResponse.setEntity(entity);
					Fields fields = entity.getFields();
					int count = 0;
					if (fields!=null) {
						for(Field field : fields.getField()) {
							System.out.println("field name "+field.getName());
							System.out.println("field value "+field.getValue());
							if(field.getName().equalsIgnoreCase("completed-successfully")) {
								if(field.getValue()!=null){
									System.out.println("total  repeat attempts.."+count);
									break;
								} else {
									count++;
									doGetBuildVerificationSuiteExecutionStatus(domainName,projectName, bvsId);
								}
							}
						}
					}
				}
			} catch (Exception e) {
				
			}
		}
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (ExecutionException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return buildVerificationSuiteResponse;		
	}
	private SSLContext getSSLContext() {
		SSLContext context = null;
		try {
			String pKeyFilePath = System.getProperty("keystore_file"); ///HPUFTIntegration/src/main/resources/epegtrustcert.pfx
			System.out.println("pKeyFilePath "+pKeyFilePath);
			String pKeyPassword = System.getProperty("keystore_password");			
			// Getting ClassLoader obj  
			ClassLoader classLoader = this.getClass().getClassLoader();  
			// Getting resource(File) from class loader  
			File configFile=new File(classLoader.getResource(pKeyFilePath).getFile());  
			System.out.println("configFile "+configFile.exists());
			KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
			//TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			KeyStore keyStore = KeyStore.getInstance("jks");
			// KeyStore keyStore = KeyStore.getInstance("jks");

			InputStream keyInput = new FileInputStream(configFile);
			keyStore.load(keyInput, pKeyPassword.toCharArray());
			keyInput.close();

			keyManagerFactory.init(keyStore, pKeyPassword.toCharArray());
			//tmf.init(keyStore);
			context = SSLContext.getInstance("TLS");
			context.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());
			//SSLContext ctx = SSLContext.getInstance("SSL");
			//ctx.init(null, tmf.getTrustManagers(), null);							
			return context;
			/* 
			 * SslConfigurator sslConfig = SslConfigurator.newInstance()
                .keyStore(keyStore)
                .keyStorePassword("mypin")
                .keyStoreType("PKCS11")
                .trustStoreFile(TRUSTORE_CLIENT_FILE)
                .trustStorePassword(TRUSTSTORE_CLIENT_PWD)
                .securityProtocol("TLS");
                final SSLContext sslContext = sslConfig.createSSLContext();
			 * 
			 * */
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return context;
	}
}
