/*
 *******************************************************************************
 * Copyright (c) 2016-2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.eclipse.microprofile.rest.client.spi;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import javax.annotation.Priority;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

/**
 * This class is not intended to be used by end-users but for portable container
 * integration purpose only.
 * <p>
 * Resolver for a {@link RestClientBuilder} implementation. The implementation
 * registers itself via the {@link java.util.ServiceLoader} mechanism or via
 * {@link setInstance(RestClientBuilderResolver resolver)}.
 * <p>
 * This class provides a default implementation which uses the service loader
 * pattern to look for all implementations of <code>RestClientBuilder</code> and
 * creates a new builder with the highest priority specified by the
 * {@link Priority} annotation.
 * <p>
 * Implementations may override the {@link newBuilder()} method to create custom
 * <code>RestClientBuilder</code> implementations.
 *
 * @author Ondrej Mihalyi
 */
public class RestClientBuilderResolver {

    private static volatile RestClientBuilderResolver instance = null;

    protected RestClientBuilderResolver() {
    }

    /**
     * Creates a new RestClientBuilder instance.
     * <p>
     * Implementations are expected to override the {@link newBuilder()} method
     * to create custom RestClientBuilder implementations.
     * <p>
     * The default implementation uses the service loader pattern to look for
     * all implementations of RestClientBuilder and creates a new builder with
     * the highest priority specified with the {@link Priority} annotation. The
     * priority is 1 it the annotations isn't present.
     * <p>
     * The {@link ServiceLoader} will first search via the current Thread's
     * Context ClassLoader, then {@link RestClientBuilder}'s {@link ClassLoader}
     *
     * @return new RestClientBuilder instance
     */
    public RestClientBuilder newBuilder() {
        ServiceLoader<RestClientBuilder> loader = ServiceLoader.load(RestClientBuilder.class);
        List<RestClientBuilder> clientBuilders = new ArrayList<>();
        loader.forEach(clientBuilders::add);
        loader = ServiceLoader.load(RestClientBuilder.class, RestClientBuilder.class.getClassLoader());
        loader.forEach(clientBuilders::add);

        if (clientBuilders.isEmpty()) {
            throw new RuntimeException("No implementation of '" + RestClientBuilder.class.getSimpleName() + "' found");
        }
        clientBuilders.sort(Comparator.comparingInt(this::getBuilderPriority)
                .reversed());
        return clientBuilders.get(0);
    }

    /**
     * Computes priority for a builder.
     *
     * @param value builder instance which can be annotated with
     * {@link Priority}
     * @return the priority of the builder
     */
    protected int getBuilderPriority(RestClientBuilder value) {
        Priority priority = value.getClass().getAnnotation(Priority.class);
        if (priority == null) {
            return 1;
        }
        else {
            return priority.value();
        }
    }

    /**
     * Gets or creates a RestClientBuilderResolver instance. Only used
     * internally from within {@link RestClientBuilder}
     *
     * @return an instance of RestClientBuilderResolver
     */
    // method copied and adapted from ConfigProviderResolver in microprofile-config
    public static RestClientBuilderResolver instance() {
        if (instance == null) {
            synchronized (RestClientBuilderResolver.class) {
                if (instance != null) {
                    return instance;
                }

                ClassLoader cl = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                    @Override
                    public ClassLoader run() {
                        return Thread.currentThread().getContextClassLoader();
                    }
                });
                if (cl == null) {
                    cl = RestClientBuilderResolver.class.getClassLoader();
                }

                RestClientBuilderResolver newInstance = loadSpi(cl);

                if (newInstance == null) {
                    throw new IllegalStateException(
                            "No RestClientBuilderResolver implementation found!");
                }

                instance = newInstance;
            }
        }

        return instance;
    }

    // method copied and adapted from ConfigProviderResolver in microprofile-config
    private static RestClientBuilderResolver loadSpi(ClassLoader cl) {
        if (cl == null) {
            return null;
        }

        // start from the root CL and go back down to the TCCL
        RestClientBuilderResolver resolver = loadSpi(cl.getParent());

        if (resolver == null) {
            ServiceLoader<RestClientBuilderResolver> sl = ServiceLoader.load(
                    RestClientBuilderResolver.class, cl);
            for (RestClientBuilderResolver spi : sl) {
                if (resolver != null) {
                    throw new IllegalStateException(
                            "Multiple RestClientBuilderResolver implementations found: "
                            + spi.getClass().getName() + " and "
                            + resolver.getClass().getName());
                }
                else {
                    resolver = spi;
                }
            }
        }
        return resolver;
    }

    /**
     * Set the instance. It can be as an alternative to service loader pattern,
     * e.g. in OSGi environment
     *
     * @param resolver instance.
     */
    public static void setInstance(RestClientBuilderResolver resolver) {
        instance = resolver;
    }

}