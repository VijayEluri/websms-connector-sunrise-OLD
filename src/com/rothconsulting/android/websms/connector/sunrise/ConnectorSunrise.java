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
	/** SMS URL with Tel-Nr. */
	private static final String URL_TEL_SENDSMS = "http://mip.sunrise.ch/mip/dyn/login/smsMeinKonto?lang=de";

	/** SMS Credit */
	private String SMS_CREDIT = DUMMY;
	/** HTTP User agent. */
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:8.0) Gecko/20100101 Firefox/8.0";
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
		Log.d(TAG, "Start updateSpec");
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
		Log.d(TAG, "End updateSpec");
		return connectorSpec;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doBootstrap(final Context context, final Intent intent)
			throws WebSMSException {
		Log.d(TAG, "Start doBootstrap");
		checkForSenderErrors = false;
		if (inBootstrap && !this.SMS_CREDIT.equals(DUMMY)
				&& !this.PHONE_NUMBER.equals(DUMMY)) {
			Log.d(TAG, "already in bootstrap: skip bootstrap");
			return;
		}

		inBootstrap = true;
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);

		String username = p.getString(Preferences.PREFS_USER, "");
		String password = p.getString(Preferences.PREFS_PASSWORD, "");

		ArrayList<BasicNameValuePair> postParameter = new ArrayList<BasicNameValuePair>();

		// Login-Username kann E-Mail oder Telefonnummer sein.
		if (this.isLoginWithEmail(p)) {
			Log.d(TAG, "**** Login mit E-Mail");
			postParameter.add(new BasicNameValuePair("username", username));
			postParameter.add(new BasicNameValuePair("password", password));
			postParameter.add(new BasicNameValuePair("_remember", "on"));
			this.sendData(URL_EMAIL_LOGIN, context, postParameter, false);
		} else {
			Log.d(TAG, "**** Login mit Telefonnummer");
			postParameter.add(new BasicNameValuePair("LoginForm_Login",
					username));
			postParameter.add(new BasicNameValuePair("LoginForm_Password",
					password));
			postParameter
					.add(new BasicNameValuePair(
							"LoginRedirectURL",
							"https://www1.sunrise.ch/is-bin/INTERSHOP.enfinity/WFS/Sunrise-Residential-Site/de_CH/-/CHF/ViewStandardCatalog-Browse?CatalogCategoryID=cK7AqFI.H90AAAEvTK41fuRr"));
			this.sendData(URL_TEL_LOGIN, context, postParameter, false);
		}

		Log.d(TAG, "******* doBootstrap PhoneNumber=" + this.PHONE_NUMBER);
		Log.d(TAG, "End doBootstrap");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doUpdate(final Context context, final Intent intent)
			throws WebSMSException {
		Log.d(TAG, "Start doUpdate");
		this.doBootstrap(context, intent);

		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);

		if (this.isLoginWithEmail(p)) {
			this.sendData(URL_EMAIL_SENDSMS, context, null, true);
		} else {
			this.sendData(URL_TEL_SENDSMS, context, null, true);
		}

		Log.d(TAG, "******* doUpdate PhoneNumber=" + this.PHONE_NUMBER);
		this.getSpec(context).setBalance(this.SMS_CREDIT);

		Log.d(TAG, "End doUpdate");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doSend(final Context context, final Intent intent)
			throws WebSMSException {
		Log.d(TAG, "Start doSend");
		this.doBootstrap(context, intent);

		ConnectorCommand command = new ConnectorCommand(intent);
		StringBuilder recipients = new StringBuilder();
		// SMS Text
		String text = command.getText();
		Log.d(TAG, "text.length()=" + text.length());
		Log.d(TAG, "text=" + text);
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
				Log.d(TAG, "text gekürzt. length=" + text.length());
				charsLeft = "" + (480 - text.length()) + " / 3";
			}
		}
		Log.d(TAG, "charsLeft=" + charsLeft);

		// SMS Recipients
		String[] to = command.getRecipients();
		if (to == null || to.length > 10) {
			String error = context
					.getString(R.string.connector_sunrise_max_10_recipients);
			Log.d(TAG, "----- throwing WebSMSException: " + error);
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
		Log.d(TAG, "to.length=" + to.length);
		Log.d(TAG, "to[0]    =" + to[0]);
		Log.d(TAG, "all recipients=" + recipients);

		// Get Phone number in case it is entered.
		// Sometimes it is needed when you have more than one number in your
		// Sunrise account.
		String phone = command.getDefSender();
		Log.d(TAG, "******* phone 1 =" + phone);

		if (phone != null && !phone.trim().equals("")) {
			if (phone.trim().startsWith("+417")) {
				phone = phone.replace("+417", "07");
			}
			if (phone.trim().startsWith("+4107")) {
				phone = phone.replace("+41", "");
			}
			Log.d(TAG, "******* phone 2 =" + phone);
			checkForSenderErrors = true;
			this.PHONE_NUMBER = phone;
		}

		// Building POST parameter
		ArrayList<BasicNameValuePair> postParameter = new ArrayList<BasicNameValuePair>();
		postParameter.add(new BasicNameValuePair("recipient", recipients
				.toString()));
		postParameter.add(new BasicNameValuePair("charsLeft", charsLeft));
		postParameter.add(new BasicNameValuePair("type", "sms"));
		postParameter.add(new BasicNameValuePair("message", text));
		postParameter.add(new BasicNameValuePair("send", "send"));
		postParameter.add(new BasicNameValuePair("task", "send"));
		Log.d(TAG, "******* post PhoneNumber vorher=" + this.PHONE_NUMBER);
		if (this.PHONE_NUMBER.equals(DUMMY)) {
			this.doUpdate(context, intent);
		}
		postParameter.add(new BasicNameValuePair("currentMsisdn",
				this.PHONE_NUMBER));
		Log.d(TAG, "******* post PhoneNumber nachher=" + this.PHONE_NUMBER);

		// push data

		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);

		if (this.isLoginWithEmail(p)) {
			this.sendData(URL_EMAIL_SENDSMS, context, postParameter, true);
		} else {
			this.sendData(URL_TEL_SENDSMS, context, postParameter, true);
		}

		Log.d(TAG, "End doSend");
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
		Log.d(TAG, "Start sendData");
		try {

			Log.d(TAG, "URL: " + fullTargetURL);
			// send data
			Log.d(TAG, "send data: getHttpClient(...)");
			HttpResponse response = Utils.getHttpClient(fullTargetURL, null,
					postParameter, USER_AGENT, fullTargetURL, ENCODING, true);
			int respStatus = response.getStatusLine().getStatusCode();
			Log.d(TAG, "response status=" + respStatus);
			Log.d(TAG, "response=\n" + response);
			if (respStatus != HttpURLConnection.HTTP_OK) {
				throw new WebSMSException(context, R.string.error_http, ""
						+ respStatus);
			}
			String htmlText = Utils.stream2str(
					response.getEntity().getContent()).trim();
			if (htmlText == null || htmlText.length() == 0) {
				throw new WebSMSException(context, R.string.error_service);
			}
			Log.d(TAG, "----- Start HTTP RESPONSE--");
			Log.d(TAG, htmlText);
			Log.d(TAG, "----- End HTTP RESPONSE--");
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
					Log.d(TAG, "----- throwing WebSMSException: "
							+ errorMessage);
					throw new WebSMSException(errorMessage);
				}

				this.getSpec(context).setBalance(this.SMS_CREDIT);
			}

			htmlText = null;

		} catch (Exception e) {
			Log.e(TAG, null, e);
			throw new WebSMSException(e.getMessage());
		}
	}

	private String getGuthabenGratis(final String htmlText,
			final Context context) {
		String guthabenGratis = "";
		int indexStartSMSCredit = htmlText.indexOf("Gratis ");
		if (indexStartSMSCredit > 0) {
			guthabenGratis = htmlText.substring(indexStartSMSCredit + 7,
					indexStartSMSCredit + 9);
		}
		Log.d(TAG, "indexOf Gratis=" + indexStartSMSCredit + " -- Gratis="
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
		Log.d(TAG, "indexOf Bezahlt =" + indexStartSMSCredit + " -- Bezahlt="
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
			Log.d(TAG, "******* indexOf PhoneNumber =" + indexStartPhoneNumber);
		}
		Log.d(TAG, "******* PhoneNumber=" + this.PHONE_NUMBER);

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
			Log.d(TAG, "indexStartOf errorBlock =" + indexStartErrorBlock
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

}
