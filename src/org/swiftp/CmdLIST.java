package org.swiftp;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.util.Log;

public class CmdLIST extends FtpCmd implements Runnable {
	//public static final String message = "LIST";
	protected static MyLog staticLog = new MyLog("LIST_static");
	protected String input;
	// The approximate number of milliseconds in 6 months
	public final static long MS_IN_SIX_MONTHS = 6 * 30 * 24 * 60 * 60 * 1000; 
	
	public CmdLIST(SessionThread sessionThread, String input) {
		super(sessionThread, "LIST");
		this.input = input; 
	}
	
	public void run() {
		myLog.l(Log.INFO, "LIST executing");

		String param = getParameter(input);
		File fileToList = null;
		if(param.length() > 0) {
			// the user specified "LIST <argument>"
			if(param.charAt(0) == '/') {
				// The LIST parameter is an absolute path
				fileToList = new File(param);
			} else {
				// The LIST parameter is a relative path,
				// so append it to the existing path prefix
				fileToList = new File(sessionThread.getPrefix(), param);
			}
		} else {
			// The user did not give a parameter to LIST. So we just
			// use the current directory for the session.
			fileToList = sessionThread.getPrefix();
		}
		// Normalize the path representation
		try {
			fileToList = fileToList.getCanonicalFile();
		} catch (IOException e) {
			myLog.l(Log.INFO, "Error getting canonical path");
			sessionThread.writeString("451 Path problem\r\n");
			return;
		}
		myLog.l(Log.DEBUG, "Listing name: " + fileToList.toString());
		StringBuilder response = new StringBuilder();
		
		if(fileToList.isDirectory()) {
			myLog.l(Log.DEBUG, "Listing directory");
			// Get a listing of all files and directories in the path
			File[] entries = fileToList.listFiles();
			myLog.l(Log.DEBUG, "Dir len " + entries.length);
			for(File entry : entries) {
				myLog.l(Log.DEBUG, "Handling dentry");
				response.append(makeLsString(entry));
				response.append("\r\n");
			}
		} else {
			myLog.l(Log.DEBUG, "Listing file");
			// The given path is a file and not a directory
			response.append(makeLsString(fileToList));
			response.append("\r\n");
		}
		
		String feedback;
		sessionThread.writeString("150-Beginning transmission\r\n");
		switch(sessionThread.sendViaDataSocket(response.toString())) {
		case 0:
			feedback = "425 Couldn't establish connection\r\n";
			break;
		case 1:
			feedback = "426 Connection broken\r\n";
			break;
		case 2:
			feedback = "226 Listing transmitted OK\r\n";
			break;
		default:
			feedback = "425 Unknown transmission error\r\n";
			break;
		}

		sessionThread.writeString(feedback);
		myLog.l(Log.INFO, "LIST complete");
	}
	
	private static String makeLsString(File file) {
		StringBuilder response = new StringBuilder();
		
		if(!file.exists()) {
			staticLog.l(Log.ERROR, "makeLsString had nonexistent file");
			return null;
		}

		// See Daniel Bernstein's explanation of /bin/ls format at:
		// http://cr.yp.to/ftp/list/binls.html
		// This stuff is almost entirely based on his recommendations.
		
		String lastNamePart = file.getName();
		// Many clients can't handle files containing these symbols
		if(lastNamePart.contains("*") || 
		   lastNamePart.contains("/") ||
		   lastNamePart.contains("-"))
		{
			staticLog.l(Log.INFO, "Filename omitted due to disallowed character");
			return null;
		}
				
		
		if(file.isDirectory()) {
			response.append("drwxr-xr-x 1");
		} else {
			// todo: think about special files, symlinks, devices
			response.append("-rw-r--r-- 1 owner group");
		}
		
		// The next field is a 13-byte right-justified space-padded file size
		long fileSize = file.length();
		String sizeString = new Long(fileSize).toString();
		int padSpaces = 13 - sizeString.length();
		while(padSpaces-- > 0) {
			response.append(' ');
		}
		response.append(sizeString);
		
		// The format of the timestamp varies depending on whether the mtime
		// is 6 months old
		long mTime = file.lastModified();
		SimpleDateFormat format;
		if(System.currentTimeMillis() - mTime > MS_IN_SIX_MONTHS) {
			// The mtime is less than 6 months ago
			format = new SimpleDateFormat(" MMM dd HH:mm ");
		} else {
			// The mtime is more than 6 months ago
			format = new SimpleDateFormat(" MMM dd  yyyy ");
		}
		response.append(format.format(new Date(file.lastModified())));
		response.append(lastNamePart);
		return response.toString();
	}

}