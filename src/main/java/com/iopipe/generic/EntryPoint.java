package com.iopipe.generic;

import com.amazonaws.services.lambda.runtime.Context;
import com.iopipe.IOpipeExecution;
import com.iopipe.IOpipeFatalError;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * This represents an entry point to another method.
 *
 * @since 2018/08/13
 */
public final class EntryPoint
{
	/** The class the entry point is in. */
	protected final Class<?> inclass;
	
	/** The base method handle for execution. */
	protected final MethodHandle handle;
	
	/** Is this a static method? */
	protected final boolean isstatic;
	
	/** Parameters to the method. */
	private final Type[] _parameters;
	
	/**
	 * Initializes the entry point.
	 *
	 * @param __cl The used class.
	 * @param __h The handle to execute.
	 * @param __static Is this static?
	 * @param __parmeters Parameters to the entry point.
	 * @throws NullPointerException On null arguments.
	 * @since 2018/08/13
	 */
	private EntryPoint(Class<?> __cl, MethodHandle __h, boolean __static,
		Type[] __parameters)
		throws InvalidEntryPointException, NullPointerException
	{
		if (__cl == null || __h == null || __parameters == null)
			throw new NullPointerException();
		
		this.inclass = __cl;
		this.handle = __h;
		this.isstatic = __static;
		this._parameters = __parameters.clone();
	}
	
	/**
	 * Returns the method handle for the invocation.
	 *
	 * @param __instance The instance to call on, for the first argument. If
	 * the entry point is static then this is ignored.
	 * @return The method handle for the invocation.
	 * @since 2018/08/13
	 */
	public final MethodHandle handle(Object __instance)
	{
		MethodHandle rv = this.handle;
		
		// Bind to the instance if this is not static so that calling the
		// handle only involves the method arguments and does not require a
		// the code using the handle to check if it is static
		if (!this.isstatic)
			return rv.bindTo(__instance);
		
		return rv;
	}
	
	/**
	 * Returns a method handle with a new instance of the current entry point
	 * if it is non-static.
	 *
	 * @return The method handle with the new instance.
	 * @since 2018/08/16
	 */
	public final MethodHandle handleWithNewInstance()
	{
		if (this.isstatic)
			return this.handle(null);
		
		return this.handle(this.newInstance());
	}
	
	/**
	 * Is this method handle static?
	 *
	 * @return If this method handle is static.
	 * @since 2018/08/13
	 */
	public final boolean isStatic()
	{
		return this.isstatic;
	}
	
	/**
	 * Create a new instance of the object for entry.
	 *
	 * @return The new object.
	 * @throws InvalidEntryPointException If the entry point is not valid.
	 * @throws IllegalStateException If the entry point is static, which means
	 * that no instance is initialized.
	 * @since 2018/08/14
	 */
	public final Object newInstance()
		throws InvalidEntryPointException, IllegalStateException
	{
		if (this.isstatic)
			throw new IllegalStateException("Entry point is static, an " +
				"instance cannot be created.");
		
		// Get the default constructor
		Constructor used;
		try
		{
			used = this.inclass.getConstructor();
		}
		catch (NoSuchMethodException e)
		{
			// Failed to get that constructor so try to get one that was
			// declared
			try
			{
				used = this.inclass.getDeclaredConstructor();
			}
			catch (NoSuchMethodException f)
			{
				throw new InvalidEntryPointException("Could not obtain the " +
					"constructor for the entry point.", f);
			}
		}
		
		// Need to make this constructor visible
		boolean access = used.isAccessible();
		if (!access)
			try
			{
				used.setAccessible(true);
			}
			catch (SecurityException e)
			{
			}
		
		// Creating an instance may fail
		try
		{
			return used.newInstance();
		}
		
		// Constructor threw exception, so unwrap it
		catch (InvocationTargetException e)
		{
			Throwable c = e.getCause();
			if (c instanceof RuntimeException)
				throw (RuntimeException)c;
			else if (c instanceof Error)
				throw (Error)c;
			else
				throw new RuntimeException("Constructor threw checked " +
					"exception.", c);
		}
		
		// Failed to initialize it somehow
		catch (IllegalAccessException|IllegalArgumentException|
			InstantiationException e)
		{
			throw new InvalidEntryPointException("Could not construct an " +
				"instance of the entry point class.", e);
		}
		
		// Always try to revert the accessible state of the constructor so
		// it does not remain accessible if it was not
		finally
		{
			if (!access)
				try
				{
					used.setAccessible(false);
				}
				catch (SecurityException e)
				{
				}
		}
	}
	
