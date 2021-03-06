package com.iopipe;

import com.iopipe.generic.ObjectTranslator;
import com.iopipe.http.RemoteRequest;
import com.iopipe.http.RemoteResult;
import java.lang.reflect.Type;
import java.util.Map;
import javax.json.JsonString;
import javax.json.JsonNumber;
import javax.json.JsonValue;
import org.pmw.tinylog.Logger;

/**
 * Tests that the generic object translator works properly and is able to
 * translates objects to other types.
 *
 * @since 2018/08/20
 */
class __DoGenericObjectTranslate__
	extends Single
{
	/** Sent with no exception? */
	protected final BooleanValue noerror =
		new BooleanValue("noerror");
		
	/** Got a result from the server okay? */
	protected final BooleanValue remoterecvokay =
		new BooleanValue("remoterecvokay");
	
	/** The JSON file. */
	protected final String file;
	
	/** The to class. */
	protected final Type to;
	
	/**
	 * Constructs the test.
	 *
	 * @param __e The owning engine.
	 * @param __file The JSON file to read.
	 * @param __to The to class.
	 * @since 2018/08/20
	 */
	__DoGenericObjectTranslate__(Engine __e, String __file, Type __to)
	{
		super(__e, "objtrans-" + __file + "-" + __to.toString());
		
		this.file = __file;
		this.to = __to;
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2018/08/20
	 */
	@Override
	public void end()
	{
		super.assertTrue(this.remoterecvokay);
		super.assertTrue(this.noerror);
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2018/08/20
	 */
	@Override
	public void remoteRequest(WrappedRequest __r)
	{
		StandardPushEvent event = (StandardPushEvent)__r.event;
		
		// It is invalid if there is an error
		if (!event.hasError())
			this.noerror.set(true);
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2018/08/20
	 */
	@Override
	public void remoteResult(WrappedResult __r)
	{
		if (__Utils__.isResultOkay(__r.result))
			this.remoterecvokay.set(true);
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2018/08/20
	 */
	@Override
	public void run(IOpipeExecution __e)
		throws Throwable
	{
		// Get basic object translation (may go to hashmap or similar)
		Object basic = __DoEventInfoPlugin__.<Object>__convert(Object.class,
			this.file);
		
		// Translate it
		Object target = ObjectTranslator.translator(basic.getClass(),
			this.to).translate(basic);
		
		// Debug log
		Logger.debug("Object translated {} {}/[Wanted: {}] | `{}` -> `{}`",
			basic.getClass(), target.getClass(), this.to, basic, target);
		__e.customMetric("source.class", basic.getClass().getName());
		__e.customMetric("source.value", basic.toString());
		__e.customMetric("target.class", target.getClass().getName());
		__e.customMetric("target.value", target.toString());
	}
}

