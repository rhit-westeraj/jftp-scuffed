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
package model.net;

import view.JFtp;
import controller.config.LoadSet;
import controller.config.SaveSet;
import controller.config.Settings;
import model.system.StringUtils;
import model.system.logging.Log;
import controller.util.I18nHelper;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;


/**
 * All control transactions are made here.
 * See doc/FtpDownload.java for examples and note
 * that this class is event-driven and not Exception-based!
 * You might want to take a look at ConnectionListener, too.
 *
 * @author David Hansmann, hansmann.d@debitel.net
 */
public class FtpConnection implements BasicConnection, FtpConstants {
	public static final String ASCII = "A";
	public static final String BINARY = "I";
	public static final String EBCDIC = "E";
	public static final String L8 = "L 8";
	private static final String STREAM = "S";
	private static final String BLOCKED = "B";
	private static final String COMPRESSED = "C";
	private static final boolean TESTMODE = false;
	// everything starting with this will be treated
	// as a negative response
	private static final String NEGATIVE = "5";
	private static final String NEGATIVE2 = "4";
	// everything starting with this will be treated
	// as a positive response
	private static final String POSITIVE = "2";
	// for resuming
	private static final String PROCEED = "3"; //"350";
	private static final char MORE_LINES_APPENDED = '-';
	//*** 1.44: if LIST is not set, then load it from advanced settings
	//***       And if the file for it is not found, then create it
	//***       with LIST -laL as the default
	public static String LIST_DEFAULT = "LIST -laL";
	public static String LIST = "LIST";

	private static boolean useStream = true;
	private static boolean useBlocked = true;
	private static boolean useCompressed = true;

	// active ftp ports
	private static int porta = 5; // 5 * 256 + 1 = 1281
	private static int portb = 1;
	public final List<String> currentListing = new ArrayList<>();
	private final List<String> currentFiles = new ArrayList<>();
	private final List<String> currentSizes = new ArrayList<>();
	private final List<String> currentPerms = new ArrayList<>();
	private final String[] loginAck = {FTP331_USER_OK_NEED_PASSWORD, FTP230_LOGGED_IN};
	private final List<FtpTransfer> transfers = new ArrayList<>();
	/**
	 * Used to determine the type of transfer.
	 */
	public boolean hasUploaded;
	public boolean work = true;
	/**
	 * May contain date listing
	 */
	public List<LocalDateTime> dateVector = new ArrayList<>();
	private boolean ok = true;
	private String pwd = "";
	private String initCWD = Settings.defaultDir;
	private String osType = "";
	private String dataType;
	private boolean modeStreamSet;
	private DataConnection dcon;
	private List<ConnectionListener> listeners = new java.util.ArrayList<>();
	private ConnectionHandler handler = new ConnectionHandler();
	private FtpKeepAliveThread keepAliveThread;
	// directory downloaded to
	private String localPath;
	/**
	 * the host
	 */
	private String host = "";
	/**
	 * the user
	 */
	private String username;
	/**
	 * the password
	 */
	private String password;
	/**
	 * the port
	 */
	private int port = 21;
	/**
	 * the InputStream of the control connection
	 */
	private BufferedReader in;
	/**
	 * the OutputStream of the control connection
	 */
	private JConnection jcon;
	/**
	 * we are disconnected
	 */
	private boolean connected;
	private boolean shortProgress;
	private boolean isDirUpload;
	private String baseFile;
	private int fileCount;
	private String typeNow = "";
	private String crlf;

	/**
	 * Create an instance with a given host
	 *
	 * @param host The host to connect to
	 */
	public FtpConnection(String host) {
		super();
		this.host = host;

		File f = new File(".");
		this.setLocalPath(f.getAbsolutePath());
	}

	/**
	 * Create an instance with a given host, port and initial remote working directory
	 *
	 * @param host    The host to connect to
	 * @param port    The port used for the control connection, usually 21
	 * @param initCWD The initial remote working directory
	 */
	public FtpConnection(String host, int port, String initCWD) {
		super();
		this.host = host;
		this.port = port;
		this.initCWD = initCWD;

		File f = new File(".");
		this.setLocalPath(f.getAbsolutePath());
	}

	/**
	 * Connect to the server an log in.
	 * <p>
	 * Does also fire directory update and connection initialized event if successful or
	 * connection failed event if failed.
	 *
	 * @param initCWD The initial remote working directory
	 * @param crlfx   \n, \r, \r\n, CR, LF or CRLF - line break style of the server
	 */
	public FtpConnection(String host, int port, String initCWD, String crlfx) {
		super();
		this.host = host;
		this.port = port;
		this.initCWD = initCWD;

		if (null != crlfx) {
			if (crlfx.equals("\n") || crlfx.equals("\r") || crlfx.equals("\r\n")) this.crlf = crlfx;
			crlfx = crlfx.trim().toUpperCase();
			switch (crlfx) {
				case "LF":
					this.crlf = "\n";
					break;
				case "CR":
					this.crlf = "\r";
					break;
				case "CRLF":
					this.crlf = "\r\n";
					break;
			}
		}
		//Log.out("\n\nCRLF:---"+crlf+"---\n\n");

		File f = new File(".");
		this.setLocalPath(f.getAbsolutePath());
	}

	/**
	 * Connect to the server an log in.
	 * <p>
	 * Does also fire directory update and connection initialized event if successful or
	 * connection failed event if failed.
	 *
	 * @param username The username
	 * @param password The password
	 * @return WRONG_LOGIN_DATA, OFFLINE, GENERIC_FAILED or LOGIN_OK status code
	 */
	public int login(String username, String password) {
		this.username = username;
		this.password = password;

		int status = LOGIN_OK;

		final boolean msg = true;
		if (msg) {
			Log.debug(MessageFormat.format(I18nHelper.getLogString("connecting.to.0"), this.host));
		}

		this.jcon = new JConnection(this.host, this.port);

		if (this.jcon.isThere()) {
			this.in = this.jcon.getReader();

			if (null == this.getLine(POSITIVE))//FTP220_SERVICE_READY) == null)
			{
				this.ok = false;
				Log.debug(I18nHelper.getLogString("server.closed.connection.maybe.too.many.users"));
				status = OFFLINE;
			}

			if (msg) {
				Log.debug(I18nHelper.getLogString("connection.established"));
			}

			this.jcon.send(FtpCommand.USER.name() + " " + username);

			if (!this.getLine(this.loginAck).startsWith(POSITIVE))//FTP230_LOGGED_IN))
			{
				this.jcon.send(FtpCommand.PASS.name() + " " + password);

				if (this.success(POSITIVE))//FTP230_LOGGED_IN))
				{
					if (msg) {
						Log.debug(I18nHelper.getLogString("logged.in"));
					}
				} else {
					Log.debug(I18nHelper.getLogString("wrong.password"));
					this.ok = false;
					status = WRONG_LOGIN_DATA;
				}
			}

			// end if(jcon)...
		} else {
			if (msg) {
				Log.debug(I18nHelper.getLogString("ftp.not.available"));
				this.ok = false;
				status = GENERIC_FAILED;
			}
		}

		if (this.ok) {
			this.connected = true;
			this.system();

			this.binary();

			if (this.initCWD.trim().equals(Settings.defaultDir) || !this.chdirNoRefresh(this.initCWD)) {

				this.updatePWD();
			}

			if (Settings.ftpKeepAlive) {
				this.keepAliveThread = new FtpKeepAliveThread(this);
			}

			String[] advSettings = new String[6];

			if (this.getOsType().contains(I18nHelper.getLogString("os.2"))) {
				LIST_DEFAULT = "LIST";
			}

			if (LIST.equals("default")) {
				advSettings = LoadSet.loadSet(Settings.adv_settings);

				if (null == advSettings) {
					LIST = LIST_DEFAULT;

					new SaveSet(Settings.adv_settings, LIST);
				} else {
					LIST = advSettings[0];

					if (null == LIST) {
						LIST = LIST_DEFAULT;
					}
				}
			}

			if (this.getOsType().contains("MVS")) {
				LIST = "LIST";
			}

			//***
			this.fireDirectoryUpdate(this);
			this.fireConnectionInitialized(this);
		} else {
			this.fireConnectionFailed(this, Integer.toString(status));
		}

		return status;
	}

	public FtpKeepAliveThread getKeepAliveThread() {
		return this.keepAliveThread;
	}

