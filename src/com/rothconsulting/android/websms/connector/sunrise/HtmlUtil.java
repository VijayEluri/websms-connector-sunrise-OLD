package com.rothconsulting.android.websms.connector.sunrise;


public class HtmlUtil {

	private static final String TAG = "HtmlUtil";

	public static String getHtmlString(final String htmlText,
			final String startString, final int startOffset1,
			final int startOffset2, final String endString, final int endOffset) {

		String resultString = "";

		int startIndex = htmlText.indexOf(startString);
		log("indexOf " + startString + "=" + startIndex);
		if (startIndex > 0) {
			resultString = htmlText.substring(startIndex + startOffset1,
					startIndex + startOffset2);
			log("resultString= " + resultString);
		}
		log("indexOf " + startString + "=" + startIndex);

		if (endString != null) {
			int endIndex = resultString.indexOf(endString);
			log("indexOf " + endString + "=" + endIndex);
			if (endIndex > 0) {
				resultString = resultString.substring(0, endIndex + endOffset);
			}
		}
		log("**** Token=" + resultString);

		return resultString;
	}

	/**
	 * central logger
	 * 
	 * @param message
	 */
	private static void log(final String message) {
		// Log.d(TAG, message);
	}

}
