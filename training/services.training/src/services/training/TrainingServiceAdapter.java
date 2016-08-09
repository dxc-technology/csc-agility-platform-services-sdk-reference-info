package services.training;

import org.apache.log4j.Logger;

import com.servicemesh.core.reactor.TimerReactor;

/**
 * TODO If you are using eclipse to edit or build this project make sure you
 * install the IvyDE plugin. 
 * Apache Ivy update site - http://www.apache.org/dist/ant/ivyde/updatesite
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
