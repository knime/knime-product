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
 *   20 Jun 2019 (albrecht): created
 */
package org.knime.product.rcp.intro;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.p2.operations.RepositoryTracker;
import org.eclipse.equinox.p2.operations.UpdateOperation;
import org.eclipse.equinox.p2.ui.ProvisioningUI;
import org.knime.core.eclipseUtil.UpdateChecker;
import org.knime.core.eclipseUtil.UpdateChecker.UpdateInfo;

/**
 *
 * @author Christian Albrecht, KNIME GmbH, Konstanz, Germany
 */
public final class UpdateDetector {

    /**
     * Check for new releases
     *
     * @return List of update infos on new releases
     * @throws IOException
     * @throws URISyntaxException
     */
    public static final List<UpdateInfo> checkForNewRelease() throws IOException, URISyntaxException {
        final ProvisioningUI provUI = ProvisioningUI.getDefaultUI();
        RepositoryTracker tracker = provUI.getRepositoryTracker();
        if (tracker == null) {
            // if run from the IDE there will be no tracker
            return Collections.emptyList();
        }

        List<UpdateInfo> updateList = new ArrayList<>();
        for (URI uri : tracker.getKnownRepositories(provUI.getSession())) {
            if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                && (uri.getHost().endsWith(".knime.org") || uri.getHost().endsWith(".knime.com"))) {
                UpdateInfo newRelease = UpdateChecker.checkForNewRelease(uri);
                if (newRelease != null) {
                    updateList.add(newRelease);
                }
            }
        }
        return updateList;
    }

    /**
     * Check for new bugfix updates
     *
     * @return List of bugfix updates
     * @throws IOException
     * @throws URISyntaxException
     */
    public static List<String> checkForBugfixes() throws IOException, URISyntaxException {
        final ProvisioningUI provUI = ProvisioningUI.getDefaultUI();
        RepositoryTracker tracker = provUI.getRepositoryTracker();
        if (tracker == null) {
            // if run from the IDE there will be no tracker
            return Collections.emptyList();
        }

        UpdateOperation op = new UpdateOperation(provUI.getSession());
        op.resolveModal(new NullProgressMonitor());
        return Stream.of(op.getPossibleUpdates()).map(u -> u.toUpdate.getProperty("org.eclipse.equinox.p2.name"))
                .sorted().distinct().collect(Collectors.toList());
    }

}