	/**
	 * Returns the parameters for the entry type.
	 *
	 * @return The parameters for the method call.
	 * @since 2018/08/21
	 */
	public final Type[] parameters()
	{
		return this._parameters.clone();
	}
	
	/**
	 * Returns the entry that would be used for AWS services.
	 *
	 * @return The entry point for AWS method.
	 * @since 2018/08/14
	 */
	public static final EntryPoint defaultAWSEntryPoint()
	{
		// This variable is very important
		String pair = System.getenv("IOPIPE_GENERIC_HANDLER");
		if (pair == null)
			throw new InvalidEntryPointException("The environment variable " +
				"IOPIPE_GENERIC_HANDLER has not been set, execution cannot " +
				"continue.");
		
		try
		{
			// Only a class is specified
			int dx = pair.indexOf("::");
			if (dx < 0)
				return EntryPoint.newAWSEntryPoint(Class.forName(pair),
					"handleRequest");
			
			// Class and method
			else
				return EntryPoint.newAWSEntryPoint(
					Class.forName(pair.substring(0, dx)),
					pair.substring(dx + 2));
		}
		catch (ClassNotFoundException e)
		{
			throw new InvalidEntryPointException("The environment variable " +
				"IOPIPE_GENERIC_HANDLER is set to a class which does not " +
				"exist. (" + pair + ")", e);
		}
	}
	
	/**
	 * Returns the default entry point.
	 *
	 * @return The default entry point.
	 * @since 2018/08/13
	 */
	public static final EntryPoint defaultEntryPoint()
	{
		// For now since only AWS is supported detect the entry point for AWS
		return EntryPoint.defaultAWSEntryPoint();
	}
	
