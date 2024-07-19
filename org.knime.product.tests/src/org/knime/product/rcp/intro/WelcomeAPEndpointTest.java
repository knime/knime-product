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
 *   2 Jul 2024 (carlwitt): created
 */
package org.knime.product.rcp.intro;

import static org.junit.Assert.assertEquals;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.knime.core.util.HubStatistics;
import org.knime.product.rcp.intro.WelcomeAPEndpoint.HubUsage;

/**
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public class WelcomeAPEndpointTest {

    /** reset the hub statistics */
    @BeforeEach
    public void setUp() {
        // reset community hub statistics
        HubStatistics.storeKnimeHubStat(HubStatistics.LAST_KNIME_HUB_LOGIN, "");
        HubStatistics.storeKnimeHubStat(HubStatistics.LAST_KNIME_HUB_UPLOAD, "");
        HubStatistics.storeKnimeHubStat(HubStatistics.LAST_SENT_KNIME_HUB_LOGIN, "");
        HubStatistics.storeKnimeHubStat(HubStatistics.LAST_SENT_KNIME_HUB_UPLOAD, "");

        // reset non-community hub statistics
        HubStatistics.storeKnimeHubStat(HubStatistics.LAST_KNIME_NON_COMMUNITY_HUB_LOGIN, "");
        HubStatistics.storeKnimeHubStat(HubStatistics.LAST_KNIME_NON_COMMUNITY_HUB_UPLOAD, "");
        HubStatistics.storeKnimeHubStat(HubStatistics.LAST_SENT_KNIME_NON_COMMUNITY_HUB_LOGIN, "");
        HubStatistics.storeKnimeHubStat(HubStatistics.LAST_SENT_KNIME_NON_COMMUNITY_HUB_UPLOAD, "");
    }

    /**
     * Test method for hub usage reporting to instrumentation based on {@link HubStatistics}.
     * @param scope of the hub usage (community or non-community)
     */
    @ParameterizedTest
    @EnumSource(HubUsage.Scope.class)
    public void testHubUsageLogin(final HubUsage.Scope scope) {
        final var loginField = scope == HubUsage.Scope.COMMUNITY ? HubStatistics.LAST_KNIME_HUB_LOGIN
            : HubStatistics.LAST_KNIME_NON_COMMUNITY_HUB_LOGIN;
        final var sentLoginField = scope == HubUsage.Scope.COMMUNITY ? HubStatistics.LAST_SENT_KNIME_HUB_LOGIN
            : HubStatistics.LAST_SENT_KNIME_NON_COMMUNITY_HUB_LOGIN;

        // no last date is set and fresh (temporary testing) workspace -> NEW
        assertEquals(HubUsage.NEW, HubUsage.current(scope));

        // last login is set but hasn't been sent -> USER
        HubStatistics.storeKnimeHubStat(loginField, ZonedDateTime.now().toString());
        assertEquals(HubUsage.USER, HubUsage.current(scope));

        // last login is set but was not in last session but longer ago (it has been reported before) -> NEW
        HubStatistics.storeKnimeHubStat(sentLoginField, scope.m_lastLogin.get().get().toString());
        assertEquals(HubUsage.NEW, HubUsage.current(scope));

        // nothing changed -> NEW
        assertEquals(HubUsage.NEW, HubUsage.current(scope));

        // newer login date -> USER
        HubStatistics.storeKnimeHubStat(loginField, ZonedDateTime.now().toString());
        assertEquals(HubUsage.USER, HubUsage.current(scope));
    }

    /**
     * Test method for hub usage reporting to instrumentation based on {@link HubStatistics}.
     * @param scope of the hub usage (community or non-community)
     */
    @ParameterizedTest
    @EnumSource(HubUsage.Scope.class)
    public void testHubUsageUpload(final HubUsage.Scope scope) {
        final var uploadField = scope == HubUsage.Scope.COMMUNITY ? HubStatistics.LAST_KNIME_HUB_UPLOAD
            : HubStatistics.LAST_KNIME_NON_COMMUNITY_HUB_UPLOAD;
        final var sentUploadField = scope == HubUsage.Scope.COMMUNITY ? HubStatistics.LAST_SENT_KNIME_HUB_UPLOAD
            : HubStatistics.LAST_SENT_KNIME_NON_COMMUNITY_HUB_UPLOAD;

        // no last date is set and fresh (temporary testing) workspace -> NEW
        assertEquals(HubUsage.NEW, HubUsage.current(scope));

        // last upload is set but hasn't been sent -> CONTRIBUTOR
        HubStatistics.storeKnimeHubStat(uploadField, ZonedDateTime.now().toString());
        assertEquals(HubUsage.CONTRIBUTOR, HubUsage.current(scope));

        // last upload is set but was not in last session but longer ago (it has been reported before) -> NEW
        HubStatistics.storeKnimeHubStat(sentUploadField, scope.m_lastUpload.get().get().toString());
        assertEquals(HubUsage.NEW, HubUsage.current(scope));

        // nothing changed -> NEW
        assertEquals(HubUsage.NEW, HubUsage.current(scope));

        // newer upload date -> CONTRIBUTOR
        HubStatistics.storeKnimeHubStat(uploadField, ZonedDateTime.now().toString());
        assertEquals(HubUsage.CONTRIBUTOR, HubUsage.current(scope));
    }

}
