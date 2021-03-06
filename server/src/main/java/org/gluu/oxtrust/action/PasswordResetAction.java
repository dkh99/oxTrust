/*
 * oxTrust is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.gluu.oxtrust.action;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import javax.enterprise.context.ConversationScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Size;

import org.gluu.jsf2.message.FacesMessages;
import org.gluu.jsf2.service.ConversationService;
import org.gluu.oxtrust.ldap.service.ApplianceService;
import org.gluu.oxtrust.ldap.service.OxTrustAuditService;
import org.gluu.oxtrust.ldap.service.PersonService;
import org.gluu.oxtrust.ldap.service.RecaptchaService;
import org.gluu.oxtrust.model.GluuAppliance;
import org.gluu.oxtrust.model.GluuCustomAttribute;
import org.gluu.oxtrust.model.GluuCustomPerson;
import org.gluu.oxtrust.model.PasswordResetRequest;
import org.gluu.oxtrust.security.Identity;
import org.gluu.oxtrust.service.PasswordResetService;
import org.gluu.oxtrust.util.OxTrustConstants;
import org.gluu.site.ldap.persistence.LdapEntryManager;
import org.gluu.site.ldap.persistence.exception.EntryPersistenceException;
import org.slf4j.Logger;
import org.xdi.config.oxtrust.AppConfiguration;
import org.xdi.util.StringHelper;

/**
 * User: Dejan Maric
 */
@ConversationScoped
@Named("passwordResetAction")
public class PasswordResetAction implements Serializable {

	private static final long serialVersionUID = 6457422770824016614L;

	@Inject
	private Logger log;

	@Inject
	private LdapEntryManager ldapEntryManager;

	@Inject
	private FacesMessages facesMessages;

	@Inject
	private ConversationService conversationService;

	@Inject
	private RecaptchaService recaptchaService;

	@Inject
	private ApplianceService applianceService;

	@Inject
	private PersonService personService;

	@Inject
	private PasswordResetService passwordResetService;

	@Inject
	private Identity identity;

	@Inject
	private OxTrustAuditService oxTrustAuditService;

	@Inject
	private AppConfiguration appConfiguration;

	private PasswordResetRequest request;
	private String guid;
	private String securityQuestion;
	private String securityAnswer;
	@Size(min = 3, max = 60, message = "Password length must be between {min} and {max} characters.")
	private String password;
	@Size(min = 3, max = 60, message = "Password length must be between {min} and {max} characters.")
	private String confirm;

