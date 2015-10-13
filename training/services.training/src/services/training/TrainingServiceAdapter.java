package services.training;

import org.apache.log4j.Logger;

import com.servicemesh.core.reactor.TimerReactor;

/**
 * TODO If you are using eclipse to edit or build this project make sure you
 * create a classpath variable named LIB that points to the lib directory under
 * agility-platform-services-sdk 
 * 
 * Right click on your project. Click on Build Path -> Configure Build Path
 * Click Add Variable
 * Click Configure Variables...
 * Click New...
 * Set the name of the variable to LIB
 * Set the path of the variable to point to the lib folder in agility-platform-services-sdk
 *
 */


public class TrainingServiceAdapter {
	private static final Logger logger = Logger.getLogger(TrainingServiceAdapter.class);

	// 1. Change "my" to your name or initials. *_TYPE aka "database name"
	//	  while *_NAME aka "UI name"
	// 2. Use *_TYPE (aka the "database
	//	  name" for: a) Link setName(); b) AssetType setName()
	// 3. Use SERVICE_PROVIDER_NAME with: a) TimerReactor getTimerReactor();
	//	  b) RegistrationRequest setName(); c) ServiceProviderType setName();
	//	  d) provider AssetType setDisplayName()
	// 4. Use SERVICE_NAME for service AssetType setDisplayName()
	public static final String SERVICE_PROVIDER_TYPE = "my-service-provider";
	public static final String SERVICE_PROVIDER_NAME = "My Service Provider";
	public static final String SERVICE_PROVIDER_DESCRIPTION = "My Provider Description";
	public static final String SERVICE_TYPE = "my-service";
	public static final String SERVICE_NAME = "My Service";

	public TrainingServiceAdapter() throws Exception {
		//super(TimerReactor.getTimerReactor(SERVICE_PROVIDER_NAME));
		logger.info(SERVICE_PROVIDER_DESCRIPTION);
	}
	
}
