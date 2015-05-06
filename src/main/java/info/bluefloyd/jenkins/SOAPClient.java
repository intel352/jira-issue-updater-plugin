package info.bluefloyd.jenkins;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.atlassian.jira.rpc.soap.client.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Simple client used to make calls to Jira via SOAP.
 * 
 * @author Laszlo Miklosik
 */
public class SOAPClient {

	private static final Log LOGGER = LogFactory.getLog(SOAPClient.class);
	private static final int MAX_NUMBER_OF_ISSUES_RETURNED = 10000;

	public SOAPSession connect(String jiraSoapWsUrl, String userName, String password) {
		SOAPSession soapSession = null;
		try {
			soapSession = new SOAPSession(new URL(jiraSoapWsUrl));
		} catch (MalformedURLException e1) {
			LOGGER.error("Invalid URL: " + jiraSoapWsUrl);
		}
		return authenticateSoapSession(soapSession, userName, password);
	}

	private SOAPSession authenticateSoapSession(SOAPSession soapSession, String userName, String password) {
		if (soapSession != null) {
			try {
				soapSession.connect(userName, password);
			} catch (RemoteAuthenticationException e) {
				LOGGER.error("Authentication to Jira failed: the Jira username and/or password is incorrect!", e);
				return null;
			} catch (RemoteException e) {
				LOGGER.error("Could not connect to Jira via SOAP.", e);
				return null;
			}
		}
		return soapSession;
	}

	public List<RemoteIssue> findIssuesByJQL(SOAPSession session, String jql) throws JqlException {
		LOGGER.info("Searching for issues by JQL query: " + jql);
		RemoteIssue[] issuesFromJQLSearch = null;
		String token = session.getAuthenticationToken();
		JiraSoapService jiraSoapService = session.getJiraSoapService();
		try {
			issuesFromJQLSearch = jiraSoapService.getIssuesFromJqlSearch(token, jql, MAX_NUMBER_OF_ISSUES_RETURNED + 1);
		} catch (com.atlassian.jira.rpc.soap.client.RemoteException e) {
			LOGGER.error("Cannot execute Jira issue search by JQL: " + jql, e);
			throw new JqlException(e.getFaultString());
		} catch (RemoteException e) {
			LOGGER.error("Cannot execute Jira issue search by JQL: " + jql, e);
			throw new JqlException(e.getMessage());
		}
		if (issuesFromJQLSearch != null) {
			List<RemoteIssue> results = Arrays.asList(issuesFromJQLSearch);
			return results;
		} else {
			return new ArrayList<RemoteIssue>();
		}
	}

	public boolean updateIssueWorkflowStatus(SOAPSession session, String issueKey, String workflowActionName) {
		String token = session.getAuthenticationToken();
		JiraSoapService jiraSoapService = session.getJiraSoapService();
		RemoteNamedObject[] actions = null;
		LOGGER.info("Attempting to update status for issue: " + issueKey + " by executing workflow action: " + workflowActionName);
		try {
			actions = jiraSoapService.getAvailableActions(token, issueKey);
		} catch (com.atlassian.jira.rpc.soap.client.RemoteException e) {
			LOGGER.error("Error getting available issue workflow actions", e);
		} catch (RemoteException e) {
			LOGGER.error("Error getting issue workflow actions", e);
		}
		boolean statusUpdated = false;
		boolean workflowActionExists = false;
		if (actions != null) {
			for (RemoteNamedObject action : actions) {
				LOGGER.info(action.getName() + "\t id " + action.getId());
				if (action.getName().equalsIgnoreCase(workflowActionName)) {
					workflowActionExists = true;
					try {
						jiraSoapService.progressWorkflowAction(token, issueKey, action.getId(), null);
						statusUpdated = true;
						LOGGER.info("Successfully updated status for issue: " + issueKey);
					} catch (com.atlassian.jira.rpc.soap.client.RemoteException e) {
						LOGGER.error("Error updating issue workflow status", e);
					} catch (RemoteException e) {
						LOGGER.error("Error updating issue workflow status", e);
					}
				}
			}
		}
		if (!statusUpdated) {
			LOGGER.error("Could not update status for issue: " + issueKey);
		}
		if (!workflowActionExists) {
			LOGGER.error("Executing workflow action '" + workflowActionName + "' is not allowed for issue: " + issueKey);
		}

		return statusUpdated;
	}

