/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.jersey.server.internal.inject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.WeakHashMap;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Providers;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;

import org.glassfish.jersey.server.internal.LocalizationMessages;
import org.glassfish.jersey.spi.StringValueReader;
import org.glassfish.jersey.spi.StringValueReaderProvider;
import org.glassfish.jersey.internal.ExtractorException;
import org.glassfish.jersey.internal.ProcessingException;

import org.glassfish.hk2.Factory;

import org.jvnet.hk2.annotations.Inject;

import org.xml.sax.InputSource;

/**
 * String reader provider producing {@link StringValueReader string readers} that
 * support conversion of a string value into a JAXB instance.
 *
 * @author Paul Sandoz
 * @author Marek Potociar (marek.potociar at oracle.com)
 */
// TODO fix the implementation and complete javadoc.
public class JaxbStringReaderProvider {

    private static final Map<Class, JAXBContext> jaxbContexts = new WeakHashMap<Class, JAXBContext>();
    private final ContextResolver<JAXBContext> context;
    private final ContextResolver<Unmarshaller> unmarshaller;

    /**
     * TODO javadoc.
     *
     * @param ps
     */
    public JaxbStringReaderProvider(Providers ps) {
        this.context = ps.getContextResolver(JAXBContext.class, null);
        this.unmarshaller = ps.getContextResolver(Unmarshaller.class, null);
    }

    /**
     * TODO javadoc.
     *
     * @param type
     * @return
     * @throws JAXBException
     */
    protected final Unmarshaller getUnmarshaller(Class type) throws JAXBException {
        if (unmarshaller != null) {
            Unmarshaller u = unmarshaller.getContext(type);
            if (u != null) {
                return u;
            }
        }

        return getJAXBContext(type).createUnmarshaller();
    }

    private JAXBContext getJAXBContext(Class type) throws JAXBException {
        if (context != null) {
            JAXBContext c = context.getContext(type);
            if (c != null) {
                return c;
            }
        }

        return getStoredJAXBContext(type);
    }

    /**
     * TODO javadoc.
     *
     * @param type
     * @return
     * @throws JAXBException
     */
    protected JAXBContext getStoredJAXBContext(Class type) throws JAXBException {
        synchronized (jaxbContexts) {
            JAXBContext c = jaxbContexts.get(type);
            if (c == null) {
                c = JAXBContext.newInstance(type);
                jaxbContexts.put(type, c);
            }
            return c;
        }
    }

    /**
     * TODO javadoc.
     */
    public static class RootElementProvider extends JaxbStringReaderProvider implements StringValueReaderProvider {

        // Delay construction of factory
        private final Factory<SAXParserFactory> spfProvider;

        /**
         * TODO javadoc.
         *
         * @param spfProvider
         * @param ps
         */
        public RootElementProvider(@Inject Factory<SAXParserFactory> spfProvider, @Inject Providers ps) {
            super(ps);
            this.spfProvider = spfProvider;
        }

        @Override
        public <T> StringValueReader<T> getStringReader(final Class<T> type, Type genericType, Annotation[] annotations) {
            final boolean supported = (type.getAnnotation(XmlRootElement.class) != null
                    || type.getAnnotation(XmlType.class) != null);
            if (!supported) {
                return null;
            }

            return new StringValueReader<T>() {

                @Override
                public T fromString(String value) {
                    try {
                        final SAXSource source = new SAXSource(
                                spfProvider.get().newSAXParser().getXMLReader(),
                                new InputSource(new java.io.StringReader(value)));

                        final Unmarshaller u = getUnmarshaller(type);
                        if (type.isAnnotationPresent(XmlRootElement.class)) {
                            return type.cast(u.unmarshal(source));
                        } else {
                            return u.unmarshal(source, type).getValue();
                        }
                    } catch (UnmarshalException ex) {
                        throw new ExtractorException(LocalizationMessages.ERROR_UNMARSHALLING_JAXB(type), ex);
                    } catch (JAXBException ex) {
                        throw new ProcessingException(LocalizationMessages.ERROR_UNMARSHALLING_JAXB(type), ex);
                    } catch (Exception ex) {
                        throw new ProcessingException(LocalizationMessages.ERROR_UNMARSHALLING_JAXB(type), ex);
                    }
                }
            };
        }
    }
}