	/**
	 * Initializes an entry point which is valid for Amazon AWS.
	 *
	 * @param __cl The class to call into.
	 * @param __m The method to be executed.
	 * @throws InvalidEntryPointException If the entry point is not valid.
	 * @throws NullPointerException On null arguments.
	 * @since 2018/08/03
	 */
	public static final EntryPoint newAWSEntryPoint(Class<?> __cl, String __m)
		throws InvalidEntryPointException, NullPointerException
	{
		if (__cl == null || __m == null)
			throw new NullPointerException();
		
		// There may be multiple type of entry points that are available
		// 0: ()
		// 1: (A)
		// 2: (A, Context)
		// 3: (I, O)
		// 4: (I, O, Context)
		// 5: (IOpipeExecution, A)
		// 6: (IOpipeExecution, I, O)
		Method[] entries = new Method[7];
		
		// Go through mappings and look for specific method types
		int scancount = 0;
__outer:
		for (Class<?> look = __cl; look != null; look = look.getSuperclass())
		{
			// A previous run through the loop found a match, so do not try
			// to use any upper class methods
			if (scancount > 0)
				break;
			
			// Scan for methods
			for (Method m : look.getDeclaredMethods())
			{
				// This is not what our method is called
				if (!__m.equals(m.getName()))
					continue;
				
				// Ignore abstract methods, they cannot be called
				if ((m.getModifiers() & Modifier.ABSTRACT) != 0)
					continue;
				
				int pn = m.getParameterCount();
				Class<?>[] parms = m.getParameterTypes();
				
				Class<?> pa = (pn > 0 ? parms[0] : null),
					pb = (pn > 1 ? parms[1] : null),
					pc = (pn > 2 ? parms[2] : null);
				
				// Determine which kind of entry point type this is
				int ehdx;
				if (IOpipeExecution.class.equals(pa))
					if (pn == 3 &&
						InputStream.class.isAssignableFrom(pb) &&
						OutputStream.class.isAssignableFrom(pc))
						ehdx = 6;
					else if (pn == 2)
						ehdx = 5;
					else
						ehdx = -1;
				else if (InputStream.class.isAssignableFrom(pa) &&
					OutputStream.class.isAssignableFrom(pb))
					if (Context.class.isAssignableFrom(pc))
						ehdx = 4;
					else
						ehdx = 3;
				else if (pn == 2 && Context.class.isAssignableFrom(pb))
					ehdx = 2;
				else if (pn == 1)
					ehdx = 1;
				else if (pn == 0)
					ehdx = 0;
				else
					ehdx = -1;
				
				// Not a valid handle
				if (ehdx < 0)
					continue;
				
				// Record the method if nothing is already there
				if (entries[ehdx] == null)
				{
					entries[ehdx] = m;
					
					// Found all method types, so stop
					if (++scancount == 4)
						break __outer;
				}
			}
		}
		
		// No methods were found
		if (scancount == 0)
			throw new InvalidEntryPointException("The entry point " + __m +
				" in class " + __cl + " is not valid, no method was found.");
		
		// Prefer ones with higher priority first
		int discovered;
		Method used = null;
		for (discovered = 6; discovered >= 0; discovered--)
		{
			used = entries[discovered];
			if (used != null)
				break;
		}
		
		// Allow us to call this method without performing any access checks
		boolean access = used.isAccessible();
		if (!access)
			try
			{
				used.setAccessible(true);
			}
			catch (SecurityException e)
			{
			}
		
		// Get method handle from it, assuming the previous call worked
		MethodHandle basehandle;
		try
		{
			basehandle = MethodHandles.lookup().unreflect(used);
		}
		catch (IllegalAccessException e)
		{
			throw new InvalidEntryPointException("Could not access the " +
				"generic entry point method.", e);
		}
		
		// If this was not accessible, then we would have tried to make it so
		// so just revert access to it
		if (!access)
			try
			{
				used.setAccessible(false);
			}
			catch (SecurityException e)
			{
			}
		
		
		// Extract all the details in the method to rebuild it
		int pn = used.getParameterCount();
		Type[] parms = used.getGenericParameterTypes();
		boolean isstatic = ((used.getModifiers() & Modifier.STATIC) != 0);
		Type pa = (pn > 0 ? parms[0] : null),
			pb = (pn > 1 ? parms[1] : null),
			pc = (pn > 2 ? parms[2] : null);
		
		// Always normalize parameters to either be a stream type or non-stream
		// type, with a context
		Type[] passparameters;
		switch (discovered)
		{
				// Parameter and context
			case 0:
			case 1:
			case 2:
			case 5:
				passparameters = new Type[]
					{
						(pa != null ? pa : Object.class),
						Context.class,
					};
				break;
			
				// Input and output streams
			case 3:
			case 4:
			case 6:
				passparameters = new Type[]
					{
						(pa != null ? pa : InputStream.class),
						(pb != null ? pb : OutputStream.class),
						Context.class,
					};
				break;
			
				// This indicates the code is wrong
			default:
				throw new Error("If this has happened then something is " +
					"very wrong.");
		}
		
		// 0: ()
		// 1: (A)
		// 2: (A, Context)
		// 3: (I, O)
		// 4: (I, O, Context)
		// 5: (IOpipeExecution, A)
		// 6: (IOpipeExecution, I, O)
		
		// Build a compatible method handle and parameter set
		MethodHandle usedhandle;
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		try
		{
			switch (discovered)
			{
					// 0: ()
				case 0:
					if (isstatic)
						usedhandle = lookup.findStatic(__AWSAdapters__.class,
							"__type0Static", MethodType.methodType(
								Object.class, MethodHandle.class, Object.class,
								Context.class)).bindTo(basehandle);
					else
						usedhandle = lookup.findStatic(__AWSAdapters__.class,
							"__type0Instance", MethodType.methodType(
								Object.class, MethodHandle.class, Object.class,
								Object.class, Context.class)).bindTo(basehandle);
					break;
				
					// 1: (A)
				case 1:
					if (isstatic)
						usedhandle = lookup.findStatic(__AWSAdapters__.class,
							"__type1Static", MethodType.methodType(
								Object.class, MethodHandle.class, Object.class,
								Context.class)).bindTo(basehandle);
					else
						usedhandle = lookup.findStatic(__AWSAdapters__.class,
							"__type1Instance", MethodType.methodType(
								Object.class, MethodHandle.class, Object.class,
								Object.class, Context.class)).bindTo(basehandle);
					break;
				
					// 2: (A, Context), identity handler
				case 2:
					usedhandle = basehandle;
					break;
					
					// 3: (I, O)
				case 3:
					throw new Error("TODO");
					
					// 4: (I, O, Context), identity handler
				case 4:
					usedhandle = basehandle;
					break;
					
					// 5: (IOpipeExecution, A)
				case 5:
					throw new Error("TODO");
					
					// 6: (IOpipeExecution, I, O)
				case 6:
					throw new Error("TODO");
				
					// This indicates that the code is wrong
				default:
					throw new Error("If this has happened then something is " +
						"very wrong.");
			}
		}
		
		// If this happens this is fatal and the code is wrong
		catch (IllegalAccessException|NoSuchMethodException e)
		{
			throw new Error("Could not locate the AWS handle wrappers.", e);
		}
		
		return new EntryPoint(__cl, usedhandle, isstatic, passparameters);
	}
}