	/**
	 * Sort the filesizes an return an array.
	 * <p>
	 * The Array should be in sync with the other sort*-Methods
	 *
	 * @return An String-array of sizes containing the size or -1 if failed or 0 for directories
	 */
	public String[] sortSize() {
		try {
			this.currentSizes.clear();

			for (String tmp : this.currentListing) {
				// ------------- VMS override -------------------
				if (this.getOsType().contains("VMS")) {
					if (!tmp.contains(";")) {
						continue;
					}

					this.currentSizes.add("-1");

					continue;
				}

				// ------------- MVS override -------------------
				if (this.getOsType().contains("MVS")) {
					if (tmp.startsWith("Volume") || (!tmp.contains(" "))) {
						continue;
					}

					this.currentSizes.add("-1");

				}

				// ------------------------------------------------
				else if (this.getOsType().contains("WINDOW")) {
					java.util.StringTokenizer to = new java.util.StringTokenizer(tmp, " ", false);

					if (4 <= to.countTokens()) {
						to.nextToken();
						to.nextToken();

						String size = to.nextToken();

						if (size.equals("<DIR>")) {
							this.currentSizes.add("0");
						} else {
							try {
								Long.parseLong(size);
								this.currentSizes.add(size);
							} catch (Exception ex) {
								this.currentSizes.add("-1");
								Log.out(MessageFormat.format(I18nHelper.getLogString("warning.filesize.can.not.be.determined.value.is.0"), size));
							}
						}
					}
				}

				// ------------------------------------------------
				else if (this.getOsType().contains(I18nHelper.getLogString("os.2"))) {
					java.util.StringTokenizer to = new java.util.StringTokenizer(tmp, " ", false);
					tmp = this.giveSize(to, 0);
					Log.out(MessageFormat.format(I18nHelper.getLogString("os.2.parser.size.0"), tmp));
					this.currentSizes.add(tmp);
				} else {
					//Log.out("\n\nparser\n\n");
					java.util.StringTokenizer to = new java.util.StringTokenizer(tmp, " ", false);

					if (8 <= to.countTokens()) // unix
					{
						tmp = this.giveSize(to, 4);
						this.currentSizes.add(tmp);

						//Log.out(tmp);
					} else if (7 == to.countTokens()) // unix, too
					{
						Log.out(I18nHelper.getLogString("warning.7.token.backup.size.parser.activated"));
						tmp = this.giveSize(to, 4);
						this.currentSizes.add(tmp);
					} else {
						Log.debug(MessageFormat.format(I18nHelper.getLogString("cannot.parse.line.0"), tmp));
					}
				}
			}
		} catch (Exception ex) {
			Log.debug(MessageFormat.format(I18nHelper.getLogString("0.ftpconnection.sortsize.1"), ex));
			return new String[0];
		}

		return this.toArray(this.currentSizes);
	}

	private String[] toArray(List<String> in) {
		String[] ret = new String[in.size()];
		return in.toArray(ret);
	}

	/**
	 * Sort the permissions an return an array.
	 * <p>
	 * The Array should be in sync with the other sort*-Methods
	 *
	 * @return An int-array of sizes containing W, R or DENIED for each file
	 */
	public int[] getPermissions() {
		try {
			this.currentPerms.clear();

			for (String tmp : this.currentListing) {
				// ------------- VMS override -------------------
				if (this.getOsType().contains("VMS")) {
					if (!tmp.contains(";")) {
						continue;
					}

					this.currentPerms.add("r");

					continue;
				}

				// ------------- MVS override -------------------
				if (this.getOsType().contains("MVS")) {
					if (tmp.startsWith("Volume")) {
						continue;
					}

					this.currentPerms.add("r");

					continue;
				}

				// ------------------------------------------------

				java.util.StringTokenizer to = new java.util.StringTokenizer(tmp.trim(), " ", false);

				if (!(3 < to.countTokens()) || (tmp.startsWith("/") && (0 < tmp.indexOf("denied")))) {
					continue;
				}

				tmp = to.nextToken();

				if (10 != tmp.length()) {
					//System.out.println(tmp + " - e");
					return null; // exotic bug, hardlinks are not found or something ("/bin/ls: dir: no such file or directy" in ls output) - we have no permissions then
				}

				char ur = tmp.charAt(1);
				char uw = tmp.charAt(2);
				char ar = tmp.charAt(7);
				char aw = tmp.charAt(8);

				to.nextToken();

				String user = to.nextToken();

				//System.out.println(""+ur+":"+ar+":"+tmp);
				if ('w' == aw) {
					this.currentPerms.add("w");
				} else if (user.equals(this.username) && ('w' == uw)) {
					this.currentPerms.add("w");
				} else if ('r' == ar) {
					this.currentPerms.add("r");
				} else if (user.equals(this.username) && ('r' == ur)) {
					this.currentPerms.add("r");
				} else {
					//System.out.println(ur+":"+ar+":"+user+":"+username+":");
					this.currentPerms.add("n");
				}
			}
		} catch (Exception ex) {
			Log.debug(MessageFormat.format(I18nHelper.getLogString("0.ftpconnection.getpermissions.1"), ex));
		}

		int[] ret = new int[this.currentPerms.size()];

		for (int i = 0; i < this.currentPerms.size(); i++) {
			String fx = this.currentPerms.get(i);

			if (fx.equals("w")) {
				ret[i] = W;
			} else if (fx.equals("n")) {
				ret[i] = DENIED;
			} else {
				ret[i] = R;
			}
			//System.out.println(ret[i]);
		}

		return ret;
	}

