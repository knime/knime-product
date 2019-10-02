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
 *   19 Jun 2019 (albrecht): created
 */
package org.knime.product.rcp.intro;

import java.io.File;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * @author Christian Albrecht, KNIME GmbH, Konstanz, Germany
 * @since 4.0
 */
class TileInjector extends AbstractInjector {

    private static final String EXAMPLE_WORKFLOW_URI =
            "knime://LOCAL/Example%20Workflows/Basic%20Examples/Visual%20Analysis%20of%20Sales%20Data/workflow.knime";

    protected TileInjector(final File templateFile, final ReentrantLock introFileLock,
        final IEclipsePreferences preferences, final boolean isFreshWorkspace,
        final DocumentBuilderFactory parserFactory, final XPathFactory xpathFactory,
        final TransformerFactory transformerFactory) {
        super(templateFile, introFileLock, preferences, isFreshWorkspace, parserFactory, xpathFactory,
            transformerFactory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void prepareData() throws Exception {
        if (IntroPage.MOCK_INTRO_PAGE) {
            Thread.sleep(5000);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void injectData(final Document doc, final XPath xpath) throws Exception {
        Element tileContainer =
            (Element)xpath.evaluate("//div[@id='carousel-content']", doc.getDocumentElement(), XPathConstants.NODE);
        while (tileContainer.hasChildNodes()) {
            tileContainer.removeChild(tileContainer.getFirstChild());
        }

        if (m_isFreshWorkspace) {
            tileContainer.appendChild(createOpenExampleWorkflowTile(doc));
            tileContainer.appendChild(createHubTile(doc));
            tileContainer.appendChild(createIntroMailTile(doc, xpath));
            Element header = (Element)xpath.evaluate("//div[@id='welcome-page-header']", doc.getDocumentElement(),
                XPathConstants.NODE);
            Element hubSearch = (Element)xpath.evaluate("//form[@id='hub-search-bar']", doc.getDocumentElement(),
                XPathConstants.NODE);
            header.removeChild(hubSearch);
        } else {
            tileContainer.appendChild(createRegisterHubTile(doc));
            tileContainer.appendChild(createCoursesTile(doc));
            tileContainer.appendChild(createForumTile(doc));
        }

    }

    private static Element createOpenExampleWorkflowTile(final Document doc) {
        return createTile(doc, "img/workflow.svg", "Example", "Get started with this example", "",
            "intro://openworkflow/" + EXAMPLE_WORKFLOW_URI, "Open workflow");
    }

    private static Element createHubTile(final Document doc) {
        return createTile(doc, "img/screen-perspective-hub.jpg", "Hub", "Looking for more examples? Visit the KNIME Hub", "",
            "https://hub.knime.com?src=knimeapp", "KNIME Hub");
    }

    private static Element createIntroMailTile(final Document doc, final XPath xpath) throws XPathExpressionException {
        return createTitleTile(doc, xpath, "img/mail.svg", "Getting started", "Sign up for introductory emails", "",
            "These messages will get you up and running as quickly as possible.",
            "https://www.knime.com/form/help-getting-started?src=knimeapp", "Sign up");
    }

    private static Element createRegisterHubTile(final Document doc) {
        return createTile(doc, "img/hub-connect.png", "Hub", "Share your workflows and components on KNIME Hub", "You are now able to share your workflow on our newly integrated hub platform",
            "https://hub.knime.com/site/about?src=knimeapp", "Learn more");
    }

    private static Element createCoursesTile(final Document doc) {
        return createTile(doc, "img/courses.svg", "Courses","KNIME Courses: learn all about Big Data, Text Mining and more", "",
            "https://www.knime.com/courses?src=knimeapp", "Explore KNIME Courses");
    }

    private static Element createForumTile(final Document doc) {
        return createTile(doc, "img/pic-community.jpg","Community" ,"Questions? Ask the community", "",
            "https://forum.knime.com?src=knimeapp", "Visit Forum");
    }

    static Element createTitleTile(final Document doc, final XPath xpath, final String imageLocation, final String tileText, final String tileSubText,
        final String titleText, final String contentText, final String buttonAction, final String buttonText)
        throws XPathExpressionException {

        Element tile = TileInjector.createTile(doc, imageLocation, tileText, titleText, tileSubText, buttonAction, buttonText);
        tile.setAttribute("class", tile.getAttribute("class") + " title-tile");

        // move icon into title tag
        Element image = (Element)xpath.evaluate("//img", tile, XPathConstants.NODE);
        image.getParentNode().removeChild(image);
        Element titleTag = (Element)xpath.evaluate("//p[@class='tile-title']", tile, XPathConstants.NODE);
        titleTag.insertBefore(image, titleTag.getFirstChild());

        Element button = (Element)xpath.evaluate("//a[@class='button-primary']", tile, XPathConstants.NODE);
        Element contentDiv = (Element)button.getParentNode();
        Element light = (Element)xpath.evaluate("//div[@class='light']", tile, XPathConstants.NODE);
        contentDiv.removeChild(titleTag);
        light.insertBefore(titleTag, contentDiv);

        // add tile text
        Element text = doc.createElement("p");
        text.setAttribute("class", "tile-text");
        text.setTextContent(contentText);
        light.insertBefore(text, contentDiv);
        return tile;
    }

    static Element createUpdateBanner(final Document doc, final String imageLocation,
        final String titleText, final String buttonAction, final String buttonText)
        throws XPathExpressionException {

        Element updateContainer = doc.createElement("div");
        updateContainer.setAttribute("id", "update-container");

        Element updateContent = doc.createElement("div");
        updateContent.setAttribute("id", "update-content");
        updateContent.setAttribute("class", "inner-content");
        updateContainer.appendChild(updateContent);

        Element carouselLength = doc.createElement("div");
        carouselLength.setAttribute("id", "carousel-length");
        carouselLength.setAttribute("class", "carousel-length");
        updateContent.appendChild(carouselLength);

        Element updateBannerDiv = doc.createElement("div");
        updateBannerDiv.setAttribute("id", "updateBanner");
        updateBannerDiv.setAttribute("class", "update-tile");
        carouselLength.appendChild(updateBannerDiv);

        Element tileImage = doc.createElement("img");
        tileImage.setAttribute("src", imageLocation);
        updateBannerDiv.appendChild(tileImage);

        // add tile text
        Element text = doc.createElement("p");
        text.setAttribute("class", "tile-text");
        text.setTextContent(titleText);
        updateBannerDiv.appendChild(text);

        Element buttonDiv = doc.createElement("div");
        buttonDiv.setAttribute("class", "button-div");
        updateBannerDiv.appendChild(buttonDiv);

        Element tileButton = doc.createElement("a");
        tileButton.setAttribute("class", "button-primary");
        tileButton.setAttribute("href", buttonAction);
        tileButton.setTextContent(buttonText);
        buttonDiv.appendChild(tileButton);

        return updateContainer;
    }

    static Element createTile(final Document doc, final String imageLocation, final String tagText, final String titleText, final String subTitleText,
        final String buttonAction, final String buttonText) {
        Element tile = doc.createElement("div");
        tile.setAttribute("class", "carousel-tile");
        Element article = doc.createElement("article");
        article.setAttribute("class", "aspect4by3");
        tile.appendChild(article);
        Element lightDiv = doc.createElement("div");
        lightDiv.setAttribute("class", "light");
        article.appendChild(lightDiv);

        Element tileImage = doc.createElement("img");
        tileImage.setAttribute("src", imageLocation);
        lightDiv.appendChild(tileImage);
        Element contentDiv = doc.createElement("div");
        contentDiv.setAttribute("class", "content-div");
        lightDiv.appendChild(contentDiv);

        Element tileTagContainer = doc.createElement("div");
        tileTagContainer.setAttribute("class", "tile-tag-container");
        contentDiv.appendChild(tileTagContainer);

        Element tileTag = doc.createElement("p");
        tileTag.setAttribute("class", "tile-tag");
        tileTag.setTextContent(tagText);
        tileTagContainer.appendChild(tileTag);

        Element tileTitle = doc.createElement("p");
        tileTitle.setAttribute("class", "tile-title");
        tileTitle.setTextContent(titleText);
        contentDiv.appendChild(tileTitle);

        Element tileSubTitle = doc.createElement("p");
        tileSubTitle.setAttribute("class", "tile-sub-title");
        tileSubTitle.setTextContent(subTitleText);
        contentDiv.appendChild(tileSubTitle);

        Element buttonDiv = doc.createElement("div");
        buttonDiv.setAttribute("class", "button-div");
        lightDiv.appendChild(buttonDiv);

        Element tileButton = doc.createElement("a");
        tileButton.setAttribute("class", "button-primary");
        tileButton.setAttribute("href", buttonAction);
        tileButton.setTextContent(buttonText);
        buttonDiv.appendChild(tileButton);

        return tile;
    }

}
