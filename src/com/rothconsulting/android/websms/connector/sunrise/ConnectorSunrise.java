/*
 * Copyright (C) 2010 Koni
 * 
 * This file is only usefull as part of WebSMS.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */
package com.rothconsulting.android.websms.connector.sunrise;

import java.net.HttpURLConnection;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import de.ub0r.android.websms.connector.common.Connector;
import de.ub0r.android.websms.connector.common.ConnectorCommand;
import de.ub0r.android.websms.connector.common.ConnectorSpec;
import de.ub0r.android.websms.connector.common.ConnectorSpec.SubConnectorSpec;
import de.ub0r.android.websms.connector.common.Utils;
import de.ub0r.android.websms.connector.common.Utils.HttpOptions;
import de.ub0r.android.websms.connector.common.WebSMSException;

/**
 * AsyncTask to manage IO to Sunrise API.
 * 
 * @author koni
 */
public class ConnectorSunrise extends Connector {
	/** Tag for output. */
	private static final String TAG = "Sunrise";
	/** Dummy String */
	private static final String DUMMY = "???";
	/** Login URL with E-mail. */
	private static final String URL_EMAIL_LOGIN = "https://mip.sunrise.ch/mip/dyn/login/login";
	/** SMS URL with E-Mail. */
	private static final String URL_EMAIL_SENDSMS = "https://mip.sunrise.ch/mip/dyn/sms/sms?up_contactsPerPage=14&amp;lang=de&amp;country=us&amp;.lang=de&amp;.country=us&amp;synd=ig&amp;mid=36&amp;ifpctok=4219904978209905668&amp;exp_track_js=1&amp;exp_ids=17259&amp;parent=http://partnerpage.google.com&amp;libs=7ndonz73vUA/lib/liberror_tracker.js,RNMmLHDUuvI/lib/libcore.js,OqjxSeEKc8o/lib/libdynamic-height.js&amp;view=home";
	/** Login URL with Tel-Nr. */
	private static final String URL_TEL_LOGIN = "https://www1.sunrise.ch/is-bin/INTERSHOP.enfinity/WFS/Sunrise-Residential-Site/de_CH/-/CHF/ViewApplication-Login";
	/** URL to get balance */
	private static final String URL_TEL_SMS_SENDER = "https://www1.sunrise.ch/is-bin/INTERSHOP.enfinity/WFS/Sunrise-Residential-Site/de_CH/-/CHF/ViewStandardCatalog-Browse?CatalogCategoryID=cK7AqFI.H90AAAEvTK41fuRr";
	/** SMS URL with Tel-Nr. */
	private static final String URL_TEL_SENDSMS = "http://mip.sunrise.ch/mip/dyn/login/smsMeinKonto?lang=de";
	/** URL when multiple numbers present */
	private static final String URL_CHOOSE_NUMBER = "https://www1.sunrise.ch/is-bin/INTERSHOP.enfinity/WFS/Sunrise-Residential-Site/de_CH/-/CHF/ViewECareMessaging-Encrypt";