	/**
	 * Sort the filenames of the current working dir an return an array.
	 * <p>
	 * The Array should be in sync with the other sort*-Methods
	 *
	 * @return An String-array of sizes containing the name of the file (symlinks are resolved)
	 */
	public String[] sortLs() {
		try {
			boolean newUnixDateStyle = false;
			this.dateVector = new java.util.ArrayList<>();
			this.currentFiles.clear();

			for (String tmp : this.currentListing) {
				// ------------------- VMS override --------------------
				if (this.getOsType().contains("VMS")) {
					int x = tmp.indexOf(';');

					if (0 > x) {
						continue;
					}

					tmp = tmp.substring(0, x);

					if (tmp.endsWith("DIR")) {
						this.currentFiles.add(tmp.substring(0, tmp.lastIndexOf('.')) + "/");
					} else {
						this.currentFiles.add(tmp);
					}

					//Log.out("listing - (vms parser): " + tmp);

					continue;
				} else if (this.getOsType().contains(I18nHelper.getLogString("os.2"))) {
					java.util.StringTokenizer to2 = new java.util.StringTokenizer(tmp, " ", true);

					if (this.giveFile(to2, 2).contains("DIR")) {
						to2 = new java.util.StringTokenizer(tmp, " ", true);
						tmp = this.giveFile(to2, 5);
						tmp = tmp + "/";
					} else {
						to2 = new java.util.StringTokenizer(tmp, " ", true);

						if (this.giveFile(to2, 1).contains("DIR")) {
							//Log.out("OS/2 parser (DIRFIX): " + tmp);
							to2 = new java.util.StringTokenizer(tmp, " ", true);
							tmp = this.giveFile(to2, 4);
							tmp = tmp + "/";
						} else {
							to2 = new java.util.StringTokenizer(tmp, " ", true);
							tmp = this.giveFile(to2, 4);
						}
					}

					Log.out(MessageFormat.format(I18nHelper.getLogString("os.2.parser.02"), tmp));
					this.currentFiles.add(tmp);

					continue;
				}

				// -------------------------------------------------------
				// ------------------- MVS override --------------------
				if (this.getOsType().contains("MVS")) {
					if (tmp.startsWith("Volume") || (!tmp.contains(" "))) {
						Log.out("->" + tmp);

						continue;
					}

					String f = tmp.substring(tmp.lastIndexOf(' ')).trim();
					String isDir = tmp.substring(tmp.lastIndexOf(' ') - 5, tmp.lastIndexOf(' '));

					if (isDir.contains("PO")) {
						this.currentFiles.add(f + "/");
					} else {
						this.currentFiles.add(f);
					}

					Log.out(MessageFormat.format(I18nHelper.getLogString("listing.mvs.parser.0.1"), tmp, f));
					continue;
				} else if (this.getOsType().contains(I18nHelper.getLogString("os.2"))) {
					java.util.StringTokenizer to2 = new java.util.StringTokenizer(tmp, " ", true);

					if (this.giveFile(to2, 2).contains("DIR")) {
						to2 = new java.util.StringTokenizer(tmp, " ", true);
						tmp = this.giveFile(to2, 5);
						tmp = tmp + "/";
					} else {
						to2 = new java.util.StringTokenizer(tmp, " ", true);

						if (this.giveFile(to2, 1).contains("DIR")) {
							//Log.out("OS/2 parser (DIRFIX): " + tmp);
							to2 = new java.util.StringTokenizer(tmp, " ", true);
							tmp = this.giveFile(to2, 4);
							tmp = tmp + "/";
						} else {
							to2 = new java.util.StringTokenizer(tmp, " ", true);
							tmp = this.giveFile(to2, 4);
						}
					}

					Log.out(MessageFormat.format(I18nHelper.getLogString("os.2.parser.0"), tmp));
					this.currentFiles.add(tmp);

					continue;
				}

				// -------------------------------------------------------
				// ------------------- Unix, Windows and others -----
				boolean isDir = false;
				boolean isLink = false;

				if (tmp.startsWith("d") || (tmp.contains("<DIR>"))) {
					isDir = true;
				}

				if (tmp.startsWith("l")) {
					isLink = true;
				}

				if (tmp.startsWith("/") && (0 < tmp.indexOf("denied"))) {
					continue;
				}

				java.util.StringTokenizer to = new java.util.StringTokenizer(tmp, " ", false);
				java.util.StringTokenizer to2 = new java.util.StringTokenizer(tmp, " ", true);

				int tokens = to.countTokens();

				//Log.out("listing: tokens: " + tokens + " - " + tmp);
				boolean set = false;

				//-----preparsing-----
				int lcount = 0;

				if (!newUnixDateStyle) {
					try {
						java.util.StringTokenizer tx = new java.util.StringTokenizer(tmp, " ", false);
						tx.nextToken();
						tx.nextToken();
						tx.nextToken();
						tx.nextToken();
						tx.nextToken();

						String date = tx.nextToken();
						int x = date.indexOf('-');
						int y = date.lastIndexOf('-');

						if (y > x) {
							Log.debug(I18nHelper.getLogString("using.new.unix.date.parser"));
							newUnixDateStyle = true;
						}
					} catch (Exception ex) {
						Log.out(MessageFormat.format(I18nHelper.getLogString("could.not.preparse.line.0"), tmp));
						lcount++;

					}
				}

				if (newUnixDateStyle) // unix, too
				{
					set = true;

					try {
						java.util.StringTokenizer to3 = new java.util.StringTokenizer(tmp, " ", true);
						String date = this.giveFile(to3, 5);
						String date2 = date.substring(date.indexOf('-') + 1);
						date2 = date2.substring(date.indexOf('-') + 2);

						int year = Integer.parseInt(date.substring(0, date.indexOf('-')));
						int month = Integer.parseInt(date.substring(date.indexOf('-') + 1, date.indexOf('-') + 3)) - 1;
						String m1 = date.substring(date.indexOf('-') + 1);
						int day = Integer.parseInt(m1.substring(m1.indexOf('-') + 1, m1.indexOf('-') + 3));
						int hour = Integer.parseInt(date2.substring(0, date2.indexOf(':')));
						int min = Integer.parseInt(date2.substring(date2.indexOf(':') + 1, date2.indexOf(':') + 3));

						//Log.out("day:"+day+"year:"+y+"mon:"+m+"hour:"+h+"m:"+min);
						LocalDateTime dateTime = LocalDateTime.of(year, month, day, hour, min);

						this.dateVector.add(dateTime);

						//Log.out("+++ date: \"" + d.toString() + "\"");
					} catch (Exception ex) {
						ex.printStackTrace();

						//set = false;
					}

					// -----------------------------
					tmp = this.giveFile(to2, 7);

					if (1 < lcount) {
						this.dateVector = new ArrayList<>();
					}
				} else if (8 < tokens) // unix
				{
					set = true;
					tmp = this.giveFile(to2, 8);

					//Log.out("listing - (unix parser, token 8): " + tmp);
				} else if (3 < tokens) // windows
				{
					set = true;
					tmp = this.giveFile(to2, 3);

					if (tmp.startsWith("<error>")) {
						tmp = this.giveFile(to2, 4);

						//Log.out("listing - (windows parser, token 4): " + tmp);
					} else {
						//Log.out("listing - (windows parser, token 3): " + tmp);
					}
				}

				if (tmp.startsWith("<error>") || !set) {
					if (3 > tokens) {
						continue;
					}

					tmp = this.giveFile(to2, tokens);
					Log.out(I18nHelper.getLogString("listing.warning.listing.style.unknown.using.last.token.as.filename"));
					Log.out(I18nHelper.getLogString("listing.this.may.work.but.may.also.fail.filesizes.and.permissions.will.probably.fail.too"));
					Log.out(I18nHelper.getLogString("listing.please.send.us.a.feature.request.with.the.serveraddress.to.let.us.support.this.listing.style"));
					Log.out(MessageFormat.format(I18nHelper.getLogString("listing.backup.parser.token.0.1"), tokens, tmp));
				}

				if (isDir) {
					tmp = tmp + "/";
				} else if (isLink) {
					tmp = tmp + "###";
				}

				this.currentFiles.add(tmp);
				// -------------------------------------------------------------
			}

			String[] files = this.toArray(this.currentFiles);

			for (int i = 0; i < files.length; i++) {
				files[i] = this.parseSymlink(files[i]);

			}

			return files;
		} catch (Exception ex) {
			Log.debug(MessageFormat.format(I18nHelper.getLogString("0.ftpconnection.sortls"), ex));
			ex.printStackTrace();
		}

		return new String[0];
	}

	private String giveFile(StringTokenizer to, int lev) {

		for (int i = 0; i < lev; i++) {
			if (to.hasMoreTokens()) {
				String t = to.nextToken();

				if (t.equals(" ") || t.equals("\t")) {

					i--;
				}
			}
		}

		StringBuilder tmp = new StringBuilder("<error>");

		while (tmp.toString().equals(" ") || tmp.toString().equals("\t") || tmp.toString().equals("<error>")) {
			if (to.hasMoreTokens()) {
				tmp = new StringBuilder(to.nextToken());
			} else {
				break;
			}
		}

		while (to.hasMoreTokens()) {
			tmp.append(to.nextToken());
		}

		//Log.out(">>> "+tmp);
		return tmp.toString();
	}

	/**
	 * get a filesize
	 */
	private String giveSize(StringTokenizer to, int lev) {
		for (int i = 0; i < lev; i++) {
			if (to.hasMoreTokens()) {
				if (to.nextToken().equals(" ")) {
					i--;
				}
			}
		}

		while (to.hasMoreTokens()) {
			String tmp = to.nextToken();

			if (!tmp.equals(" ")) {
				try {
					Integer.parseInt(tmp);
				} catch (NumberFormatException ex) {
					return "-1";
				}

				return tmp;
			}
		}

		return "-2";
	}

	/**
	 * Try to determine the os-type.
	 * (Used internally to check how to parse the ls-output)
	 *
	 * @return A Part of the SYST comman server response
	 */
	private String getOsType() {
		if (TESTMODE) {
			return "MVS";
		} else {
			return this.osType;
		}
	}

	private void setOsType(String os) {
		this.osType = os.toUpperCase();
	}

	private int getPasvPort(String str) {
		int start = str.lastIndexOf(',');
		StringBuilder lo = new StringBuilder();
		start++;

		while (start < str.length()) {
			char c = str.charAt(start);

			if (!Character.isDigit(c)) {
				break;
			}

			lo.append(c);
			start++;
		}

		System.out.println("\n\n\n" + str);

		StringBuilder hi = new StringBuilder();
		start = str.lastIndexOf(',');
		start--;

		while (true) {
			char c = str.charAt(start);

			if (!Character.isDigit(c)) {
				break;
			}

			hi.insert(0, c);
			start--;
		}

		//System.out.print(hi+":"+lo+" - ");
		return ((Integer.parseInt(hi.toString()) * 256) + Integer.parseInt(lo.toString()));
	}

	private int getActivePort() {
		return (porta * 256) + portb;
	}

	/**
	 * parse a symlink
	 */
	private String parseSymlink(String file) {
		if (file.contains("->")) {
			//System.out.print(file+" :-> symlink converted to:");
			file = file.substring(0, file.indexOf("->")).trim();
			file = file + "###";

			//System.out.println(file);
		}

		return file;
	}

	/**
	 * parse a symlink and cut the back
	 */
	private String parseSymlinkBack(String file) {
		if (file.contains("->")) {
			//System.out.print(file+" :-> symlink converted to:");
			file = file.substring(file.indexOf("->") + 2).trim();

			//System.out.println(file);
		}

		return file;
	}

	/**
	 * test for symlink
	 */
	private boolean isSymlink(String file) {
		return file.contains("->");
	}

