package com.iopipe;

/**
 * This class is used to setup configurations which are solely used for testing
 * purposes only.
 *
 * @since 2017/12/13
 */
final class __MockConfiguration__
{
	/** This is the token which is considered to be valid for the service. */
	public static final String VALID_TOKEN =
		"ThisIsNotARealIOPipeTokenAndIsUsedForTesting";
	
	/**
	 * Not used.
	 *
	 * @since 2017/12/13
	 */
	private __MockConfiguration__()
	{
	}
	
	/**
	 * Returns a new test configuration, one which is always enabled.
	 *
	 * @return A new test configuration.
	 * @since 2017/12/13
	 */
	public static final IOPipeConfiguration testConfig()
	{
		return __MockConfiguration__.testConfig(true);
	}
	
	/**
	 * Returns a new test configuration.
	 *
	 * @param __enabled A flag which specifies if the test is enabled.
	 * @return A new test configuration.
	 * @since 2017/12/13
	 */
	public static final IOPipeConfiguration testConfig(boolean __enabled)
	{
		IOPipeConfigurationBuilder rv = new IOPipeConfigurationBuilder();
		
		rv.setEnabled(__enabled);
		rv.setDebugStream(System.err);
		rv.setProjectToken(__MockConfiguration__.VALID_TOKEN);
		rv.setHTTPConnectionFactory(new __MockHTTPConnectionFactory__());
		
		return rv.build();
	}
}
