package com.rothconsulting.android.websms.connector.sunrise;

import android.util.Log;

public class HtmlUtil {

	private static final String TAG = "HtmlUtil";

	public static String getHtmlString(final String htmlText,
			final String startString, final int startOffset1,
			final int startOffset2, final String endString, final int endOffset) {

		String resultString = "";

		int startIndex = htmlText.indexOf(startString);
		Log.d(TAG, "indexOf " + startString + "=" + startIndex);
		if (startIndex > 0) {
			resultString = htmlText.substring(startIndex + startOffset1,
					startIndex + startOffset2);
			Log.d(TAG, "resultString= " + resultString);
		}
		Log.d(TAG, "indexOf " + startString + "=" + startIndex);

		if (endString != null) {
			int endIndex = resultString.indexOf(endString);
			Log.d(TAG, "indexOf " + endString + "=" + endIndex);
			if (endIndex > 0) {
				resultString = resultString.substring(0, endIndex + endOffset);
			}
		}
		Log.d(TAG, "**** Token=" + resultString);

		return resultString;
	}
}
