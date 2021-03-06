/*
Copyright 2009 David Revell

This file is part of PrivilegeFTP.

PrivilegeFTP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

PrivilegeFTP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with PrivilegeFTP.  If not, see <http://www.gnu.org/licenses/>.
*/

package privilege.bluepremium.PrivilegeFTP;

import java.io.File;
import java.io.IOException;

import android.util.Log;

public class CmdCWD extends FtpCmd implements Runnable {
	protected String input;
	
	public CmdCWD(SessionThread sessionThread, String input) {
		super(sessionThread, CmdCWD.class.toString());
		this.input = input;
	}
	
	public void run() {
		myLog.l(Log.DEBUG, "CWD executing");
		String param = getParameter(input);
		File newDir;
		String errString = null;
		mainblock: {
			newDir = inputPathToChrootedFile(sessionThread.getWorkingDir(), param);

			// Ensure the new path does not violate the chroot restriction
			if(violatesChroot(newDir)) {
				errString = "Invalid Name or Chroot Violation\r\n";
				sessionThread.writeString(errString);
				myLog.l(Log.INFO, errString);
				break mainblock;
			}

			try {
				newDir = newDir.getCanonicalFile();
				if(!newDir.isDirectory()) {
					sessionThread.writeString("550 Can't CWD to invalid directory\r\n");
				} else if(newDir.canRead()) {
					sessionThread.setWorkingDir(newDir);
					sessionThread.writeString("250 CWD successful\r\n");
				} else {
					sessionThread.writeString("550 That path is inaccessible\r\n");
				}
			} catch(IOException e) {
				sessionThread.writeString("550 Invalid path\r\n");
				break mainblock;
			}
		}
		myLog.l(Log.DEBUG, "CWD complete");
	}
}
