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
package com.qaprosoft.carina.core.resources;

import com.qaprosoft.carina.core.foundation.commons.SpecialKeywords;
import com.qaprosoft.carina.core.foundation.report.ReportContext;
import com.qaprosoft.carina.core.foundation.utils.Configuration;
import com.qaprosoft.carina.core.foundation.utils.Configuration.Parameter;
import com.qaprosoft.carina.core.foundation.utils.R;
import com.qaprosoft.carina.core.foundation.utils.resources.LocaleReader;
import com.qaprosoft.carina.core.foundation.utils.resources.ResourceURLFilter;
import com.qaprosoft.carina.core.foundation.utils.resources.Resources;
import com.qaprosoft.carina.core.foundation.webdriver.decorator.ExtendedWebElement;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.*;

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

public class L10Nnew {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static ArrayList<ResourceBundle> resBoundles = new ArrayList<ResourceBundle>();

    public static Locale actualLocale;

    public static String assertErrorMsg = "";

    public static LinkedList<String> newLocList = new LinkedList<String>();

    public static Properties prop = new Properties();

    public static String propFileName = "";

    private static String encoding = "ISO-8859-1";

    protected static final int BASIC_WAIT_SHORT_TIMEOUT = 5;

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

    //Parser part
    /**
     * get Actual Locale
     *
     * @return Locale
     */
    public static Locale getActualLocale() {
        return actualLocale;
    }

    /**
     * get AssertErrorMsg
     *
     * @return String
     */
    public static String getAssertErrorMsg() {
        return assertErrorMsg;
    }

//    /**
//     * should we add New Localization - true or false
//     *
//     * @return boolean
//     */
//    public static boolean getNewLocalization() {
//        return newLocalization;
//    }