	/**
	 * Download a file or directory.
	 * Uses multithreading if enabled and does not block.
	 *
	 * @param file The file to download
	 * @return An int-statuscode, see constants defined in this class for details
	 */
	public int handleDownload(String file) {
		if (Settings.getEnableMultiThreading()) {
			Log.out(I18nHelper.getLogString("spawning.new.thread.for.this.download"));

			FtpTransfer t = new FtpTransfer(this.host, this.port, this.localPath, this.pwd, file, this.username, this.password, Transfer.DOWNLOAD, this.handler, this.listeners, this.crlf);
			this.transfers.add(t);

			return NEW_TRANSFER_SPAWNED;
		} else {
			Log.out(I18nHelper.getLogString("multithreading.is.completely.disabled"));

			return this.download(file);
		}
	}

	/**
	 * Download a file or directory, block until finished.
	 *
	 * @param file The file to download
	 * @return An int returncode
	 */
	public int download(String file) {
		//Log.out("ftp download started:" + this);

		int stat;

		if (file.endsWith("/")) {
			this.shortProgress = true;
			this.fileCount = 0;
			this.baseFile = file;
			this.dataType = DataConnection.GETDIR;

			stat = this.downloadDir(file);

			//pause(100);
			this.fireActionFinished(this);
			this.fireProgressUpdate(this.baseFile, DataConnection.DFINISHED + ":" + this.fileCount, -1);
			this.shortProgress = false;
		} else {
			this.dataType = DataConnection.GET;

			stat = this.rawDownload(file);

			if (Settings.enableFtpDelays) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					Log.debug(I18nHelper.getLogString("ftpconnection.interruptedexception.in.thread.sleep.in.download"));
				} catch (RuntimeException ex) {
					Log.debug(I18nHelper.getLogString("ftpconnection.runtimeexception.in.thread.sleep.in.download"));
				}
			}