	public String start() throws ParseException {
		if (StringHelper.isEmpty(guid)) {
			sendExpirationError();
			return OxTrustConstants.RESULT_FAILURE;
		}

		// Load requested entry
		PasswordResetRequest passwordResetRequest;
		try {
			passwordResetRequest = passwordResetService.findPasswordResetRequest(guid);
		} catch (EntryPersistenceException ex) {
			log.error("Failed to find password reset request by '{}'", guid, ex);
			sendExpirationError();
			return OxTrustConstants.RESULT_FAILURE;
		}

		if (passwordResetRequest == null) {
			sendExpirationError();
			return OxTrustConstants.RESULT_FAILURE;
		}

		// Load latest entry by person inum
		PasswordResetRequest personPasswordResetRequest = passwordResetService
				.findActualPasswordResetRequest(passwordResetRequest.getPersonInum());
		if (personPasswordResetRequest == null) {
			sendExpirationError();
			return OxTrustConstants.RESULT_FAILURE;
		}

		// Check if requested entry is actual one
		if (!StringHelper.equalsIgnoreCase(guid, personPasswordResetRequest.getOxGuid())) {
			sendExpirationError();
			return OxTrustConstants.RESULT_FAILURE;
		}

		this.request = personPasswordResetRequest;

		Calendar requestCalendarExpiry = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		Calendar currentCalendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));

		if (request != null) {
			requestCalendarExpiry.setTime(request.getCreationDate());
		}
		currentCalendar.add(Calendar.SECOND, -appConfiguration.getPasswordResetRequestExpirationTime());
		GluuCustomPerson person = personService.getPersonByInum(request.getPersonInum());

		GluuCustomAttribute question = null;
		if (person != null) {
			question = person.getGluuCustomAttribute("secretQuestion");
		}

		if ((request != null) && requestCalendarExpiry.after(currentCalendar)) {
			if (question != null) {
				securityQuestion = question.getValue();
			}
			return OxTrustConstants.RESULT_SUCCESS;
		} else {
			facesMessages.add(FacesMessage.SEVERITY_ERROR,
					"Your link is not valid or your user is not allowed to perform a password reset. If you want to initiate a reset password procedure please fill this form.");
			conversationService.endConversation();

			return OxTrustConstants.RESULT_FAILURE;
		}
	}

	protected void sendExpirationError() {
		facesMessages.add(FacesMessage.SEVERITY_ERROR, "The reset link is no more valid.");
		facesMessages.add(FacesMessage.SEVERITY_ERROR, "\n\n");
		facesMessages.add(FacesMessage.SEVERITY_ERROR,
				"It has already been used to reset your password or it has expired.");
		facesMessages.add(FacesMessage.SEVERITY_ERROR, "\n\n");
		facesMessages.add(FacesMessage.SEVERITY_ERROR, "Re-enter your e-mail to generate a new link.");
		conversationService.endConversation();
	}

	public String update() throws ParseException {
		String outcome = updateImpl();

		if (OxTrustConstants.RESULT_SUCCESS.equals(outcome)) {
			facesMessages.add(FacesMessage.SEVERITY_INFO, "Password reset successful.");
			conversationService.endConversation();
		} else if (OxTrustConstants.RESULT_FAILURE.equals(outcome)) {
			facesMessages.add(FacesMessage.SEVERITY_ERROR,
					"Your secret answer or Captcha code may have been wrong. Please try to correct it or contact your administrator to change your password.");
			conversationService.endConversation();
		}

		return outcome;
	}

	public String updateImpl() throws ParseException {
		boolean valid = true;
		if (recaptchaService.isEnabled()) {
			valid = recaptchaService.verifyRecaptchaResponse();
		}

		if (valid) {
			GluuAppliance appliance = applianceService.getAppliance();
			this.request = ldapEntryManager.find(PasswordResetRequest.class,
					"oxGuid=" + this.guid + ", ou=resetPasswordRequests," + appliance.getDn());
			Calendar requestCalendarExpiry = Calendar.getInstance();
			Calendar currentCalendar = Calendar.getInstance();
			if (request != null) {
				requestCalendarExpiry.setTime((request.getCreationDate()));
				requestCalendarExpiry.add(Calendar.HOUR, 2);
			}
			GluuCustomPerson person = personService.getPersonByInum(request.getPersonInum());
			GluuCustomAttribute question = null;
			GluuCustomAttribute answer = null;
			if (person != null) {
				question = person.getGluuCustomAttribute("secretQuestion");
				answer = person.getGluuCustomAttribute("secretAnswer");
			}
			if (request != null
					&& requestCalendarExpiry.after(currentCalendar) /* && question != null && answer != null */) {
				PasswordResetRequest removeRequest = new PasswordResetRequest();
				removeRequest.setBaseDn(request.getBaseDn());
				ldapEntryManager.remove(removeRequest);
				oxTrustAuditService.audit("PASSWORD RESET REQUEST" + removeRequest.getBaseDn() + " REMOVED",
						identity.getUser(),
						(HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest());
				if (question != null && answer != null) {
					String correctAnswer = answer.getValue();
					Boolean securityQuestionAnswered = (securityAnswer != null) && securityAnswer.equals(correctAnswer);
					if (securityQuestionAnswered) {
						person.setUserPassword(password);
						personService.updatePerson(person);
						return OxTrustConstants.RESULT_SUCCESS;
					}
				} else {
					person.setUserPassword(password);
					personService.updatePerson(person);
					return OxTrustConstants.RESULT_SUCCESS;
				}
			}
		}

		return OxTrustConstants.RESULT_FAILURE;
	}

	public String cancel() {
		return OxTrustConstants.RESULT_SUCCESS;
	}

	public String checkAnswer() {
		return OxTrustConstants.RESULT_SUCCESS;
	}

	@AssertTrue(message = "Different passwords entered!")
	public boolean isPasswordsEquals() {
		return password.equals(confirm);
	}

	public PasswordResetRequest getRequest() {
		return request;
	}

	public String getGuid() {
		return guid;
	}

	public void setGuid(String guid) {
		this.guid = guid;
	}

	public String getSecurityQuestion() {
		return securityQuestion;
	}

	public void setSecurityQuestion(String securityQuestion) {
		this.securityQuestion = securityQuestion;
	}

	public String getSecurityAnswer() {
		return securityAnswer;
	}

	public void setSecurityAnswer(String securityAnswer) {
		this.securityAnswer = securityAnswer;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getConfirm() {
		return confirm;
	}

	public void setConfirm(String confirm) {
		this.confirm = confirm;
	}

}