    /**
     * set Actual Locale
     *
     * @param countryCode String
     */
    public static void setActualLocale(String countryCode) {
        List<Locale> locales = LocaleReader.init(Configuration.get(Parameter.LOCALE));
        Locale locale = locales.get(0);
        try {
            String[] localeSetttings = countryCode.split("_");
            String lang, country = "";
            lang = localeSetttings[0];
            if (localeSetttings.length > 1) {
                country = localeSetttings[1];
            }
            locale = new Locale(lang, country);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        setActualLocale(locale);
    }

    /**
     * set Actual Locale
     *
     * @param locale - Locale
     */
    public static void setActualLocale(Locale locale) {
        LOGGER.info("Set actual Locale to " + locale);
        actualLocale = locale;

        propFileName = getPropertyFileName(actualLocale.toString());
        LOGGER.info("propFileName:=" + propFileName);

        boolean exists = new File(propFileName).exists();
        if (exists) {
            try {
                FileInputStream in = new FileInputStream(propFileName);
                prop.load(in);
                in.close();
            } catch (Exception e) {
                LOGGER.debug(e.getMessage(), e);
            }
        }
    }



//    /**
//     * check should we add New Localization or not
//     *
//     * @return boolean
//     */
//    private static boolean getAddNewLocalization() {
//        boolean ret = false;
//        if (!newLocalization) {
//            try {
//                String add_new = Configuration.get(Parameter.ADD_NEW_LOCALIZATION);
//                if (add_new.toLowerCase().contains("true")) {
//                    LOGGER.info("New localization will be added.");
//                    newLocalization = true;
//                    return true;
//                }
//            } catch (Exception e) {
//                LOGGER.debug(e.getMessage(), e);
//            }
//        } else {
//            ret = true;
//        }
//
//        return ret;
//    }

    /**
     * ge tProperty FileName
     *
     * @param localName - String
     * @return String with path + PropertyFileName
     */
    private static String getPropertyFileName(String localName) {
        String ret;
        String add_new_loc_path = "null";
        String add_new_loc_name = "null";
        try {
            add_new_loc_path = Configuration.get(Parameter.ADD_NEW_LOCALIZATION_PATH);
            add_new_loc_name = Configuration.get(Parameter.ADD_NEW_LOCALIZATION_PROPERTY_NAME);
        } catch (Exception e) {
            LOGGER.debug("Using default parameters because of error: " + e);
        }
        if (add_new_loc_path.toLowerCase().contains("null")
                || add_new_loc_path.toLowerCase().contains("{must_override}") || add_new_loc_path.isEmpty()) {
            add_new_loc_path = ReportContext.getArtifactsFolder().getAbsolutePath();
        }

        if (add_new_loc_name.toLowerCase().contains("null")
                || add_new_loc_name.toLowerCase().contains("{must_override}") || add_new_loc_name.isEmpty()) {
            add_new_loc_name = "new_localization_";
        }

        ret = add_new_loc_path + "/" + add_new_loc_name + localName + ".properties";

        return ret;
    }

    /**
     * check Localization Text. Will work ONLY if locKey is equal to element
     * Name and element is Public
     *
     * @param elem ExtendedWebElement
     * @return boolean
     */
    public static boolean checkLocalizationText(ExtendedWebElement elem) {
        return checkLocalizationText(elem, true, BASIC_WAIT_SHORT_TIMEOUT , false);
    }

    /**
     * check Localization Text. Will work ONLY if locKey is equal to element
     * Name and element is Public
     *
     * @param elem                      ExtendedWebElement
     * @param skipMissed                - boolean - if true - will ignore missed elements.
     * @param timeout                   - timeout for element presence waiting.
     * @param skipPunctuationAndNumbers - if true - there will be no numbers and tricky punctuation in l10n values
     * @return boolean
     */
    public static boolean checkLocalizationText(ExtendedWebElement elem, boolean skipMissed, int timeout, boolean skipPunctuationAndNumbers) {
        if (elem.isElementPresent(timeout)) {
            String elemText = elem.getText();
            String locKey = elem.getName();
            return checkLocalizationText(elemText, locKey, skipPunctuationAndNumbers);
        } else {
            LOGGER.info("Expected element not present. Please check: " + elem);
            if (skipMissed) {
                LOGGER.info("Skip missed element: " + elem);
                return true;
            }
        }
        return false;
    }

    private static boolean checkLocalizationText(String expectedText, String locKey, boolean skipPunctuationAndNumbers) {
        String l10n_default = L10Nnew.getText(locKey, actualLocale);
        boolean ret;

        if (skipPunctuationAndNumbers) {
            ret = removeNumbersAndPunctuation(expectedText).toLowerCase().contains(removeNumbersAndPunctuation(l10n_default).toLowerCase());
        } else {
            ret = expectedText.contains(l10n_default);
        }

        if (!ret) {
            LOGGER.error(
                    "Actual text should be localized and be equal to: '" + l10n_default + "'. But currently it is '" + expectedText + "'.");
            assertErrorMsg = "Expected: '" + l10n_default + "', length=" + l10n_default.length() + ". Actually: '" + expectedText + "', length="
                    + expectedText.length() + ".";

            if (skipPunctuationAndNumbers) {
                expectedText = removeNumbersAndPunctuation(expectedText);
            }
            String newItem = locKey + "=" + expectedText;
            LOGGER.info("Making new localization string: " + newItem);
            newLocList.add(newItem);
            prop.setProperty(locKey, expectedText);
            return false;
        } else {
            LOGGER.debug("Found localization text '" + expectedText + "' in ISO-8859-1 encoding : " + l10n_default);
            return true;
        }
    }

    /**
     * removeNumbersAndPunctuation from L10n string
     *
     * @param str String
     * @return String
     */
    private static String removeNumbersAndPunctuation(String str) {
        try {
            str = str.replaceAll("[0-9]", "");
            str = str.replace("!", "").replace("\u0085", "").replace("…", "");
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        return str;
    }

    /**
     * check MultipleLocalization
     *
     * @param localizationCheckList - ExtendedWebElement[] should be set on required page with all
     *                              needed public elements
     * @return boolean
     */
    public static boolean checkMultipleLocalization(ExtendedWebElement[] localizationCheckList) {
        return checkMultipleLocalization(localizationCheckList, BASIC_WAIT_SHORT_TIMEOUT, false);
    }

    /**
     * check MultipleLocalization
     *
     * @param localizationCheckList     - ExtendedWebElement[] should be set on required page with all
     *                                  needed public elements
     * @param timeout                   - timeout for element presence waiting.
     * @param skipPunctuationAndNumbers - if true - there will be no numbers and tricky punctuation in l10n values
     * @return boolean
     */
    public static boolean checkMultipleLocalization(ExtendedWebElement[] localizationCheckList, int timeout, boolean skipPunctuationAndNumbers) {
        boolean ret = true;
        String returnAssertErrorMsg = "";
        assertErrorMsg = "";
        for (ExtendedWebElement elem : localizationCheckList) {
            if (!checkLocalizationText(elem, true, timeout, skipPunctuationAndNumbers)) {
                ret = false;
                returnAssertErrorMsg = returnAssertErrorMsg + " \n" + assertErrorMsg;
            }
        }
        assertErrorMsg = returnAssertErrorMsg;
        return ret;
    }

    /**
     * Save Localization to property file
     */
    public static void saveLocalization() {
        try {
            if (prop.size() == 0) {
                LOGGER.info("There are no new localization properties.");
                return;
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        LOGGER.info("New localization for '" + actualLocale + "'");
        LOGGER.info(newLocList.toString());
        LOGGER.info("Properties: " + prop.toString());
        newLocList.clear();
        try {
            if (propFileName.isEmpty()) {
                propFileName = getPropertyFileName(actualLocale.toString());
                LOGGER.info("propFileName:=" + propFileName);
            }

            String encoding = getLocalizationSaveEncoding();
            if (encoding.contains("UTF")) {
                prop.store(new OutputStreamWriter(
                        new FileOutputStream(propFileName), "UTF-8"), null);
            } else {
                OutputStream output = new FileOutputStream(propFileName);
                prop.store(output, null);
                output.close();
            }

        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        prop.clear();
    }

    /**
     * get Localization Save Encoding
     *
     * @return String
     */
    private static String getLocalizationSaveEncoding() {
        try {
            encoding = Configuration.get(Parameter.ADD_NEW_LOCALIZATION_ENCODING);
        } catch (Exception e) {
            LOGGER.error("There is no localization encoding parameter in config property.");
        }
        LOGGER.info("Will use encoding: " + encoding);
        return encoding.toUpperCase();
    }

}
