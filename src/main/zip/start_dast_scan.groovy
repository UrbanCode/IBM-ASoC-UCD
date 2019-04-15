/**
 * (c) Copyright IBM Corporation 2015.
 * (c) Copyright HCL Technologies Ltd. 2018. All Rights Reserved.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

import com.urbancode.air.AirPluginTool
import com.urbancode.air.plugin.AppScanSaaS.SCXRestClient
import com.urbancode.air.plugin.AppScanSaaS.ScanType

final def airHelper = new AirPluginTool(args[0], args[1])
final Properties props = airHelper.getStepProperties()

String appId = props["applicationId"]
String startingUrl = props["startingUrl"]
String issueCountString = props['reportIssueCountValidation'] // Fail count threshold
String scanUser = props["scanUser"]
String scanPassword = props["scanPassword"]
String thirdCredential = props['thirdCredential']
String parentjobid = props["parentScanId"]
String scanType = props["scanType"]
String scanFilePath = props["scanFile"]
String presenceId = props["presenceId"]
String testPolicy = props["testPolicy"]
boolean outputIssues = Boolean.parseBoolean(props['outputIssues'])
long scanTimeout = props["scanTimeout"] ? Long.parseLong(props["scanTimeout"]) : -1
boolean mailNotification = props['mailNotification']
boolean failOnPause = Boolean.parseBoolean(props['failOnPause'])
boolean validateReport = !issueCountString.isEmpty() || outputIssues
int exitCode = 0

SCXRestClient restClient = new SCXRestClient(props)

if (startingUrl == null || startingUrl.isEmpty()){
    println "[Error] Missing starting url."
    System.exit(1)
}

String scanId = restClient.startDastScan(
    startingUrl,
    scanUser,
    scanPassword,
    thirdCredential,
    parentjobid,
    presenceId,
    testPolicy,
    appId,
    scanType,
    scanFilePath,
    mailNotification)

airHelper.setOutputProperty("ScanId", scanId)
airHelper.storeOutputProperties()

if (validateReport) {
    long startTime = System.currentTimeMillis()
    def scan = restClient.waitForScan(scanId, ScanType.DAST, startTime, scanTimeout, failOnPause)
    def issuesJson = scan.LastSuccessfulExecution

    /* Fail if issue count exceeds the set threshhold */
    if (!issueCountString.isEmpty()) {
        exitCode = restClient.validateScanIssues(issuesJson, scan.Name, scanId, issueCountString)

    }
    if (outputIssues) {
        String highIssueCount = issuesJson.NHighIssues
        String medIssueCount = issuesJson.NMediumIssues
        String lowIssueCount = issuesJson.NLowIssues
        String infoIssueCount = issuesJson.NInfoIssues

        try {
            println("Setting the following output properties on the step: highIssueCount, " +
                "medIssueCount, lowIssueCount, infoIssueCount.")
            airHelper.setOutputProperty("highIssueCount", highIssueCount)
            airHelper.setOutputProperty("medIssueCount", medIssueCount)
            airHelper.setOutputProperty("lowIssueCount", lowIssueCount)
            airHelper.setOutputProperty("infoIssueCount", infoIssueCount)
        }
        finally {
            airHelper.storeOutputProperties()
        }
    }
}

if (exitCode) {
    println("[Error] Scan has failed validation.")
}
else {
    println("[OK] Scan has completed successfully.")
}

System.exit(exitCode)