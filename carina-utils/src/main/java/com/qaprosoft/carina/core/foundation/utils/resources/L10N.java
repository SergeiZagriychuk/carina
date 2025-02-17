/*******************************************************************************
 * Copyright 2013-2020 QaProSoft (http://www.qaprosoft.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.qaprosoft.carina.core.foundation.utils.resources;

import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qaprosoft.carina.core.foundation.commons.SpecialKeywords;
import com.qaprosoft.carina.core.foundation.utils.Configuration;
import com.qaprosoft.carina.core.foundation.utils.Configuration.Parameter;

/*
 * http://maven.apache.org/surefire/maven-surefire-plugin/examples/class-loading.html
 * Need to set useSystemClassLoader=false for maven surefire plugin to receive access to classloader L10N files on CI
 * <plugin>
 * <groupId>org.apache.maven.plugins</groupId>
 * <artifactId>maven-surefire-plugin</artifactId>
 * <version>3.0.0-M4</version>
 * <configuration>
 * <useSystemClassLoader>false</useSystemClassLoader>
 * </configuration>
 */

public class L10N {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static ArrayList<ResourceBundle> resBoundles = new ArrayList<ResourceBundle>();

    public static void init() {
        List<Locale> locales = LocaleReader.init(Configuration
                .get(Parameter.LOCALE));

        List<String> loadedResources = new ArrayList<String>();
        
        try {

            for (URL u : Resources.getResourceURLs(new ResourceURLFilter() {
                public @Override
                boolean accept(URL u) {
                    String s = u.getPath();
                    boolean contains = s.contains(SpecialKeywords.L10N);
                    if (contains) {
                        LOGGER.debug("L10N: file URL: " + u);
                    }
                    return contains;
                }
            })) {
                LOGGER.debug(String.format(
                        "Analyzing '%s' L10N resource for loading...", u));
                /*
                 * 2. Exclude localization resources like such L10N.messages_de, L10N.messages_ptBR etc...
                 * Note: we ignore valid resources if 3rd or 5th char from the end is "_". As designed :(
                 */
                String fileName = FilenameUtils.getBaseName(u.getPath());

                if (u.getPath().endsWith("L10N.class")
                        || u.getPath().endsWith("L10N$1.class")) {
                    // separate conditions to support core JUnit tests
                    continue;
                }

                if (fileName.lastIndexOf('_') == fileName.length() - 3
                        || fileName.lastIndexOf('_') == fileName.length() - 5) {
                    LOGGER.debug(String
                            .format("'%s' resource IGNORED as it looks like localized resource!",
                                    fileName));
                    continue;
                }
                /*
                 * convert "file: <REPO>\target\classes\L10N\messages.properties" to "L10N.messages"
                 */
                String filePath = FilenameUtils.getPath(u.getPath());
                int index = filePath.indexOf(SpecialKeywords.L10N);

                if (index == -1) {
                    LOGGER.warn("Unable to find L10N pattern for " + u.getPath() + " resource!");
                    continue;
                }

                String resource = filePath.substring(
                        filePath.indexOf(SpecialKeywords.L10N))
                        .replaceAll("/", ".")
                        + fileName;

                if (!loadedResources.contains(resource)) {
                    loadedResources.add(resource);
                    try {
                        LOGGER.debug(String.format("Adding '%s' resource...",
                                resource));
                        for (Locale locale : locales) {
                            resBoundles.add(ResourceBundle.getBundle(resource, locale));
                        }
                        LOGGER.debug(String
                                .format("Resource '%s' added.", resource));
                    } catch (MissingResourceException e) {
                        LOGGER.debug(e.getMessage(), e);
                    }
                } else {
                    LOGGER.debug(String
                            .format("Requested resource '%s' is already loaded into the ResourceBundle!",
                                    resource));
                }
            }
            LOGGER.debug("init: L10N bundle size: " + resBoundles.size());
        } catch (IllegalArgumentException e) {
            LOGGER.debug("L10N folder with resources is missing!");
        }
    }

    /**
     * get Default Locale
     * 
     * @return Locale
     */
    public static Locale getDefaultLocale() {
        List<Locale> locales = LocaleReader.init(Configuration
                .get(Parameter.LOCALE));

        if (locales.size() == 0) {
            throw new RuntimeException("Undefined default locale specified! Review 'locale' setting in _config.properties.");
        }

        return locales.get(0);
    }

    /**
     * getText by key for default locale.
     * 
     * @param key
     *            - String
     * 
     * @return String
     */
    public static String getText(String key) {
        return getText(key, getDefaultLocale());
    }

    /**
     * getText for specified locale and key.
     * 
     * @param key
     *            - String
     * @param locale
     *            - Locale
     * @return String
     */
    public static String getText(String key, Locale locale) {
//        LOGGER.debug("getText: L10N bundle size: " + resBoundles.size());
        Iterator<ResourceBundle> iter = resBoundles.iterator();
        while (iter.hasNext()) {
            ResourceBundle bundle = iter.next();
            try {
                String value = bundle.getString(key);
                if (bundle.getLocale().toString().equals(locale.toString())) {
                    return value;
                }
            } catch (MissingResourceException e) {
                // do nothing
            }
        }
        return key;
    }

    /*
     * This method helps when translating strings that have single quote or other special characters that get omitted.
     */
    public static String formatString(String resource, String... parameters) {
        for (int i = 0; i < parameters.length; i++) {
            resource = resource.replace("{" + i + "}", parameters[i]);
            LOGGER.debug("Localized string value is: " + resource);
        }
        return resource;
    }

    /*
     * Make sure you remove the single quotes around %s in xpath as string
     * returned will either have it added for you or single quote won't be
     * added as concat() doesn't need them.
     */
    public static String generateConcatForXPath(String xpathString) {
        String returnString = "";
        String searchString = xpathString;
        char[] quoteChars = new char[] { '\'', '"' };

        int quotePos = StringUtils.indexOfAny(searchString, quoteChars);
        if (quotePos == -1) {
            returnString = "'" + searchString + "'";
        } else {
            returnString = "concat(";
            LOGGER.debug("Current concatenation: " + returnString);
            while (quotePos != -1) {
                String subString = searchString.substring(0, quotePos);
                returnString += "'" + subString + "', ";
                LOGGER.debug("Current concatenation: " + returnString);
                if (searchString.substring(quotePos, quotePos + 1).equals("'")) {
                    returnString += "\"'\", ";
                    LOGGER.debug("Current concatenation: " + returnString);
                } else {
                    returnString += "'\"', ";
                    LOGGER.debug("Current concatenation: " + returnString);
                }
                searchString = searchString.substring(quotePos + 1,
                        searchString.length());
                quotePos = StringUtils.indexOfAny(searchString, quoteChars);
            }
            returnString += "'" + searchString + "')";
            LOGGER.debug("Concatenation result: " + returnString);
        }
        return returnString;
    }

}
