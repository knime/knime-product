/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Nov 4, 2024 (lw): created
 */
package org.knime.product.rcp.shutdown;

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.junit.jupiter.api.Test;

import junit.framework.AssertionFailedError;

/**
 * Tests the {@link PreShutdown} API for correct dispatching of abort/continue signals.
 */
class PreShutdownTest {

    @Test
    void testSuccessfulHook() {
        final var run = new AtomicBoolean();
        final var successfulHook = new PreShutdown() {

            @Override
            public boolean onPreShutdown() {
                return true;
            }

            @Override
            public void onShutdownAborted() {
                throw new AssertionFailedError("Should not have received the abort signal in successful hook");
            }

            @Override
            public void onShutdownContinued() {
                run.set(true);
            }
        };
        final var configElement = new TestingConfigurationElement() {

            @Override
            public Object createExecutableExtension(final String propertyName) throws CoreException {
                return successfulHook;
            }
        };

        final var shutdown = PreShutdown.preShutdown(Map.of(configElement, successfulHook));
        assertThat("Hook should have been executed by PreShutdown API", shutdown && run.get());
    }

    @Test
    void testVetoingHook() {
        final var run = new AtomicBoolean();
        final var vetoingHook = new PreShutdown() {

            @Override
            public boolean onPreShutdown() {
                return false;
            }

            @Override
            public void onShutdownAborted() {
                run.set(true);
            }

            @Override
            public void onShutdownContinued() {
                throw new AssertionFailedError("Should not have received the continue signal in vetoing hook");
            }
        };
        final var configElement = new TestingConfigurationElement() {

            @Override
            public Object createExecutableExtension(final String propertyName) throws CoreException {
                return vetoingHook;
            }
        };

        final var shutdown = PreShutdown.preShutdown(Map.of(configElement, vetoingHook));
        assertThat("Hook should have been executed by PreShutdown API", !shutdown && run.get());
    }

    /**
     * Dummy class for an essentially empty {@link IConfigurationElement} except for the possibility to
     * implement {@link #createExecutableExtension(String)} to create arbitrary extensions.
     */
    abstract static class TestingConfigurationElement implements IConfigurationElement {

        @Override
        public String getAttribute(final String name) throws InvalidRegistryObjectException {
            return "";
        }

        @Override
        public String getAttribute(final String attrName, final String locale) throws InvalidRegistryObjectException {
            return "";
        }

        @Override
        public String getAttributeAsIs(final String name) throws InvalidRegistryObjectException {
            return "";
        }

        @Override
        public String[] getAttributeNames() throws InvalidRegistryObjectException {
            return new String[0];
        }

        @Override
        public IConfigurationElement[] getChildren() throws InvalidRegistryObjectException {
            return new IConfigurationElement[0];
        }

        @Override
        public IConfigurationElement[] getChildren(final String name) throws InvalidRegistryObjectException {
            return new IConfigurationElement[0];
        }

        @Override
        public IExtension getDeclaringExtension() throws InvalidRegistryObjectException {
            return null;
        }

        @Override
        public String getName() throws InvalidRegistryObjectException {
            return getClass().getName();
        }

        @Override
        public Object getParent() throws InvalidRegistryObjectException {
            return null;
        }

        @Override
        public String getValue() throws InvalidRegistryObjectException {
            return "";
        }

        @Override
        public String getValue(final String locale) throws InvalidRegistryObjectException {
            return "";
        }

        @Override
        public String getValueAsIs() throws InvalidRegistryObjectException {
            return "";
        }

        @Override
        public String getNamespace() throws InvalidRegistryObjectException {
            return "";
        }

        @Override
        public String getNamespaceIdentifier() throws InvalidRegistryObjectException {
            return "";
        }

        @Override
        public IContributor getContributor() throws InvalidRegistryObjectException {
            return new IContributor() {

                @Override
                public String getName() {
                    return "";
                }
            };
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public int getHandleId() {
            return 0;
        }
    }
}