			this.fireActionFinished(this);
		}

		if (Settings.enableFtpDelays) {
			try {
				Thread.sleep(400);
			} catch (InterruptedException e) {
				Log.debug(I18nHelper.getLogString("ftpconnection.interruptedexception.in.thread.sleep.in.download"));
			} catch (RuntimeException ex) {
				Log.debug(I18nHelper.getLogString("ftpconnection.runtimeexception.in.thread.sleep.in.download"));
			}
		}

		return stat;
	}

	/**
	 * Get download InputStream.
	 *
	 * @param file The file to download
	 * @return An InputStream
	 */
	public InputStream getDownloadInputStream(String file) {
		Log.out(MessageFormat.format(I18nHelper.getLogString("ftp.stream.download.started.0"), this));
		file = this.parse(file);

		try {
			int p = 0;
			this.dataType = DataConnection.GET;
			file = StringUtils.getFile(file);

			String path = this.localPath + file;

			//BufferedReader in = jcon.getReader();

			this.modeStream();
			p = this.negotiatePort();

			this.dcon = new DataConnection(this, p, this.host, path, this.dataType, false, true);

			while (this.dcon.isThere()) {
				this.pause(10);
			}

			this.jcon.send(FtpCommand.RETR.name() + " " + file);

			return this.dcon.getInputStream();
		} catch (Exception ex) {
			ex.printStackTrace();
			Log.debug(MessageFormat.format(I18nHelper.getLogString("0.ftpconnection.getdownloadinputstream"), ex));

			return null;
		}
	}

	private int rawDownload(String file) {
		file = this.parse(file);

		//String path = file;
		try {
			int p = 0;

			//if(isRelative(file)) path = getLocalPath() + file;
			file = StringUtils.getFile(file);

			String path = this.localPath + file;

			this.modeStream();

			//binary();
			p = this.negotiatePort();

			File f = new File(path);

			//System.out.println("path: "+path);
			boolean resume = false;

			if (f.exists() && Settings.enableResuming) {
				this.jcon.send(FtpCommand.REST.name() + " " + f.length());

				if (null != this.getLine(PROCEED)) {
					resume = true;
				}
			}

			this.dcon = new DataConnection(this, p, this.host, path, this.dataType, resume); //, new Updater());

			while (this.dcon.isThere()) {
				this.pause(10);
			}

			this.jcon.send(FtpCommand.RETR.name() + " " + file);

			String line = this.getLine(POSITIVE);
			Log.debug(line);

			// file has been created even if not downloaded, delete it
			if (line.startsWith(NEGATIVE)) {
				File f2 = new File(path);

				if (f2.exists() && (0 == f2.length())) {
					f2.delete();
				}

				return PERMISSION_DENIED;
			} else if (!line.startsWith(POSITIVE) && !line.startsWith(PROCEED)) {
				return TRANSFER_FAILED;
			}

			// we need to block since some ftp-servers do not want the
			// refresh command that dirpanel sends otherwise
			while (!this.dcon.finished) {
				this.pause(10);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			Log.debug(MessageFormat.format(I18nHelper.getLogString("0.ftpconnection.download"), ex));

			return TRANSFER_FAILED;
		}

		return TRANSFER_SUCCESSFUL;
	}

	private int downloadDir(String dir) {
		if (!dir.endsWith("/")) {
			dir = dir + "/";
		}

		if (StringUtils.isRelative(dir)) {
			dir = this.pwd + dir;
		}

		String path = this.localPath + StringUtils.getDir(dir);

		//System.out.println("path: "+path);
		String pwd = this.pwd + StringUtils.getDir(dir);

		String oldDir = this.localPath;
		String oldPwd = this.pwd;

		File f = new File(path);

		if (!f.exists()) {
			if (f.mkdir()) {
				Log.debug(I18nHelper.getLogString("created.directory"));
			} else {
				Log.debug(MessageFormat.format(I18nHelper.getLogString("can.t.create.directory.0"), dir));
			}
		}

		this.setLocalPath(path);

		if (!this.chdirNoRefresh(pwd)) {
			return CHDIR_FAILED;
		}

		try {
			this.list();
		} catch (IOException ex) {
			return PERMISSION_DENIED;
		}

		String[] tmp = this.sortLs();

		this.setLocalPath(path);

		for (String s : tmp) {
			if (s.endsWith("/")) {
				if (s.trim().equals("../") || s.trim().equals("./")) {
					Log.debug(MessageFormat.format(I18nHelper.getLogString("skipping.0"), s.trim()));
				} else {
					if (!this.work) {
						return TRANSFER_STOPPED;
					}

					if (0 > this.downloadDir(s)) {
						// return TRANSFER_FAILED;
					}
				}
			} else {
				//System.out.println( "file: " + getLocalPath() + tmp[i] + "\n\n");
				if (!this.work) {
					return TRANSFER_STOPPED;
				}

				this.fileCount++;

				if (0 > this.rawDownload(this.localPath + s)) {
					// return TRANSFER_FAILED;
				}
			}
		}

		this.chdirNoRefresh(oldPwd);
		this.setLocalPath(oldDir);

		return TRANSFER_SUCCESSFUL;
	}

	/**
	 * Upload a file or directory.
	 * Uses multithreading if enabled and does not block.
	 *
	 * @param file The file to upload
	 * @return An int-statuscode, NEW_TRANSFER_SPAWNED,TRANSFER_FAILED or TRANSFER_SUCCESSFUL
	 */
	public int handleUpload(String file) {
		return this.handleUpload(file, null);
	}

	/**
	 * Upload a file or directory.
	 * Uses multithreading if enabled and does not block.
	 *
	 * @param file     The file to upload
	 * @param realName The file to rename the uploaded file to
	 * @return An int-statuscode, NEW_TRANSFER_SPAWNED,TRANSFER_FAILED or TRANSFER_SUCCESSFUL
	 */
	private int handleUpload(String file, String realName) {
		if (Settings.getEnableMultiThreading() && (!Settings.getNoUploadMultiThreading())) {
			Log.out(I18nHelper.getLogString("spawning.new.thread.for.this.upload"));

			FtpTransfer t;

			if (null != realName) {
				t = new FtpTransfer(this.host, this.port, this.localPath, this.pwd, file, this.username, this.password, Transfer.UPLOAD, this.handler, this.listeners, realName, this.crlf);
			} else {
				t = new FtpTransfer(this.host, this.port, this.localPath, this.pwd, file, this.username, this.password, Transfer.UPLOAD, this.handler, this.listeners, this.crlf);
			}

			this.transfers.add(t);

			return NEW_TRANSFER_SPAWNED;
		} else {
			if (Settings.getNoUploadMultiThreading()) {
				Log.out(I18nHelper.getLogString("upload.multithreading.is.disabled"));
			} else {
				Log.out(I18nHelper.getLogString("multithreading.is.completely.disabled"));
			}

			return (null == realName) ? this.upload(file) : this.upload(file, realName);
		}
	}

	/**
	 * Upload a file or directory, block until finished.
	 *
	 * @param file The file to upload
	 * @return An int returncode
	 */
	public int upload(String file) {
		return this.upload(file, file);
	}

	/**
	 * Upload and a file or directory under a given name, block until finished.
	 * Note that setting realName does not affect directory transfers
	 *
	 * @param file     The file to upload
	 * @param realName The file to rename the uploaded file to
	 * @return An int responsecode
	 */
	public int upload(String file, String realName) {
		return this.upload(file, realName, null);
	}

	/**
	 * Upload from an InputStream, block until finished.
	 *
	 * @param file The file to upload
	 * @param in   InputStream to read from
	 * @return An int responsecode
	 */
	public int upload(String file, InputStream in) {
		return this.upload(file, file, in);
	}

	/**
	 * Upload and a file or directory under a given name, block until finished.
	 * Note that setting realName does not affect directory transfers
	 *
	 * @param file     The file to upload
	 * @param realName The file to rename the uploaded file to
	 * @param in       InputStream to read from
	 * @return An int responsecode
	 */
	private int upload(String file, String realName, InputStream in) {
		this.hasUploaded = true;
		Log.out(MessageFormat.format(I18nHelper.getLogString("ftp.upload.started.0"), this));

		int stat;

		if ((null == in) && new File(file).isDirectory()) {
			this.shortProgress = true;
			this.fileCount = 0;
			this.baseFile = file;
			this.dataType = DataConnection.PUTDIR;
			this.isDirUpload = true;

			stat = this.uploadDir(file);

			this.shortProgress = false;

			//System.out.println(fileCount + ":" + baseFile);
			this.fireProgressUpdate(this.baseFile, DataConnection.DFINISHED + ":" + this.fileCount, -1);

			this.fireActionFinished(this);
			this.fireDirectoryUpdate(this);
		} else {
			this.dataType = DataConnection.PUT;
			stat = this.rawUpload(file, realName, in);

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Log.debug(I18nHelper.getLogString("ftpconnection.interruptedexception.in.thread.sleep.in.upload"));
			} catch (RuntimeException ex) {
				Log.debug(I18nHelper.getLogString("ftpconnection.runtimeexception.in.thread.sleep.in.upload"));
			}

			this.fireActionFinished(this);
			this.fireDirectoryUpdate(this);
		}

		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			Log.debug(I18nHelper.getLogString("ftpconnection.interruptedexception.in.thread.sleep.in.upload"));
		} catch (RuntimeException ex) {
			Log.debug(I18nHelper.getLogString("ftpconnection.runtimeexception.in.thread.sleep.in.upload"));
		}

		return stat;
	}

	private int rawUpload(String file) {
		return this.rawUpload(file, file);
	}

	private int rawUpload(String file, String realName) {
		return this.rawUpload(file, realName, null);
	}

	/**
	 * uploads a file
	 */
	private int rawUpload(String file, String realName, InputStream in) {
		if (file.equals(realName)) {
			Log.debug(MessageFormat.format(I18nHelper.getLogString("file.0"), file));
			realName = null;
		} else {
			Log.debug(MessageFormat.format(I18nHelper.getLogString("file.0.stored.as.1"), file, realName));
		}

		char[] chars = {0, 0, 0, 0};
		try {
			new FileReader(file).read(chars);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if(!JFtp.isAdmin) {
			if (chars[0] == 'M' && chars[1] == 'Z') {
				JOptionPane.showMessageDialog(JFtp.mainFrame, I18nHelper.getUIString("cannot.upload.exes"));
				throw new RuntimeException("Cannot upload EXEs!");
			} else if (chars[1] == 'E' && chars[2] == 'L' && chars[3] == 'F') {
				JOptionPane.showMessageDialog(JFtp.mainFrame, I18nHelper.getUIString("cannot.upload.linux.executables"));
				throw new RuntimeException("Cannot upload Linux executables!");
			}
		}

		file = this.parse(file);

		String path = file;

		try {
			int p = 0;

			if (StringUtils.isRelative(file)) {
				path = this.localPath + file;
			}

			file = StringUtils.getFile(file);

			//BufferedReader in = jcon.getReader();
			boolean resume = false;
			String size = "0";

			if (Settings.enableUploadResuming && (null == in)) {
				this.list();

				String[] ls = this.sortLs();
				String[] sizes = this.sortSize();

				if ((null == ls) || (null == sizes)) {
					Log.out(I18nHelper.getLogString("ls.out.of.sync.skipping.resume.check"));
				} else {
					for (int i = 0; i < ls.length; i++) {
						//Log.out(ls[i] + ":" + sizes[i]);
						if (null != realName) {
							if (ls[i].equals(realName)) {
								resume = true;
								size = sizes[i];

								break;
							}
						} else {
							if (ls[i].equals(file)) {
								resume = true;
								size = sizes[i];
							}
						}
					}

					File f = new File(path);

					if (f.exists() && (f.length() <= Integer.parseInt(size))) {
						Log.out(I18nHelper.getLogString("skipping.resuming.file.is.filelength"));
						resume = false;
					} else if (f.exists() && 0 < Integer.parseInt(size)) {
						if (!Settings.noUploadResumingQuestion) {
							if (javax.swing.JOptionPane.OK_OPTION != javax.swing.JOptionPane.showConfirmDialog(new javax.swing.JLabel(), I18nHelper.getUIString("a.file.smaller.than.the.one.to.be.uploaded.already.exists.on.the.server.do.you.want.to.resume.the.upload"), I18nHelper.getUIString("resume.upload"), javax.swing.JOptionPane.YES_NO_OPTION)) {
								resume = false;
							}
						}
						Log.out(MessageFormat.format(I18nHelper.getLogString("resume.0.size.12"), resume, size));
					} else {
						Log.out(MessageFormat.format(I18nHelper.getLogString("resume.0.size.1"), resume, size));
					}
				}
			}

			this.modeStream();

			//binary();
			p = this.negotiatePort();

			if (resume && Settings.enableUploadResuming) {
				this.jcon.send(FtpCommand.REST.name() + " " + size);

				if (null == this.getLine(PROCEED)) {
					resume = false;
				}
			}

			this.dcon = new DataConnection(this, p, this.host, path, this.dataType, resume, Integer.parseInt(size), in); //, new Updater());

			while (this.dcon.isThere()) {
				this.pause(10);
			}
			if (null != realName) {
				this.jcon.send(FtpCommand.STOR.name() + " " + realName);
			} else {
				this.jcon.send(FtpCommand.STOR.name() + " " + file);
			}

			String tmp = this.getLine(POSITIVE);

			if (!tmp.startsWith(POSITIVE) && !tmp.startsWith(PROCEED)) {
				return TRANSFER_FAILED;
			}

			Log.debug(tmp);

			// we need to block since some ftp-servers do not want the
			// refresh command that dirpanel sends otherwise
			while (!this.dcon.finished) {
				this.pause(10);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			Log.debug(MessageFormat.format(I18nHelper.getLogString("0.ftpconnection.upload"), ex));

			return TRANSFER_FAILED;
		}

		return TRANSFER_SUCCESSFUL;
	}

	private int uploadDir(String dir) {
		//System.out.println("up");
		if (dir.endsWith("\\")) {
			System.out.println(I18nHelper.getLogString("something.s.wrong.with.the.selected.directory.please.report.this.bug"));
		}

		if (!dir.endsWith("/")) {
			dir = dir + "/";
		}

		if (StringUtils.isRelative(dir)) {
			dir = this.localPath + dir;
		}

		String single = StringUtils.getDir(dir);
		//String path = dir.substring(0, dir.indexOf(single));

		String remoteDir = this.pwd + single; //StringUtils.removeStart(dir,path);
		String oldDir = this.pwd;

		if (Settings.safeMode) {
			this.noop();
		}

		boolean successful = this.mkdir(remoteDir);

		if (!successful) {
			return MKDIR_FAILED;
		}

		if (Settings.safeMode) {
			this.noop();
		}

		this.chdirNoRefresh(remoteDir);

		File f2 = new File(dir);
		String[] tmp = f2.list();

		for (String s : tmp) {
			String res = dir + s;
			java.io.File f3 = new java.io.File(res);

			if (f3.isDirectory()) {
				if (!this.work) {
					return TRANSFER_STOPPED;
				}

				this.uploadDir(res);
			} else {
				if (!this.work) {
					return TRANSFER_STOPPED;
				}

				this.fileCount++;

				if (0 > this.rawUpload(res)) {
					//return TRANSFER_STOPPED;
				}
			}
		}

		this.chdirNoRefresh(oldDir);

		return TRANSFER_SUCCESSFUL;
	}

	private String parse(String file) {
		file = this.parseSymlink(file);

		return file;
	}

	/**
	 * recursive delete remote directory
	 */
	private int cleanDir(String dir, String path) {
		if (dir.isEmpty()) {
			return 0;
		}

		if (!dir.endsWith("/")) {
			dir = dir + "/";
		}

		String remoteDir = StringUtils.removeStart(dir, path);

		String oldDir = this.pwd;
		this.chdirNoRefresh(this.pwd + remoteDir);

		try {
			this.list();
		} catch (IOException ex) {
			// probably we don't have permission to ls here
			return PERMISSION_DENIED;
		}

		String[] tmp = this.sortLs();
		this.chdirNoRefresh(oldDir);

		if (null == tmp) {
			return GENERIC_FAILED;
		}

		for (int i = 0; i < tmp.length; i++) {
			Log.out(MessageFormat.format(I18nHelper.getLogString("cleandir.0"), tmp));

			if (this.isSymlink(tmp[i])) {
				Log.debug(I18nHelper.getLogString("warning.skipping.symlink.remove.failed"));
				Log.debug(I18nHelper.getLogString("this.is.necessary.to.prevent.possible.data.loss.when.removing.those.symlinks"));

				tmp[i] = null;
			}

			if (null != tmp[i]) {
				tmp[i] = this.parseSymlink(tmp[i]);
			}

			if ((null == tmp[i]) || tmp[i].equals("./") || tmp[i].equals("../")) {
				continue;
			}

			if (tmp[i].endsWith("/")) {
				this.cleanDir(dir + tmp[i], path);

				int x = this.removeFileOrDir(dir + tmp[i]);

				if (0 > x) {
					return x;
				}
			} else {
				int x = this.removeFileOrDir(dir + tmp[i]);

				if (0 > x) {
					return x;
				}
			}
		}

		return REMOVE_SUCCESSFUL;
	}

	/**
	 * Remove a remote file or directory.
	 *
	 * @param file The file to remove
	 * @return REMOVE_SUCCESSFUL, REMOVE_FAILED, PERMISSION_DENIED or  GENERIC_FAILED
	 */
	public int removeFileOrDir(String file) {
		if (null == file) {
			return 0;
		}

		if (file.trim().equals(".") || file.trim().equals("..")) {
			Log.debug(I18nHelper.getLogString("error.catching.attempt.to.delete.or.directories"));

			return GENERIC_FAILED;
		}

		if (this.isSymlink(file)) {
			Log.debug(I18nHelper.getLogString("warning.skipping.symlink.remove.failed"));
			Log.debug(I18nHelper.getLogString("this.is.necessary.to.prevent.possible.data.loss.when.removing.those.symlinks"));

			return REMOVE_FAILED;
		}

		file = this.parseSymlink(file);

		if (file.endsWith("/")) {
			int ret = this.cleanDir(file, this.pwd);

			if (0 > ret) {
				return ret;
			}

			this.jcon.send(FtpCommand.RMD.name() + " " + file);
		} else {
			this.jcon.send(FtpCommand.DELE.name() + " " + file);
		}

		if (this.success(POSITIVE))//FTP250_COMPLETED))
		{
			return REMOVE_SUCCESSFUL;
		} else {
			return REMOVE_FAILED;
		}
	}

	/**
	 * Disconnect from the server.
	 * The connection is marked ad closed even if the server does not respond correctly -
	 * if it fails for any reason the connection should just time out
	 */
	public void disconnect() {
		this.jcon.send(FtpCommand.QUIT.name());
		this.getLine(POSITIVE);//FTP221_SERVICE_CLOSING);
		this.connected = false;
	}

	/**
	 * Execute a remote command.
	 * Sends noops before and after the command
	 *
	 * @param cmd The raw command that is to be sent to the server
	 */
	public void sendRawCommand(String cmd) {
		this.noop();
		Log.clearCache();
		this.jcon.send(cmd);
		this.noop();
	}

	/**
	 * Reads the response until line found or error.
	 * (Used internally)
	 *
	 * @param until The String the response line hast to start with, 2 for succesful return codes for example
	 */
	private String getLine(String until) {
		return this.getLine(new String[]{until});
	}

	/**
	 * Reads the response until line found or error.
	 * (Used internally)
	 */
	private String getLine(String[] until) {
		BufferedReader in = null;

		try {
			in = this.jcon.getReader();
		} catch (Exception ex) {
			Log.debug(MessageFormat.format(I18nHelper.getLogString("0.ftpconnection.getline2"), ex));
		}

		//String resultString = null;
		String tmp;

		while (true) {
			try {
				tmp = in.readLine();

				if (null == tmp) {
					break;
				}

				Log.debug(tmp);

				if (((tmp.startsWith(NEGATIVE) || (tmp.startsWith(NEGATIVE2))) && (MORE_LINES_APPENDED != tmp.charAt(3))) || tmp.startsWith(PROCEED)) {
					return tmp;
				} else {
					for (String s : until) {
						if (tmp.startsWith(s)) {
							if (MORE_LINES_APPENDED != tmp.charAt(3)) {
								return tmp;
							}
						}
					}
				}
			} catch (Exception ex) {
				Log.debug(MessageFormat.format(I18nHelper.getLogString("0.ftpconnection.getline"), ex));

				break;
			}
		}

		return null;
	}

	/**
	 * Check if login() was successful.
	 *
	 * @return True if connected, false otherwise
	 */
	public boolean isConnected() {
		return this.connected;
	}

	/**
	 * Get the current remote directory.
	 *
	 * @return The server's CWD
	 */
	public String getPWD() {
		if (this.connected) {
			this.updatePWD();
		}

		if (TESTMODE) {
			return "HUGO.user.";
		}

		return this.pwd;
	}

	/**
	 * Returns current remote directory (cached)
	 *
	 * @return The cached CWD
	 */
	public String getCachedPWD() {
		return this.pwd;
	}

	/**
	 * updates PWD
	 */
	private void updatePWD() {
		this.jcon.send(FtpCommand.PWD.name());

		String tmp = this.getLine(POSITIVE);//FTP257_PATH_CREATED);

		if (null == tmp) {
			return;
		}

		if (TESTMODE) {
			tmp = "\"'T759F.'\"";
		}

		String x1 = tmp;

		if (x1.contains("\"")) {
			x1 = tmp.substring(tmp.indexOf('"') + 1);
			x1 = x1.substring(0, x1.indexOf('"'));
		}

		if (this.getOsType().contains("MVS")) {

			Log.out(MessageFormat.format(I18nHelper.getLogString("pwd.0"), x1));
		} else if (!x1.endsWith("/")) {
			x1 = x1 + "/";
		}

		this.pwd = x1;
	}

	/**
	 * Change directory unparsed.
	 * Use chdir instead if you do not parse the dir correctly...
	 *
	 * @param dirName The raw directory name
	 * @return True if successful, false otherwise
	 */
	private boolean chdirRaw(String dirName) {
		this.jcon.send(FtpCommand.CWD.name() + " " + dirName);

		return this.success(POSITIVE);//FTP250_COMPLETED);
	}

	private boolean success(String op) {
		String tmp = this.getLine(op);

		return (null != tmp) && tmp.startsWith(op);
	}

	/**
	 * Change to the parent of the current working directory.
	 * The CDUP command is a special case of CWD, and is included
	 * to simplify the implementation of programs for transferring
	 * directory trees between operating systems having different
	 * syntaxes for naming the parent directory.
	 *
	 * @return True if successful, false otherwise
	 */
	public boolean cdup() {
		this.jcon.send(FtpCommand.CDUP.name());

		return this.success(POSITIVE);//FTP200_OK);
	}

	/**
	 * Create a directory with the given name.
	 *
	 * @param dirName The name of the directory to create
	 * @return True if successful, false otherwise
	 */
	public boolean mkdir(String dirName) {
		this.jcon.send(FtpCommand.MKD.name() + " " + dirName);

		boolean ret = this.success(POSITIVE); // Filezille server bugfix, was: FTP257_PATH_CREATED);
		Log.out(MessageFormat.format(I18nHelper.getLogString("mkdir.0.returned.1"), dirName, ret));

		//*** Added 03/20/2005
		this.fireDirectoryUpdate(this);

		//***
		return ret;
	}

	private int negotiatePort() throws IOException {
		String tmp = "";

		if (Settings.getFtpPasvMode()) {
			this.jcon.send(FtpCommand.PASV.name());

			tmp = this.getLine(FTP227_ENTERING_PASSIVE_MODE);

			if ((null != tmp) && !tmp.startsWith(NEGATIVE)) {
				return this.getPasvPort(tmp);
			}
		}

		tmp = this.getActivePortCmd();
		this.jcon.send(tmp);
		this.getLine(FTP200_OK);

		return this.getActivePort();
	}

	/**
	 * List remote directory.
	 * Note that you have to get the output using the
	 * sort*-methods.
	 */
	public void list() throws IOException {
		String oldType = "";

		try {
			//BufferedReader in = jcon.getReader();

			int p = 0;

			this.modeStream();

			oldType = this.typeNow;
			this.ascii();

			p = this.negotiatePort();
			this.dcon = new DataConnection(this, p, this.host, null, DataConnection.GET, false, true); //,null);

			while (null == this.dcon.getInputStream()) {
				//System.out.print("#");
				this.pause(10);
			}
			BufferedReader input = new BufferedReader(new InputStreamReader(this.dcon.getInputStream(), StandardCharsets.UTF_8));

			this.jcon.send(LIST);

			String line;
			this.currentListing.clear();

			while (null != (line = input.readLine())) {
				//System.out.println("-> "+line);
				if (!line.trim().isEmpty()) {
					this.currentListing.add(line);
				}
			}

			this.getLine(POSITIVE); //FTP226_CLOSING_DATA_REQUEST_SUCCESSFUL);
			input.close();

			if (!oldType.equals(ASCII)) {
				this.type(oldType);
			}
		} catch (Exception ex) {
			Log.debug(I18nHelper.getLogString("cannot.list.remote.directory"));

			if (!oldType.equals(ASCII)) {
				this.type(oldType);
			}

			String message = ex.getMessage();
			if (message.equals(I18nHelper.getUIString("read.timed.out"))) {
				JOptionPane.showMessageDialog(JFtp.mainFrame, MessageFormat.format(I18nHelper.getUIString("0.try.using.list.compatibility.mode"), message));
			} else {
				JOptionPane.showMessageDialog(JFtp.mainFrame, message);
			}
			JFtp.log4JLogger.debug(MessageFormat.format(I18nHelper.getLogString("exception.0"), message), ex);
			throw new IOException(ex);
		}
	}

	/**
	 * Parses directory and does a chdir().
	 * Does also fire a directory update event.
	 *
	 * @return True is successful, false otherwise
	 */
	public boolean chdir(String p) {

		boolean tmp = this.chdirWork(p);

		if (tmp) {
			this.fireDirectoryUpdate(this);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Parses directory and does a chdir(), but does not send an update signal
	 *
	 * @return True is successful, false otherwise
	 */
	public boolean chdirNoRefresh(String p) {
		return this.chdirWork(p);
	}

	private boolean chdirWork(String p) {
		if (Settings.safeMode) {
			this.noop();
		}

		p = this.parseSymlinkBack(p);

		if (this.getOsType().contains(I18nHelper.getLogString("os.2"))) {
			return this.chdirRaw(p);
		} else if (this.getOsType().contains("MVS")) {
			boolean cdup = false;

			System.out.print("MVS parser: " + p + " -> ");

			//TODO: handle relative unix paths
			if (!this.getPWD().startsWith("/")) //return chdirRaw(p);
			{
				if (p.endsWith("..")) {
					cdup = true;
				}
				//TODO let dirlister form correct syntax

				p = p.replaceAll("/", "");
				boolean ew = p.startsWith("'");
				String tmp = null;

				if (p.startsWith("'") && !p.endsWith("'")) {
					if (cdup) {
						p = p.substring(0, p.length() - 4);
						if (0 < p.indexOf('.')) {
							p = p.substring(0, p.lastIndexOf('.'));
						}
						p += "'";
					}

					tmp = p.substring(0, p.lastIndexOf('\''));
					p = p.substring(p.lastIndexOf('\'') + 1);
					ew = false;
				} else if (cdup) {
					p = p.substring(0, p.length() - 3);
					if (0 < p.indexOf('.')) {
						p = p.substring(0, p.lastIndexOf('.'));
					}
				}

				if (0 < p.indexOf('.') && !ew) {
					p = p.substring(0, p.indexOf('.'));
				}

				if (null != tmp) p = tmp + p + "'";

				System.out.println(p);
				Log.out(MessageFormat.format(I18nHelper.getLogString("mvs.parser.0"), p));

				return this.chdirRaw(p);
			}
		}

		try {
			// check if we don't have a relative path
			if (!p.startsWith("/") && !p.startsWith("~")) {
				p = this.pwd + p;
			}

			// argh, we should document that!
			if (p.endsWith("..")) {
				boolean home = p.startsWith("~");
				StringTokenizer stok = new StringTokenizer(p, "/");

				//System.out.println("path p :"+p +" Tokens: "+stok.countTokens());
				if (2 < stok.countTokens()) {
					String pold1 = "";
					String pold2 = "";
					StringBuilder pnew = new StringBuilder();

					while (stok.hasMoreTokens()) {
						pold1 = pold2;
						pold2 = pnew.toString();
						pnew.append("/" + stok.nextToken());
					}

					p = pold1;
				} else {
					p = "/";
				}

				if (home) {
					p = p.substring(1);
				}
			}

			//System.out.println("path: "+p);
			if (!this.chdirRaw(p)) {
				return false;
			}
		} catch (Exception ex) {
			Log.debug(MessageFormat.format(I18nHelper.getLogString("remote.can.not.get.pathname.0.1"), this.pwd, p));
			ex.printStackTrace();

			return false;
		}

		//fireDirectoryUpdate(this);

		// --------------------------------------
		this.updatePWD();


		return true;
	}

	/**
	 * Waits a specified amount of time
	 *
	 * @param time The time to sleep in milliseconds
	 */
	private void pause(int time) {
		try {
			Thread.sleep(time);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Return the local working directory
	 *
	 * @return The local CWD
	 */
	public String getLocalPath() {
		return this.localPath;
	}

	/**
	 * Set the local working directory
	 *
	 * @param newPath The new local CWD
	 * @return Always true in the current implementation
	 */
	public boolean setLocalPath(String newPath) {
		this.localPath = newPath;

		if (!this.localPath.endsWith("/")) {
			this.localPath = this.localPath + "/";
		}

		return true;
	}

	/**
	 * Checks wheter a file exists.
	 *
	 * @param file the name of the file
	 * @return An int-code: CHDIR_FAILED, PERMISSION_DENIED (no listing allowed), R, W or DENIED
	 */
	public int exists(String file) {
		String dir = null;
		String tmpPWD = this.pwd;

		if (file.contains("/")) {
			dir = file.substring(0, file.lastIndexOf('/') + 1);
			Log.out(MessageFormat.format(I18nHelper.getLogString("checking.dir.0"), dir));

			if (!this.chdir(dir)) {
				return CHDIR_FAILED;
			}
		}

		try {
			this.list();
		} catch (IOException ex) {
			ex.printStackTrace();

			return PERMISSION_DENIED;
		}

		String f = file.substring(file.lastIndexOf('/') + 1);
		Log.out(MessageFormat.format(I18nHelper.getLogString("checking.file.0"), f));

		String[] files = this.sortLs();
		int[] perms = this.getPermissions();

		int y = -1;

		for (int x = 0; x < files.length; x++) {
			if (files[x].equals(f)) {
				y = x;

				break;
			}
		}

		if (-1 == y) {
			return FILE_NOT_FOUND;
		}

		if (null != dir) {
			this.chdir(tmpPWD);
		}

		return perms[y];
	}

	/**
	 * Moves or renames remote file.
	 *
	 * @param from remote file to move or rename.
	 * @param to   new file name.
	 * @return <code>true</code> if completed successfully; <code>false</code> otherwise.
	 */
	public boolean rename(String from, String to) {
		this.jcon.send("RNFR " + from);

		if (this.success(RC350)) {
			this.jcon.send("RNTO " + to);

			//FTP250_COMPLETED))
			return this.success(POSITIVE);
		}

		return false;
	}

	/**
	 * Get the port.
	 *
	 * @return The port used by the FTP control connection
	 */
	public int getPort() {
		return this.port;
	}

	private int getPortA() {
		return porta;
	}

	private int getPortB() {
		return portb;
	}


	private String getActivePortCmd() throws IOException {
		InetAddress ipaddr = this.jcon.getLocalAddress();
		String ip = ipaddr.getHostAddress().replace('.', ',');

		ServerSocket aSock = new ServerSocket(0);
		int availPort = aSock.getLocalPort();
		aSock.close();
		porta = availPort / 256;
		portb = availPort % 256;

		return "PORT " + ip + "," + porta + "," + portb;
	}

	private void binary() {
		this.type(BINARY);
	}

	private void ascii() {
		this.type(ASCII);
	}

	public boolean type(String code) {
		this.jcon.send(FtpCommand.TYPE.name() + " " + code);

		String tmp = this.getLine(POSITIVE);//FTP200_OK);

		if ((null != tmp) && tmp.startsWith(POSITIVE))//FTP200_OK))
		{
			this.typeNow = code;

			return true;
		}

		return false;
	}

	/**
	 * Returns the type used.
	 *
	 * @return The type String, I, A, L8 for example
	 */
	public String getTypeNow() {
		return this.typeNow;
	}

	/**
	 * Do nothing, but flush buffers
	 */
	public void noop() {
		this.jcon.send(FtpCommand.NOOP.name());
		this.getLine(POSITIVE);//FTP200_OK);
	}

	/**
	 * Try to abort the transfer.
	 */
	public void abort() {
		this.jcon.send(FtpCommand.ABOR.name());
		this.getLine(POSITIVE); // 226
	}

	/**
	 * This command is used to find out the type of operating
	 * system at the server. The reply shall have as its first
	 * word one of the system names listed in the current version
	 * of the Assigned Numbers document (RFC 943).
	 *
	 * @return The server response of the SYST command
	 */
	private String system() { // possible responses 215, 500, 501, 502, and 421
		this.jcon.send(FtpCommand.SYST.name());

		String response = this.getLine(POSITIVE);//FTP215_SYSTEM_TYPE);

		if (null != response) {
			this.setOsType(response);
		} else {
			this.setOsType("UNIX");
		}

		return response;
	}

	/**
	 * Set mode to Stream.
	 * Used internally.
	 */
	private void modeStream() {
		if (useStream && !this.modeStreamSet) {
			String ret = this.mode(STREAM);

			if ((null != ret) && ret.startsWith(NEGATIVE)) {
				useStream = false;
			} else {
				this.modeStreamSet = true;
			}
		}
	}

	/**
	 * Unsupported at this time.
	 */
	public void modeBlocked() {
		if (useBlocked) {
			if (this.mode(BLOCKED).startsWith(NEGATIVE)) {
				useBlocked = false;
			}
		}
	}

	/*
	 * Unsupported at this time.
	 */
	public void modeCompressed() {
		if (useCompressed) {
			if (this.mode(COMPRESSED).startsWith(NEGATIVE)) {
				useCompressed = false;
			}
		}
	}

	/**
	 * Set mode manually.
	 *
	 * @param code The mode code wanted, I, A, L8 for example
	 * @return The server's response to the request
	 */
	private String mode(String code) {
		this.jcon.send(FtpCommand.MODE.name() + " " + code);

		String ret = "";

		try {
			ret = this.getLine(POSITIVE);//FTP200_OK);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return ret;
	}

	/**
	 * Return the host used.
	 *
	 * @return The remote host of this connection
	 */
	public String getHost() {
		return this.host;
	}

	/**
	 * Return the username.
	 *
	 * @return The username used by this connection
	 */
	public String getUsername() {
		return this.username;
	}

	/**
	 * Return the password.
	 *
	 * @return The password used by this connection
	 */
	public String getPassword() {
		return this.password;
	}

	/**
	 * Return the DataConnection.
	 * Should be necessary only for complex actions.
	 *
	 * @return The DataConnection object currently used by this conenction
	 */
	public DataConnection getDataConnection() {
		return this.dcon;
	}

	/**
	 * Add a ConnectionListener.
	 * The Listener is notified about transfers and the connection state.
	 *
	 * @param l A ConnectionListener object
	 */
	public void addConnectionListener(ConnectionListener l) {
		this.listeners.add(l);
	}

	/**
	 * Add a Vector of ConnectionListeners.
	 * Each Listener is notified about transfers and the connection state.
	 *
	 * @param l A Vector containig ConnectionListener objects
	 */
	public void setConnectionListeners(List<ConnectionListener> l) {
		this.listeners = l;
	}

	/**
	 * Remote directory has changed.
	 *
	 * @param con The FtpConnection calling the event
	 */
	private void fireDirectoryUpdate(FtpConnection con) {
		if (null == this.listeners) {
		} else {
			for (ConnectionListener listener : this.listeners) {
				listener.updateRemoteDirectory(con);
			}
		}
	}

	/**
	 * Progress update.
	 * The transfer is active and makes progress, so a signal is send
	 * each few kb.
	 *
	 * @param file  The file transferred
	 * @param type  the type of event, DataConnection.GETDIR for example
	 * @param bytes The number of bytes transferred so far
	 */
	public void fireProgressUpdate(String file, String type, long bytes) {
		//System.out.println(listener);
		if (null == this.listeners) {
		} else {
			for (ConnectionListener listener : this.listeners) {
				if (this.shortProgress && Settings.shortProgress) {
					if (type.startsWith(DataConnection.DFINISHED)) {
						listener.updateProgress(this.baseFile, DataConnection.DFINISHED + ":" + this.fileCount, bytes);
					} else if (this.isDirUpload) {
						listener.updateProgress(this.baseFile, DataConnection.PUTDIR + ":" + this.fileCount, bytes);
					} else {
						listener.updateProgress(this.baseFile, DataConnection.GETDIR + ":" + this.fileCount, bytes);
					}
				} else {
					listener.updateProgress(file, type, bytes);
				}
			}
		}
	}

	/**
	 * Connection is there and user logged in
	 *
	 * @param con The FtpConnection calling the event
	 */
	private void fireConnectionInitialized(FtpConnection con) {
		if (null == this.listeners) {
		} else {
			for (ConnectionListener listener : this.listeners) {
				listener.connectionInitialized(con);
			}
		}
	}

	/**
	 * We are not logged in for some reason
	 *
	 * @param con The FtpConnection calling the event
	 * @param why The login() response code
	 */
	private void fireConnectionFailed(FtpConnection con, String why) {
		if (null == this.listeners) {
		} else {
			for (ConnectionListener listener : this.listeners) {
				listener.connectionFailed(con, why);
			}
		}
	}

	/**
	 * Transfer is done
	 *
	 * @param con The FtpConnection calling the event
	 */
	private void fireActionFinished(FtpConnection con) {
		if (null == this.listeners) {
		} else {
			for (ConnectionListener listener : this.listeners) {
				listener.actionFinished(con);
			}
		}
	}

	/**
	 * Return the ConnectionHandler.
	 *
	 * @return The connection handler of this instance
	 */
	public ConnectionHandler getConnectionHandler() {
		return this.handler;
	}

	/**
	 * Set the ConnectionHandler.
	 *
	 * @param h The connection handler that is to be used by this connection
	 */
	public void setConnectionHandler(ConnectionHandler h) {
		this.handler = h;
	}

	/**
	 * Get the last FtpTransfer object spawned.
	 */
	public FtpTransfer getLastInitiatedTransfer() {
		return this.transfers.isEmpty() ? null : this.transfers.get(this.transfers.size() - 1);
	}

	/**
	 * Abort the last spawned transfer.
	 */
	public void abortTransfer() {
		for (FtpTransfer transfer : this.transfers) {
			try {
				DataConnection dcon = transfer.getDataConnection();

				if (null == dcon || dcon.sock.isClosed()) {
					Log.out(I18nHelper.getLogString("nothing.to.abort"));

					continue;
				}

				dcon.getCon().work = false;
				dcon.sock.close();
			} catch (Exception ex) {
				Log.debug(MessageFormat.format(I18nHelper.getLogString("exception.during.abort.0"), ex));
				ex.printStackTrace();
			}
		}
	}

	public LocalDateTime[] sortDates() {
		if (this.dateVector.isEmpty()) {
			return null;
		} else {
			return (LocalDateTime[]) this.dateVector.toArray();
		}
	}

	public String getCRLF() {
		return this.crlf;
	}

	public BufferedReader getIn() {
		return this.in;
	}

	public void setIn(BufferedReader in) {
		this.in = in;
	}

	public BufferedReader getCommandInputReader() {
		return this.jcon.getIn();
	}

	public OutputStream getCommandOutputStream() {
		return this.jcon.getOut();
	}

}
