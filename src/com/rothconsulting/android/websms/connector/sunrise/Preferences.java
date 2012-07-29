/*
 * Copyright (C) 2010 Felix Bechstein
 * 
 * This file is part of WebSMS.
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

/**
 * Preferences.
 * 
 * @author flx
 */
public final class Preferences extends PreferenceActivity {
	/** Preference's username. */
	static final String PREFS_USER = "sunrise_user";
	/** Preference's user's password. */
	static final String PREFS_PASSWORD = "sunrise_password";
	/** Preference's sunrise hostname id. */
	static final String PREFS_SUNRISE_HOST = "sunrise_host";
	/** Preference's enabled. */
	static final String PREFS_ENABLED = "enable_sunrise";
	/** Preference's defined sender in case the account has multiple numbers. */
	static final String PREFS_DEFINED_SENDER_NUMBER = "sunrise_defined_sender_number";

	/** User. */
	private static String user;
	/** Password. */
	private static String pw;

	/** Need to bootstrap? */
	private static boolean needBootstrap = false;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.addPreferencesFromResource(R.xml.connector_sunrise_prefs);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onPause() {
		super.onPause();
		// check if prefs changed
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(this);
		needBootstrap |= user != null
				&& !user.equals(p.getString(PREFS_USER, ""));
		needBootstrap |= pw != null
				&& !pw.equals(p.getString(PREFS_PASSWORD, ""));
		user = p.getString(PREFS_USER, "");
		pw = p.getString(PREFS_PASSWORD, "");
	}

	/**
	 * @param context
	 *            {@link Context}
	 * @return true if bootstrap is needed
	 */
	static boolean needBootstrap(final Context context) {
		if (needBootstrap) {
			return true;
		}
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);

		return p.getString(PREFS_USER, "").length() == 0;
	}
}