	/** SMS Credit */
	private String SMS_CREDIT = DUMMY;
	/** HTTP User agent. */
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:14.0) Gecko/20100101 Firefox/14.0.1";
	/** SMS Encoding */
	private static final String ENCODING = "UTF-8";
	/** Check whether this connector is bootstrapping. */
	private static boolean inBootstrap = false;
	/** The phone number from parsing the http response */
	private String PHONE_NUMBER = DUMMY;
	/** Only when mobile number is entered, check for sender errors. */
	private static boolean checkForSenderErrors = false;
	/** My Ad-ID */
	private static final String AD_UNITID = "a14ed1536d6c700";

	private boolean isLoginWithEmail(final SharedPreferences p) {
		if (p != null) {
			String username = p.getString(Preferences.PREFS_USER, "");
			if (username.indexOf("@") > 0) {
				return true;
			}
		}
		return false;
	}

	private boolean isDefinedSenderEntered(final SharedPreferences p) {
		if (p != null) {
			String definedSender = p.getString(
					Preferences.PREFS_DEFINED_SENDER_NUMBER, "");
			if (definedSender != null && definedSender.length() > 9) {
				this.log("*********** isDefinedSenderEntered = true definedSender="
						+ definedSender);
				return true;
			}
		}
		this.log("*********** isDefinedSenderEntered = false");

		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec initSpec(final Context context) {
		final String name = context.getString(R.string.connector_sunrise_name);
		ConnectorSpec c = new ConnectorSpec(name);
		c.setAuthor(context.getString(R.string.connector_sunrise_author));
		c.setBalance(null);
		c.setLimitLength(480);
		c.setAdUnitId(AD_UNITID);
		c.setCapabilities(ConnectorSpec.CAPABILITIES_BOOTSTRAP
				| ConnectorSpec.CAPABILITIES_UPDATE
				| ConnectorSpec.CAPABILITIES_SEND
				| ConnectorSpec.CAPABILITIES_PREFS);
		c.addSubConnector("sunrise", c.getName(),
				SubConnectorSpec.FEATURE_MULTIRECIPIENTS);
		return c;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec updateSpec(final Context context,
			final ConnectorSpec connectorSpec) {
		this.log("************************************************");
		this.log("*** Start updateSpec");
		this.log("************************************************");
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);
		if (p.getBoolean(Preferences.PREFS_ENABLED, false)) {
			if (p.getString(Preferences.PREFS_USER, "").length() > 0
					&& p.getString(Preferences.PREFS_PASSWORD, "") // .
							.length() > 0) {
				connectorSpec.setReady();
			} else {
				connectorSpec.setStatus(ConnectorSpec.STATUS_ENABLED);
			}
		} else {
			connectorSpec.setStatus(ConnectorSpec.STATUS_INACTIVE);
		}
		this.log("************************************************");
		this.log("*** End updateSpec");
		this.log("************************************************");
		return connectorSpec;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doBootstrap(final Context context, final Intent intent)
			throws WebSMSException {
		this.log("************************************************");
		this.log("*** Start doBootstrap");
		this.log("************************************************");
		checkForSenderErrors = false;

		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);

		if (inBootstrap && !this.SMS_CREDIT.equals(DUMMY)
				&& !this.PHONE_NUMBER.equals(DUMMY)) {
			this.log("already in bootstrap: skip bootstrap");
			return;
		}

		this.log("In new bootstrap");

		inBootstrap = true;

		String username = p.getString(Preferences.PREFS_USER, "");
		String password = p.getString(Preferences.PREFS_PASSWORD, "");

		ArrayList<BasicNameValuePair> postParameter = new ArrayList<BasicNameValuePair>();

		// Login-Username kann E-Mail oder Telefonnummer sein.
		if (this.isLoginWithEmail(p)) {
			this.log("**** Login mit E-Mail");
			postParameter.add(new BasicNameValuePair("username", username));
			postParameter.add(new BasicNameValuePair("password", password));
			postParameter.add(new BasicNameValuePair("_remember", "on"));
			this.sendData(URL_EMAIL_LOGIN, context, postParameter, false);
		} else {
			this.log("**** Login mit Telefonnummer");
			postParameter.add(new BasicNameValuePair("LoginForm_Login",
					username));
			postParameter.add(new BasicNameValuePair("LoginForm_Password",
					password));
			postParameter.add(new BasicNameValuePair("LoginRedirectSecret",
					"d285715d94fb4cd4613ad70aaeb8f735"));
			postParameter
					.add(new BasicNameValuePair(
							"LoginRedirectURL",
							"https://www1.sunrise.ch/is-bin/INTERSHOP.enfinity/WFS/Sunrise-Residential-Site/de_CH/-/CHF/ViewStandardCatalog-Browse?CatalogCategoryID=zv7AqFI.Z.gAAAEkbGdQzDCf&LoginOrigin=Mein%20Konto&LoginDestination=Mein%20Konto"));
			postParameter
					.add(new BasicNameValuePair(
							"ajaxhref",
							"http://www1.sunrise.ch/is-bin/INTERSHOP.enfinity/WFS/Sunrise-Residential-Site/de_CH/-/CHF/ViewPersonalCodeRetrieval-New?SpcRetrievalMode=ECare"));
			postParameter.add(new BasicNameValuePair("ajaxhref", ""));

			this.sendData(URL_TEL_LOGIN, context, postParameter, false);
		}

		this.log("**** at end of bootstrap phonenumber=" + this.PHONE_NUMBER);
		this.log("************************************************");
		this.log("*** Ende doBootstrap");
		this.log("************************************************");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doUpdate(final Context context, final Intent intent)
			throws WebSMSException {
		this.log("************************************************");
		this.log("*** Start doUpdate");
		this.log("************************************************");
		this.doBootstrap(context, intent);

		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);

		if (this.isLoginWithEmail(p)) {
			this.sendData(URL_EMAIL_SENDSMS, context, null, true);
		} else {
			// if default sender for multiple numbers is present
			if (this.isDefinedSenderEntered(p)) {
				String defaultSender = p.getString(
						Preferences.PREFS_DEFINED_SENDER_NUMBER, "");
				ArrayList<BasicNameValuePair> postParameter = new ArrayList<BasicNameValuePair>();
				postParameter.add(new BasicNameValuePair("PhoneNumber",
						defaultSender));
				this.sendData(URL_CHOOSE_NUMBER, context, postParameter, true);
			}

			this.sendData(URL_TEL_SMS_SENDER, context, null, true); // cookie
			this.sendData(URL_TEL_SENDSMS, context, null, true); // get credit
		}

		this.log("******* doUpdate PhoneNumber=" + this.PHONE_NUMBER);
		this.getSpec(context).setBalance(this.SMS_CREDIT);

		this.log("************************************************");
		this.log("*** Ende doUpdate");
		this.log("************************************************");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doSend(final Context context, final Intent intent)
			throws WebSMSException {
		this.log("************************************************");
		this.log("*** Start doSend");
		this.log("************************************************");
		this.doBootstrap(context, intent);

		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);

		ConnectorCommand command = new ConnectorCommand(intent);
		StringBuilder recipients = new StringBuilder();
		// SMS Text
		String text = command.getText();
		this.log("text.length()=" + text.length());
		this.log("text=" + text);
		String charsLeft = "";
		if (text != null) {
			if (text.length() <= 160) {
				charsLeft = "" + (160 - text.length()) + " / 1";
			}
			if (text.length() > 160 && text.length() <= 320) {
				charsLeft = "" + (320 - text.length()) + " / 2";
			}
			if (text.length() > 320 && text.length() <= 480) {
				charsLeft = "" + (480 - text.length()) + " / 3";
			}
			if (text.length() > 480) {
				text = text.substring(0, 480);
				this.log("text gekürzt. length=" + text.length());
				charsLeft = "" + (480 - text.length()) + " / 3";
			}
		}
		this.log("charsLeft=" + charsLeft);

		// SMS Recipients
		String[] to = command.getRecipients();
		if (to == null || to.length > 10) {
			String error = context
					.getString(R.string.connector_sunrise_max_10_recipients);
			this.log("----- throwing WebSMSException: " + error);
			throw new WebSMSException(error);
		}
		for (int i = 0; i < to.length; i++) {
			if (to[i] != null && to[i].length() > 1) {
				if (i > 0) {
					recipients.append(",");
				}
				recipients.append(to[i].trim());
			}
		}
		this.log("to.length=" + to.length);
		this.log("to[0]    =" + to[0]);
		this.log("all recipients=" + recipients);

		// if defined sender for multiple numbers is present
		if (!this.isLoginWithEmail(p) && this.isDefinedSenderEntered(p)) {

			String definedSender = p.getString(
					Preferences.PREFS_DEFINED_SENDER_NUMBER, "");

			if (definedSender.trim().startsWith("+417")) {
				definedSender = definedSender.replace("+417", "07");
			}
			if (definedSender.trim().startsWith("+4107")) {
				definedSender = definedSender.replace("+41", "");
			}

			ArrayList<BasicNameValuePair> postParameter = new ArrayList<BasicNameValuePair>();
			postParameter.add(new BasicNameValuePair("PhoneNumber",
					definedSender));
			this.sendData(URL_CHOOSE_NUMBER, context, postParameter, true);

			this.log("******* definedSender =" + definedSender);
			checkForSenderErrors = true;
			this.PHONE_NUMBER = definedSender;

		}

		if (this.PHONE_NUMBER.equals(DUMMY)) {
			this.doUpdate(context, intent);
		}
		this.log("******* post PhoneNumber nachher=" + this.PHONE_NUMBER);

		// Building POST parameter
		ArrayList<BasicNameValuePair> postParameter = new ArrayList<BasicNameValuePair>();
		postParameter.add(new BasicNameValuePair("recipient", recipients
				.toString()));
		postParameter.add(new BasicNameValuePair("charsLeft", charsLeft));
		postParameter.add(new BasicNameValuePair("type", "sms"));
		postParameter.add(new BasicNameValuePair("message", text));
		postParameter.add(new BasicNameValuePair("send", "send"));
		postParameter.add(new BasicNameValuePair("task", "send"));
		postParameter.add(new BasicNameValuePair("currentMsisdn",
				this.PHONE_NUMBER));

		this.log("****** = PHONE_NUMBER = " + this.PHONE_NUMBER);

		if (this.isLoginWithEmail(p)) {
			this.sendData(URL_EMAIL_SENDSMS, context, postParameter, true);
		} else {
			this.sendData(URL_TEL_SENDSMS, context, postParameter, true);
		}

		this.log("************************************************");
		this.log("*** Ende doSend");
		this.log("************************************************");
	}

	/**
	 * Sending the SMS
	 * 
	 * @param fullTargetURL
	 * @param context
	 * @param postParameter
	 * @throws WebSMSException
	 */
	private void sendData(final String fullTargetURL, final Context context,
			final ArrayList<BasicNameValuePair> postParameter,
			final boolean parseHtml) throws WebSMSException {

		this.log("************************************************");
		this.log("*** Start sendData");
		this.log("************************************************");
		try {

			this.log("URL: " + fullTargetURL);
			// send data
			this.log("prepare: getHttpClient(...)");

			HttpOptions httpOptions = new HttpOptions();
			httpOptions.url = fullTargetURL;
			httpOptions.userAgent = USER_AGENT;
			// httpOptions.encoding = ENCODING;
			httpOptions.trustAll = true;
			this.log("UrlEncodedFormEntity()");
			if (postParameter != null) {
				httpOptions.postData = new UrlEncodedFormEntity(postParameter,
						ENCODING);
			}

			this.log("send data: getHttpClient(...)");
			HttpResponse response = Utils.getHttpClient(httpOptions);

			// HttpResponse response = Utils.getHttpClient(fullTargetURL, null,
			// postParameter, USER_AGENT, fullTargetURL, ENCODING, true);

			int respStatus = response.getStatusLine().getStatusCode();
			this.log("response status=" + respStatus);
			this.log("response=\n" + response);
			if (respStatus != HttpURLConnection.HTTP_OK) {
				throw new WebSMSException(context, R.string.error_http, ""
						+ respStatus);
			}
			String htmlText = Utils.stream2str(
					response.getEntity().getContent()).trim();
			if (htmlText == null || htmlText.length() == 0) {
				throw new WebSMSException(context, R.string.error_service);
			}
			this.log("----- Start HTTP RESPONSE--");
			this.log(htmlText);
			this.log("----- End HTTP RESPONSE--");
			if (parseHtml) {
				this.getPhoneNumber(htmlText, context);
				String guthabenGratis = this.getGuthabenGratis(htmlText,
						context);
				String guthabenBezahlt = this.getGuthabenBezahlt(htmlText,
						context);
				String errorMessage = this.getErrorBlockMessage(htmlText,
						context);

				if (guthabenGratis != null && !guthabenGratis.equals("")) {
					guthabenGratis = context
							.getString(R.string.connector_sunrise_gratis)
							+ "="
							+ guthabenGratis;
					this.SMS_CREDIT = guthabenGratis;
				}
				if (guthabenBezahlt != null && !guthabenBezahlt.equals("")
						&& !guthabenBezahlt.trim().equals("0")) {
					guthabenBezahlt = ", "
							+ context
									.getString(R.string.connector_sunrise_bezahlt)
							+ "=" + guthabenBezahlt;
					this.SMS_CREDIT = guthabenGratis + guthabenBezahlt;
				}
				if (errorMessage != null && !errorMessage.equals("")) {
					this.log("----- throwing WebSMSException: " + errorMessage);
					throw new WebSMSException(errorMessage);
				}

				this.getSpec(context).setBalance(this.SMS_CREDIT);
			}

			htmlText = null;

		} catch (Exception e) {
			Log.e(TAG, null, e);
			throw new WebSMSException(e.getMessage());
		}
		this.log("************************************************");
		this.log("*** Ende sendData");
		this.log("************************************************");

	}

	private String getGuthabenGratis(final String htmlText,
			final Context context) {
		String guthabenGratis = "";
		int indexStartSMSCredit = htmlText.indexOf("Gratis ");
		if (indexStartSMSCredit > 0) {
			guthabenGratis = htmlText.substring(indexStartSMSCredit + 7,
					indexStartSMSCredit + 9);
		}
		this.log("indexOf Gratis=" + indexStartSMSCredit + " -- Gratis="
				+ guthabenGratis);

		return guthabenGratis;
	}

	private String getGuthabenBezahlt(final String htmlText,
			final Context context) {
		String guthabenBezahlt = "";
		int indexStartSMSCredit = htmlText.indexOf("Bezahlt ");
		if (indexStartSMSCredit > 0) {
			guthabenBezahlt = htmlText.substring(indexStartSMSCredit + 8,
					indexStartSMSCredit + 10);
		}
		this.log("indexOf Bezahlt =" + indexStartSMSCredit + " -- Bezahlt="
				+ guthabenBezahlt);

		return guthabenBezahlt;
	}

	private void getPhoneNumber(final String htmlText, final Context context) {
		if (this.PHONE_NUMBER.equals(DUMMY)) {
			int indexStartPhoneNumber = htmlText.indexOf("currentMsisdn");
			if (indexStartPhoneNumber > 0) {
				this.PHONE_NUMBER = htmlText.substring(
						indexStartPhoneNumber + 22, indexStartPhoneNumber + 32);
			}
			this.log("******* indexOf PhoneNumber =" + indexStartPhoneNumber);
		}
		this.log("******* PhoneNumber=" + this.PHONE_NUMBER);

	}

	private String getErrorBlockMessage(final String htmlText,
			final Context context) {
		String message = "";
		if (checkForSenderErrors) {
			int indexStartErrorBlock = htmlText.indexOf("errorBlock");
			int indexEndeErrorBlock = htmlText
					.indexOf("Die SMS/MMS wurde nicht versandt");

			if (indexStartErrorBlock > 0 && indexEndeErrorBlock > 0) {
				message = htmlText.substring(indexStartErrorBlock + 28,
						indexEndeErrorBlock);
			}
			this.log("indexStartOf errorBlock =" + indexStartErrorBlock
					+ ", indexEndeOf errorBlock =" + indexEndeErrorBlock
					+ " -- Message=" + message);

			if (message.trim().startsWith(
					"Die Absendernummer hat sich geändert")) {
				message = context
						.getString(R.string.connector_sunrise_wrong_mobilenumber);
			} else {
				message = "";
			}
		}
		return message.trim();
	}

	/**
	 * central logger
	 * 
	 * @param message
	 */
	private void log(final String message) {
		// Log.d(TAG, message);
	}
}
