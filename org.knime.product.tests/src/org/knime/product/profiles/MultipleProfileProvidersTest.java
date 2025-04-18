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
 *   Mar 20, 2025 (lw): created
 */
package org.knime.product.profiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.knime.product.profiles.TestPreferencesContext.getDefaultPreferences;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.apache.commons.lang3.function.FailableRunnable;
import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.knime.product.ProductPlugin;
import org.knime.product.profiles.ProfileManager.Profile;

/**
 * Advanced tests for the ProfileManager covering multiple providers, overwrites, filtering of
 * {@link Profile#stream(IProfileProvider, java.nio.file.Path)} and other edge cases.
 *
 * @author Leon Wenzler, KNIME GmbH, Konstanz, Germany
 */
final class MultipleProfileProvidersTest {

    private static TestProfileServer server;

    /**
     * Sets up the test environment.
     */
    @BeforeAll
    static void applyProfiles() {
        ProfileManager.getInstance().applyProfiles(true);
    }

    @BeforeAll
    static void startServer() {
        // Start a server for downloading remote profiles.
        server = new TestProfileServer("profiles.zip", IProfileProvider.SERVER_PROFILE_PATH);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    private static void withTestProfileProviders(final BiConsumer<TestProfileProvider, TestProfileProvider> configurer,
        final FailableRunnable<Exception> test) throws Exception {
        final var lowPriority = TestProfileProvider.LowPriority.getInstance();
        final var highPriority = TestProfileProvider.HighPriority.getInstance();

        // Backup the current contents of both providers.
        final var lowPriorityProfiles = lowPriority.getRequestedProfiles();
        final var lowPriorityLocation = lowPriority.getProfilesLocation();
        final var highPriorityProfiles = highPriority.getRequestedProfiles();
        final var highPriorityLocation = highPriority.getProfilesLocation();

        try {
            // Accept test-specific provider configuration before applying profiles.
            configurer.accept(lowPriority, highPriority);
            ProfileManager.getInstance().applyProfiles(true);
            test.run();
        } finally {
            // Restore the contents of both, low-priority and high-priority, providers.
            lowPriority.setRequestedProfiles(lowPriorityProfiles);
            lowPriority.setProfilesLocation(lowPriorityLocation);
            highPriority.setRequestedProfiles(highPriorityProfiles);
            highPriority.setProfilesLocation(highPriorityLocation);
        }
    }

    private static Stream<Arguments> locationCombinations1() {
        return Stream.of( //
            Arguments.of(new Object[]{null}), //
            Arguments.of(server.getProfilesLocation()) //
        );
    }

    private static Stream<Arguments> locationCombinations2() {
        return locationCombinations1() //
            .flatMap(a -> locationCombinations1().map(b -> Arguments.of(a.get()[0], b.get()[0])));
    }

    // -- TESTS FOR COMBINING PREFERENCES --

    /**
     * Test that multiple providers’ profiles are combined and that when the same key is defined,
     * the high-priority provider’s value overwrites the low-priority one.
     */
    @ParameterizedTest
    @MethodSource("locationCombinations2")
    void testInterProviderProfilesCombination(final URI lowLocation, final URI highLocation) throws Exception {
        withTestProfileProviders((low, high) -> {
            // Low-prio provider containing unique "low-key", high-prio provider containing "high-key",
            // and both containing the overlapping key "duplicate-key".
            low.setRequestedProfiles(Arrays.asList("lowprio"));
            low.setProfilesLocation(lowLocation);
            high.setRequestedProfiles(Arrays.asList("highprio"));
            high.setProfilesLocation(highLocation);
        }, () -> {
            // Verify via the public Eclipse preferences.
            getDefaultPreferences(ProductPlugin.PLUGIN_ID, prefs -> {
                assertEquals("low-value", prefs.get("low-key", "XXX"), //
                    "Value should be loaded from low-priority provider");
                assertEquals("high-value", prefs.get("high-key", "XXX"), //
                    "Value should be loaded from high-priority provider");
                assertEquals("high-value", prefs.get("duplicate-key", "XXX"), //
                    "Value from low-priority provider should be overwriten by high-priority provider");
            });
        });
    }

    /**
     * Test that within one provider (here low priority) multiple profiles can overwrite each other. For example,
     * profile "base" is overwritten by profile "custom" (following order in profiles list).
     */
    @ParameterizedTest
    @MethodSource("locationCombinations2")
    void testIntraProviderProfilesCombination(final URI lowLocation, final URI highLocation) throws Exception {
        withTestProfileProviders((low, high) -> {
            // Use low for both profiles, "lowprio" and "highprio".
            low.setRequestedProfiles(Arrays.asList("lowprio", "highprio"));
            low.setProfilesLocation(lowLocation);
            // High provider will not contribute.
            high.setRequestedProfiles(Collections.emptyList());
            high.setProfilesLocation(highLocation);
        }, () -> {
            getDefaultPreferences(ProductPlugin.PLUGIN_ID, prefs -> {
                assertEquals("low-value", prefs.get("low-key", "XXX"), //
                    "Value should be loaded from \"lowprio\" profile");
                assertEquals("high-value", prefs.get("high-key", "XXX"), //
                    "Value should be loaded from \"lowprio\" profile");
                assertEquals("high-value", prefs.get("duplicate-key", "XXX"), //
                    "Value from \"lowprio\" profile should be overwriten by \"highprio\" profile");
            });
        });
    }

    // -- TESTS FOR ERRANEOUS CONFIGURATIONS --

    /**
     * Test fuzzy inputs: what if a provider returns an empty list and a null profile location.
     */
    @Test
    void testEmptyProfilesLocation() throws Exception {
        withTestProfileProviders((low, high) -> {
            // Set both providers to return empty profiles.
            low.setRequestedProfiles(Collections.emptyList());
            high.setRequestedProfiles(Collections.emptyList());
        }, () -> {
            // Expect that no profiles have been applied.
            assertTrue(ProfileManager.getInstance().getRequestProfiles().isEmpty(), //
                "Providers with no profiles should result in none being applied");
        });
    }

    /**
     * Test that if a provider’s profile location URI is invalid (unsupported scheme) an exception is thrown.
     */
    @ParameterizedTest
    @MethodSource("locationCombinations1")
    void testInvalidProfilesLocation(final URI highLocation) throws Exception {
        final var lowLocation = URI.create("ftp://localhost/foo/bar/baz");

        // Test that `ProfileManager#getLocalProfilesLocation` throws.
        withTestProfileProviders((low, high) -> {
            // For low priority, set an unsupported scheme.
            low.setRequestedProfiles(Collections.emptyList());
            low.setProfilesLocation(lowLocation);
            high.setRequestedProfiles(Collections.emptyList());
            high.setProfilesLocation(highLocation);
        }, () -> {
            final var provider = TestProfileProvider.LowPriority.getInstance();
            assertThrows(IllegalArgumentException.class, //
                () -> ProfileManager.getLocalProfilesLocation(provider), //
                "Profile location should be marked as invalid, per exception");
        });

        // Test that `ProfilesManager#applyProfiles` throws.
        assertThrows(IllegalArgumentException.class, //
            () -> withTestProfileProviders((low, high) -> low.setProfilesLocation(lowLocation), () -> {
            }), "Profile location should be marked as invalid, per exception");
    }

    /**
     * Test that if a provider’s URI points to a valid (syntactically correct) but non-downloadable location, then
     * downloadWasSuccessful() becomes false while valid local profiles (from the other provider) are loaded.
     */
    @ParameterizedTest
    @MethodSource("locationCombinations1")
    void testNonExistingProfileLocation(final URI highLocation) throws Exception {
        withTestProfileProviders((low, high) -> {
            // Low priority provider containing unique "low-key", high priority provider containing "high-key",
            // and both containing the overlapping "duplicate-key".
            low.setRequestedProfiles(Arrays.asList("lowprio"));
            high.setRequestedProfiles(Arrays.asList("highprio"));

            // Simulate a low-priority, remote provider with a non-downloadable URI.
            low.setProfilesLocation(URI.create("http://localhost/foo/bar/baz"));
            high.setProfilesLocation(highLocation);
        }, () -> {
            // Expect that the remote download failed.
            Optional<Boolean> downloadSuccess = ProfileManager.getInstance().downloadWasSuccessful();
            assertFalse(downloadSuccess.orElse(true), "Download should have failed for invalid URI");

            // But the high priority local profile should have been applied.
            getDefaultPreferences(ProductPlugin.PLUGIN_ID, prefs -> {
                assertNull(prefs.get("low-key", null), //
                    "Preferences from the profile \"lowprio\" should not be loaded");
                assertEquals("high-value", prefs.get("high-key", "XXX"), //
                    "Preferences from the profile \"highprio\" should be loaded");
            });
        });
    }

    /**
     * Test what happens if a provider’s URI points to a remote location, where the server
     * returns an error status code (e.g. 404 not found).
     */
    @ParameterizedTest
    @MethodSource("locationCombinations1")
    void testHttpErrorProfileLocation(final URI highLocation) throws Exception {
        // Create a URI returning 404 (which is the default for WireMock when no stub is used).
        final var notFoundLocation = new URIBuilder(server.getProfilesLocation()) //
            .setPath("") //
            .build();

        withTestProfileProviders((low, high) -> {
            // Basically the same setup as `#testNonExistingProfileLocation`.
            low.setRequestedProfiles(Arrays.asList("lowprio"));
            high.setRequestedProfiles(Arrays.asList("highprio"));
            low.setProfilesLocation(notFoundLocation);
            high.setProfilesLocation(highLocation);
        }, () -> {
            // Expect that the remote download failed.
            Optional<Boolean> downloadSuccess = ProfileManager.getInstance().downloadWasSuccessful();
            assertFalse(downloadSuccess.orElse(true), "Download should have failed for invalid URI");

            // But the high priority local profile should have been applied.
            getDefaultPreferences(ProductPlugin.PLUGIN_ID, prefs -> {
                assertNull(prefs.get("low-key", null), //
                    "Preferences from the profile \"lowprio\" should not be loaded");
                assertEquals("high-value", prefs.get("high-key", "XXX"), //
                    "Preferences from the profile \"highprio\" should be loaded");
            });
        });
    }

    // -- TESTS FOR PROFILE APPLICATION --

    /**
     * That that `overwrite=false` does not apply the following changes. The test is
     * re-implementing {@link #withTestProfileProviders(BiConsumer, FailableRunnable)}.
     */
    @Test
    void testNoOverwriteAppliedProfiles() {
        final var lowPriority = TestProfileProvider.LowPriority.getInstance();
        final var highPriority = TestProfileProvider.HighPriority.getInstance();

        // Backup the current contents of both providers.
        final var lowPriorityProfiles = lowPriority.getRequestedProfiles();
        final var highPriorityProfiles = highPriority.getRequestedProfiles();

        try {
            // Force overwriting with non-empty profile lists.
            ProfileManager.getInstance().applyProfiles(true);
            lowPriority.setRequestedProfiles(Collections.emptyList());
            highPriority.setRequestedProfiles(Collections.emptyList());

            // Use the default (`overwrite=false`) here.
            ProfileManager.getInstance().applyProfiles();
            final var expected = new LinkedList<>(lowPriorityProfiles);
            expected.addAll(highPriorityProfiles);
            final var actual = ProfileManager.getInstance().getAllAppliedProfiles().stream() //
                .map(Profile::name) //
                .toList();
            assertEquals(expected, actual, "Different to expected profiles were applied");
        } finally {
            lowPriority.setRequestedProfiles(lowPriorityProfiles);
            highPriority.setRequestedProfiles(highPriorityProfiles);
        }
    }

    /**
     * Test that Profile.stream correctly filters out profiles that do not exist
     * or that are outside the given base path.
     */
    @Test
    void testProfileStreamFiltersCorrectly() throws Exception {
        withTestProfileProviders((low, high) -> {
            // For low priority, use these profiles which could escape the profiles location.
            // - the existing "base" profile.
            // - the non-existing "gone" profile.
            // - the non-existing and path-relative "../evil" profile.
            // - the existing and path-relative "../src" profile.
            low.setRequestedProfiles(Arrays.asList("base", "gone", "../evil", "../src"));
            // High provider will not contribute.
            high.setRequestedProfiles(Collections.emptyList());
        }, () -> {
            // List valid profiles, should only be "base".
            final var baseUri = TestProfileProvider.LowPriority.getInstance().getProfilesLocation();
            final var basePath = Path.of(baseUri);
            final var profiles = Profile.stream(TestProfileProvider.LowPriority.getInstance(), basePath) //
                .toList();
            assertEquals(1, profiles.size(), "Only one profile should be loaded");
            assertEquals("base", profiles.get(0).name(), "Only the profile \"base\" should be loaded");
        });
    }
}
