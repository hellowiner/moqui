/*
 * This Work is in the public domain and is provided on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE,
 * NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * You are solely responsible for determining the appropriateness of using
 * this Work and assume any risks associated with your use of this Work.
 *
 * This Work includes contributions authored by David E. Jones, not as a
 * "work for hire", who hereby disclaims any copyright to the same.
 */
package org.moqui.impl.service.runner

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.InvocationTargetException

import org.moqui.context.Cache
import org.moqui.context.ExecutionContext
import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.service.ServiceException
import org.moqui.impl.service.ServiceRunner

public class JavaServiceRunner implements ServiceRunner {
    protected ServiceFacadeImpl sfi
    protected Cache classCache

    JavaServiceRunner() {}

    public ServiceRunner init(ServiceFacadeImpl sfi) {
        this.sfi = sfi
        classCache = sfi.ecfi.getCacheFacade().getCache("service.java.class")
        return this
    }

    public Map<String, Object> runService(ServiceDefinition sd, Map<String, Object> context) {
        if (!sd.serviceNode."@location" || !sd.serviceNode."@method") {
            throw new ServiceException("Service [" + sd.serviceName + "] is missing location and/or method attributes and they are required for running a java service.")
        }

        Map<String, Object> result

        try {
            Class c = (Class) classCache.get(sd.location)
            if (!c) {
                c = this.getClass().getClassLoader().loadClass(sd.location)
                classCache.put(sd.location, c)
            }
            Method m = c.getMethod(sd.serviceNode."@method", ExecutionContext.class, Map.class)
            if (Modifier.isStatic(m.getModifiers())) {
                result = (Map<String, Object>) m.invoke(null, sfi.ecfi.getExecutionContext(), context)
            } else {
                result = (Map<String, Object>) m.invoke(c.newInstance(), sfi.ecfi.getExecutionContext(), context)
            }
        } catch (ClassNotFoundException e) {
            throw new ServiceException("Could not find class for java service [${sd.serviceName}]", e)
        } catch (NoSuchMethodException e) {
            throw new ServiceException("Java Service [${sd.serviceName}] specified method [${sd.serviceNode."@method"}] that does not exist in class [${sd.location}]", e)
        } catch (SecurityException e) {
            throw new ServiceException("Access denied in service [${sd.serviceName}]", e)
        } catch (IllegalAccessException e) {
            throw new ServiceException("Method not accessible in service [${sd.serviceName}]", e)
        } catch (IllegalArgumentException e) {
            throw new ServiceException("Invalid parameter match in service [${sd.serviceName}]", e)
        } catch (NullPointerException e) {
            throw new ServiceException("Null pointer in service [${sd.serviceName}]", e)
        } catch (ExceptionInInitializerError e) {
            throw new ServiceException("Initialization failed for service [${sd.serviceName}]", e)
        } catch (InvocationTargetException e) {
            throw new ServiceException("Java method for service [${sd.serviceName}] threw an exception", e.getTargetException())
        } catch (Throwable t) {
            throw new ServiceException("Error or unknown exception in service [${sd.serviceName}]", t)
        }

        return result
    }

    public void destroy() { }
}