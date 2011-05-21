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
import de.ub0r.android.websms.connector.common.Connector;
import de.ub0r.android.websms.connector.common.ConnectorCommand;
import de.ub0r.android.websms.connector.common.ConnectorSpec;
import de.ub0r.android.websms.connector.common.ConnectorSpec.SubConnectorSpec;
import de.ub0r.android.websms.connector.common.Log;
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
	/** Login URL. */
	private static final String URL_LOGIN = "https://mip.sunrise.ch/mip/dyn/login/login";
	/** SMS URL. */
	private static final String URL_SENDSMS = "https://mip.sunrise.ch/mip/dyn/sms/sms?up_contactsPerPage=14&amp;lang=de&amp;country=us&amp;.lang=de&amp;.country=us&amp;synd=ig&amp;mid=36&amp;ifpctok=4219904978209905668&amp;exp_track_js=1&amp;exp_ids=17259&amp;parent=http://partnerpage.google.com&amp;libs=7ndonz73vUA/lib/liberror_tracker.js,RNMmLHDUuvI/lib/libcore.js,OqjxSeEKc8o/lib/libdynamic-height.js&amp;view=home";
	/** SMS Credit */
	private String SMS_CREDIT = DUMMY;
	/** HTTP User agent. */
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:2.0.1) Gecko/20100101 Firefox/4.0.1";
	/** SMS Encoding */
	private static final String ENCODING = "UTF-8";
	/** Check whether this connector is bootstrapping. */
	private static boolean inBootstrap = false;
	/** The phone number from parsing the http response */
	private String PHONE_NUMBER = DUMMY;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec initSpec(final Context context) {
		final String name = context.getString(R.string.connector_sunrise_name);
		ConnectorSpec c = new ConnectorSpec(name);
		c.setAuthor(context.getString(R.string.connector_sunrise_author));
		c.setBalance(null);
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
		if (inBootstrap && !this.SMS_CREDIT.equals(DUMMY)
				&& !this.PHONE_NUMBER.equals(DUMMY)) {
			Log.d(TAG, "already in bootstrap: skip bootstrap");
			return;
		}
		inBootstrap = true;
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);

		ArrayList<BasicNameValuePair> postParameter = new ArrayList<BasicNameValuePair>();
		postParameter.add(new BasicNameValuePair("username", p.getString(
				Preferences.PREFS_USER, "")));
		postParameter.add(new BasicNameValuePair("password", p.getString(
				Preferences.PREFS_PASSWORD, "")));
		postParameter.add(new BasicNameValuePair("_remember", "on"));

		this.sendData(URL_LOGIN, context, postParameter, false);
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

		this.sendData(URL_SENDSMS, context, null, true);
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
		for (int i = 0; i < to.length; i++) {
			if (to[i] != null && to[i].length() > 1) {
				if (i > 0) {
					recipients.append(",");
				}
				recipients.append(Utils.national2international(
						command.getDefPrefix(),
						Utils.getRecipientsNumber(to[i])));
			}
		}

		Log.d(TAG, "to.length=" + to.length);
		Log.d(TAG, "to[0]=" + to[0]);
		Log.d(TAG, "all recipients=" + recipients);

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
		this.sendData(URL_SENDSMS, context, postParameter, true);
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
			// Log.d(TAG, "--Start HTTP RESPONSE--");
			// Log.d(TAG, htmlText);
			// Log.d(TAG, "--End HTTP RESPONSE--");
			if (parseHtml) {
				this.getPhoneNumber(htmlText, context);
				String guthabenGratis = this.getGuthabenGratis(htmlText,
						context);
				String guthabenBezahlt = this.getGuthabenBezahlt(htmlText,
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

}
