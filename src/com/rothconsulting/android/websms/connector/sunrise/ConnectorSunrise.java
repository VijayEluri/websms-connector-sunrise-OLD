/*
 * Copyright (C) 2010 Koni Roth
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

	/** Login URL. */
	private static final String URL_LOGIN = "https://mip.sunrise.ch/mip/dyn/login/login";
	/** SMS URL. */
	private static final String URL_SENDSMS = "https://mip.sunrise.ch/mip/dyn/sms/sms?up_contactsPerPage=14&lang=de&country=us&.lang=de&.country=us&synd=ig&mid=36&ifpctok=8799310261136394284&exp_track_js=1&parent=http://partnerpage.google.com&libs=7ndonz73vUA/lib/liberror_tracker.js,vrFMICQBNJo/lib/libcore.js,OqjxSeEKc8o/lib/libdynamic-height.js&view=home";
	/** SMS Credit */
	private String SMS_CREDIT = "???";
	/** HTTP Useragent. */
	private static final String USER_AGENT = "Mozilla/5.0 (Windows; U; Windows NT 6.1; de; rv:1.9.2.8) Gecko/20100722 Firefox/3.6.8";
	/** Default Character Encoding */
	private static final String SMS_CHARACTER_ENCODING = "UTF-8";
	/** Check whether this connector is bootstrapping. */
	private static boolean inBootstrap = false;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec initSpec(final Context context) {
		final String name = context.getString(R.string.connector_sunrise_name);
		ConnectorSpec c = new ConnectorSpec(name);
		c.setAuthor(context.getString(R.string.connector_sunrise_author));
		c.setBalance(null);
		c.setCapabilities(ConnectorSpec.CAPABILITIES_BOOTSTRAP | ConnectorSpec.CAPABILITIES_UPDATE
				| ConnectorSpec.CAPABILITIES_SEND | ConnectorSpec.CAPABILITIES_PREFS);
		c.addSubConnector("sunrise", c.getName(), SubConnectorSpec.FEATURE_MULTIRECIPIENTS);
		return c;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec updateSpec(final Context context, final ConnectorSpec connectorSpec) {
		Log.d(TAG, "Start updateSpec");
		final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);
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
		if (inBootstrap) {
			Log.d(TAG, "already in bootstrap: skip bootstrap");
			return;
		}
		inBootstrap = true;
		final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);

		ArrayList<BasicNameValuePair> postParameter = new ArrayList<BasicNameValuePair>();
		postParameter.add(new BasicNameValuePair("username", p
				.getString(Preferences.PREFS_USER, "")));
		postParameter.add(new BasicNameValuePair("password", p.getString(
				Preferences.PREFS_PASSWORD, "")));
		postParameter.add(new BasicNameValuePair("_remember", "on"));

		this.sendData(URL_LOGIN, context, postParameter);
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

		ArrayList<BasicNameValuePair> postParameter = new ArrayList<BasicNameValuePair>();

		this.sendData(URL_SENDSMS, context, postParameter);
		this.getSpec(context).setBalance("SMS=" + this.SMS_CREDIT);

		Log.d(TAG, "End doUpdate");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doSend(final Context context, final Intent intent) throws WebSMSException {
		Log.d(TAG, "Start doSend");
		this.doBootstrap(context, intent);

		ConnectorCommand command = new ConnectorCommand(intent);
		StringBuilder recipients = new StringBuilder();
		// SMS Text
		String text = command.getText();
		Log.d(TAG, "text=" + text);
		// SMS Receiver
		String[] to = command.getRecipients();
		for (int i = 0; i < to.length; i++) {
			if (to[i] != null && to[i].length() > 1) {
				if (i > 0) {
					recipients.append(",");
				}
				recipients.append(Utils.national2international(command.getDefPrefix(),
						Utils.getRecipientsNumber(to[i])));
			}
		}

		Log.d(TAG, "to.length=" + to.length);
		Log.d(TAG, "to[0]=" + to[0]);
		Log.d(TAG, "all recipients=" + recipients);
		ArrayList<BasicNameValuePair> postParameter = new ArrayList<BasicNameValuePair>();

		postParameter.add(new BasicNameValuePair("recipient", recipients.toString()));
		postParameter.add(new BasicNameValuePair("charsLeft", "" + (160 - text.length())));
		postParameter.add(new BasicNameValuePair("type", "sms"));
		postParameter.add(new BasicNameValuePair("message", text));
		postParameter.add(new BasicNameValuePair("send", "send"));
		postParameter.add(new BasicNameValuePair("task", "send"));
		// postParameter.add(new BasicNameValuePair("mmsAttachment", ""));
		// postParameter.add(new BasicNameValuePair("mmsAttachmentFileName",
		// ""));

		// push data
		this.sendData(URL_SENDSMS, context, postParameter);
		Log.d(TAG, "End doSend");
	}

	/**
	 * Send data.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param packetData
	 *            packetData
	 * @throws WebSMSException
	 *             WebSMSException
	 */
	private void sendData(final String fullTargetURL, final Context context,
			final ArrayList<BasicNameValuePair> postParameter) throws WebSMSException {
		Log.d(TAG, "Start sendData");
		try { // get Connection

			Log.d(TAG, "--HTTP POST--");
			Log.d(TAG, "URL: " + fullTargetURL);
			Log.d(TAG, "--HTTP POST--");
			// send data
			Log.d(TAG, "send data: getHttpClient(...)");
			HttpResponse response = Utils.getHttpClient(fullTargetURL, null, postParameter,
					USER_AGENT, fullTargetURL, SMS_CHARACTER_ENCODING, true);
			Log.d(TAG, "response=" + response);
			int resp = response.getStatusLine().getStatusCode();
			Log.d(TAG, "int resp=" + resp);
			if (resp != HttpURLConnection.HTTP_OK) {
				throw new WebSMSException(context, R.string.error_http, "" + resp);
			}
			String htmlText = Utils.stream2str(response.getEntity().getContent()).trim();
			if (htmlText == null || htmlText.length() == 0) {
				throw new WebSMSException(context, R.string.error_service);
			}
			Log.d(TAG, "--Start HTTP RESPONSE--");
			Log.d(TAG, htmlText);
			Log.d(TAG, "--End HTTP RESPONSE--");

			int indexStartSMSCredit = htmlText.indexOf("Gratis ");
			if (indexStartSMSCredit == -1) {
				indexStartSMSCredit = htmlText.indexOf("Gratuits ");
			}
			if (indexStartSMSCredit > 0) {
				this.SMS_CREDIT = htmlText.substring(indexStartSMSCredit + 7,
						indexStartSMSCredit + 9);
			}
			Log.d(TAG, "indexOf SMS gratis=" + indexStartSMSCredit + " -- " + this.SMS_CREDIT);

			this.getSpec(context).setBalance("SMS=" + this.SMS_CREDIT);

			htmlText = null;

		} catch (Exception e) {
			Log.e(TAG, null, e);
			throw new WebSMSException(e.getMessage());
		}
	}

}