	public boolean addIssueComment(SOAPSession session, final String issueKey, String commentText) {
		String token = session.getAuthenticationToken();
		JiraSoapService jiraSoapService = session.getJiraSoapService();
		RemoteComment comment = new RemoteComment();
		comment.setBody(commentText);
		String errorMessage = "Error adding comment to issue: " + issueKey;
		boolean commentAdded = false;
		try {
			jiraSoapService.addComment(token, issueKey, comment);
			commentAdded = true;
		} catch (RemotePermissionException e) {
			LOGGER.error(errorMessage, e);
		} catch (RemoteAuthenticationException e) {
			LOGGER.error(errorMessage, e);
		} catch (com.atlassian.jira.rpc.soap.client.RemoteException e) {
			LOGGER.error(errorMessage, e);
		} catch (RemoteException e) {
			LOGGER.error(errorMessage, e);
		}
		return commentAdded;
	}
	
	/**
	 * Returns all versions defined for specified project.
	 * @param session
	 * @param projectKey	project key
	 * @return
	 */
	public List<RemoteVersion> getVersions( SOAPSession session, String projectKey ) {
		String token = session.getAuthenticationToken();
		JiraSoapService soap = session.getJiraSoapService();
		
		List<RemoteVersion> versions = new ArrayList<RemoteVersion>();
		try	{
			RemoteVersion[] remoteVersions = soap.getVersions( token, projectKey );
			if ( remoteVersions != null ) {
				for( RemoteVersion ver : remoteVersions ) {
					versions.add( ver );
				}
			}
		} catch ( RemoteException e ) {
			LOGGER.error( "Error getting versions for project " + projectKey, e);
		}
		return versions;
	}

	/**
	 * Returns all custom fields defined in Jira instance
	 * @param session
	 * @return
	 */
	public List<RemoteField> getCustomFields( SOAPSession session ) {
		String token = session.getAuthenticationToken();
		JiraSoapService soap = session.getJiraSoapService();

		List<RemoteField> customFields = new ArrayList<RemoteField>();
		try	{
			RemoteField[] remoteCustomFields = soap.getCustomFields(token);
			if ( remoteCustomFields != null ) {
				for( RemoteField customField : remoteCustomFields ) {
					customFields.add( customField );
				}
			}
		} catch ( RemoteException e ) {
			LOGGER.error( "Error getting list of custom fields for Jira instance.", e);
		}
		return customFields;
	}

	/**
	 * Updates fixedVersions of the specified jira issue.
	 * @param session
	 * @param issue
	 * @param finalVersionIds	collection of <b>id</b> of the fixed versions -
	 * 							numbers in text format (also Strings)
	 * @return
	 */
	public boolean updateFixedVersions( SOAPSession session, final RemoteIssue issue, Collection<String> finalVersionIds )
	{
		return updateIssueField(session, issue.getKey(), "fixVersions", finalVersionIds.toArray( new String[finalVersionIds.size()] ));
	}

	public boolean updateIssueField(SOAPSession session, final String issueKey, String fieldId, String fieldValue)
	{
		return updateIssueField(session, issueKey, fieldId, new String[]{fieldValue});
	}

	public boolean updateIssueField(SOAPSession session, final String issueKey, String fieldId, String[] fieldValue)
	{
		String token = session.getAuthenticationToken();
		JiraSoapService jiraSoapService = session.getJiraSoapService();

		RemoteFieldValue rf = new RemoteFieldValue(fieldId, fieldValue);

		boolean successful = false;
		try {
			jiraSoapService.updateIssue( token, issueKey,  new RemoteFieldValue[] { rf} );
			successful = true;
		} catch (RemoteException e) {
			LOGGER.error( "Error setting " + fieldId + " to issue: " + issueKey, e);
		}
		return successful;
	}

}
