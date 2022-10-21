/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package net.sf.jftp.system;

import net.sf.jftp.event.Event;
import net.sf.jftp.event.EventCollector;
import net.sf.jftp.event.EventHandler;
import net.sf.jftp.event.EventProcessor;
import net.sf.jftp.event.FtpEvent;
import net.sf.jftp.event.FtpEventConstants;
import net.sf.jftp.event.FtpEventHandler;
import net.sf.jftp.system.logging.Log;
import net.sf.jftp.system.logging.Log4JLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


public class CommandLine implements Runnable, EventHandler, FtpEventConstants {
	private final EventCollector eventCollector;

	private CommandLine() {
		super();
		Log.setLogger(new Log4JLogger());
		Log.out("Testing a getEnableDebug dependent log message");
		Log.debug("Testing a getDisableLog dependent log message");

		this.eventCollector = new EventCollector();
		EventProcessor.addHandler(net.sf.jftp.event.FtpEventConstants.FTPCommand, new FtpEventHandler());
		EventProcessor.addHandler(net.sf.jftp.event.FtpEventConstants.FTPPrompt, this);
		new Thread(this).start();
	}

	public static void main(String[] argv) {
		CommandLine ftp = new CommandLine();
	}

	public boolean handle(Event e) {
		System.out.print("ftp> ");

		return true;
	}

	public void run() {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		String line = null;

		do {
			try {
				this.eventCollector.accept(new FtpEvent(net.sf.jftp.event.FtpEventConstants.FTPPrompt));
				line = in.readLine();
				this.eventCollector.accept(new FtpEvent(net.sf.jftp.event.FtpEventConstants.FTPCommand, line));
			} catch (IOException e) {
				Log.debug("CommandLine exception on run");
			}
		} while (!line.toLowerCase().startsWith("quit"));

		this.eventCollector.accept(new FtpEvent(net.sf.jftp.event.FtpEventConstants.FTPShutdown)); // make the quit command spawn this event?
	}
}
