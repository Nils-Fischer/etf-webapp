/**
 * Copyright 2010-2022 interactive instruments GmbH
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
 */
package de.interactive_instruments.etf.webapp.helpers;

import java.util.Collection;

import de.interactive_instruments.SUtils;
import de.interactive_instruments.etf.dal.dto.test.ExecutableTestSuiteDto;
import de.interactive_instruments.etf.webapp.controller.EtfConfig;

/**
 * Helper functions for views
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class View {

    private View() {

    }

    public static Collection<String[]> getTestRunParams(final ExecutableTestSuiteDto ets) {
        return ets.getParameters().asNameDefaultValuePairs();
    }

    public static boolean hasTestRunParams(final ExecutableTestSuiteDto ets) {
        return !ets.getParameters().isEmpty();
    }

    public static String getWorkflowType() {
        return EtfConfig.getInstance().getProperty(EtfConfig.ETF_WORKFLOWS);
    }

    public static String getBrandingText() {
        return EtfConfig.getInstance().getProperty(EtfConfig.ETF_BRANDING_TEXT);
    }

    public static String getContactText() {
        final String disclaimer = EtfConfig.getInstance().getProperty(EtfConfig.ETF_META_CONTACT_TEXT);
        return SUtils.isNullOrEmpty(disclaimer) ? null : disclaimer;
    }

    public static String getDisclaimerText() {
        final String legalNotice = EtfConfig.getInstance().getProperty(EtfConfig.ETF_META_DISCLAIMER_TEXT);
        return SUtils.isNullOrEmpty(legalNotice) ? null : legalNotice;
    }

    public static String getCopyrightText() {
        final String disclaimer = EtfConfig.getInstance().getProperty(EtfConfig.ETF_META_COPYRIGHT_TEXT);
        return SUtils.isNullOrEmpty(disclaimer) ? null : disclaimer;
    }

    public static String getPrivacyStatementText() {
        final String legalNotice = EtfConfig.getInstance()
                .getProperty(EtfConfig.ETF_META_PRIVACYSTATEMENT_TEXT);
        return SUtils.isNullOrEmpty(legalNotice) ? null : legalNotice;
    }

    public static String getBaseUrl() {
        return EtfConfig.getInstance()
                .getProperty(EtfConfig.ETF_WEBAPP_BASE_URL);
    }

    public static String getCssUrl() {
        return EtfConfig.getInstance()
                .getProperty(EtfConfig.ETF_CSS_URL);
    }

    public static String getHelpPageURL() {
        return EtfConfig.getInstance().getProperty(EtfConfig.ETF_HELP_PAGE_URL);
    }

    public static String getTconfPageURL() {
        return EtfConfig.getInstance().getProperty(EtfConfig.ETF_TCONF_PAGE_URL);
    }

    public static String getVersion() {
        return EtfConfig.getInstance().getVersion();
    }

    public static String getReportComparison() {
        return EtfConfig.getInstance().getProperty(EtfConfig.ETF_REPORT_COMPARISON);
    }

    public static String getSubmitAnalysisData() {
        return EtfConfig.getInstance().getProperty(EtfConfig.ETF_SUBMIT_ERRORS);
    }

    public static String getBranding() {
        String branding = EtfConfig.getInstance().getProperty(EtfConfig.ETF_BRANDING);
        String brandingDefault = EtfConfig.getInstance().getPropertyDefault(EtfConfig.ETF_BRANDING);
        if (branding == brandingDefault) {
            return branding + " " + getVersion() + "-xyz";
        }
        else {
            String brandingVersion = EtfConfig.getInstance().getProperty(EtfConfig.ETF_BRANDING_VERSION);
            String versionDefault = EtfConfig.getInstance().getPropertyDefault(EtfConfig.ETF_BRANDING_VERSION);
            return branding + " " + (brandingVersion == versionDefault ? getVersion() : brandingVersion)
                + " (based on ETF)";
        }
    }
}
