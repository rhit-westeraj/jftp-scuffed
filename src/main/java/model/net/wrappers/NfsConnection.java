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
package model.net.wrappers;

import com.sun.xfile.XFile;
import com.sun.xfile.XFileInputStream;
import com.sun.xfile.XFileOutputStream;
import model.net.DataConnection;
import controller.config.Settings;
import model.net.BasicConnection;
import model.net.ConnectionListener;
import model.net.FtpConstants;
import model.system.StringUtils;
import model.system.logging.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;


public class NfsConnection implements BasicConnection {
	private static final int buffer = 128000;
	private final boolean dummy = false;
	private String url = "";
	private String host = "";
	private String path = "";
	private String pwd = "";
	private java.util.List<ConnectionListener> listeners = new java.util.ArrayList<>();
	private String[] files;
	private String[] size = new String[0];
	private int[] perms;
	private String baseFile;
	private int fileCount;
	private boolean isDirUpload;
	private boolean shortProgress;

	public NfsConnection(String url) {
		super();
		this.url = url;

		this.host = url.substring(6);

		int x = this.host.indexOf('/');

		if (0 <= x) {
			this.host = this.host.substring(0, x);
		}

		Log.out("nfs host is: " + this.host);
	}

	public boolean login(String user, String pass) {
		Log.out("nfs login called: " + this.url);

		try {
			XFile xf = new XFile(this.url);

			if (xf.exists()) {
				Log.out("nfs url ok");
			} else {
				Log.out("WARNING: nfs url not found, cennection will fail!");
			}

			com.sun.nfs.XFileExtensionAccessor nfsx = (com.sun.nfs.XFileExtensionAccessor) xf.getExtensionAccessor();

			if (nfsx.loginPCNFSD(this.host, user, pass)) {
				Log.debug("Login successful...");
			} else {
				Log.out("login failed!");

				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return true;
	}

	private String[] getExports() throws java.net.UnknownHostException, IOException {
		XFile xf = new XFile(this.url);
		com.sun.nfs.XFileExtensionAccessor nfsx = (com.sun.nfs.XFileExtensionAccessor) xf.getExtensionAccessor();

		String[] tmp = nfsx.getExports();

		if (null == tmp) {
			return new String[0];
		}

		for (String s : tmp) {
			Log.out("nfs export found: " + s);
		}

		return tmp;
	}

	public int removeFileOrDir(String file) {
		try {
			String tmp = this.toNFS(file);

			XFile f = new XFile(tmp);

			if (!f.getAbsolutePath().equals(f.getCanonicalPath())) {
				Log.debug("WARNING: Skipping symlink, remove failed.");
				Log.debug("This is necessary to prevent possible data loss when removing those symlinks.");

				return -1;
			}

			if (f.exists() && f.isDirectory()) {
				this.cleanLocalDir(tmp);
			}

			if (f.delete()) {
				return 1;
			} else {
				return -1;
			}
		} catch (IOException ex) {
			Log.debug("Error: " + ex);
			ex.printStackTrace();
		}

		return -1;
	}

	private void cleanLocalDir(String dir) {
		dir = this.toNFS(dir);

		if (dir.endsWith("\\")) {
			Log.out("need to fix \\-problem!!!");
		}

		if (!dir.endsWith("/")) {
			dir = dir + "/";
		}

		XFile f2 = new XFile(dir);
		String[] tmp = f2.list();

		if (null == tmp) {
			return;
		}

		for (String s : tmp) {
			com.sun.xfile.XFile f3 = new com.sun.xfile.XFile(dir + s);

			if (f3.isDirectory()) {
				this.cleanLocalDir(dir + s);
				f3.delete();
			} else {
				f3.delete();
			}
		}
	}

	public void sendRawCommand(String cmd) {
	}

	public void disconnect() {
	}

	public boolean isConnected() {
		return true;
	}

	public String getPWD() {
		String tmp = this.toNFS(this.pwd);

		if (!tmp.endsWith("/")) {
			tmp = tmp + "/";
		}

		return tmp;
	}

	public boolean cdup() {
		String tmp = this.pwd;

		if (this.pwd.endsWith("/") && !this.pwd.equals("nfs://")) {
			tmp = this.pwd.substring(0, this.pwd.lastIndexOf('/'));
		}

		return this.chdir(tmp.substring(0, tmp.lastIndexOf('/') + 1));
	}

	public boolean mkdir(String dirName) {
		if (!dirName.endsWith("/")) {
			dirName = dirName + "/";
		}

		dirName = this.toNFS(dirName);

		File f = new File(dirName);

		boolean x = f.mkdir();
		this.fireDirectoryUpdate();

		return x;
	}

	public void list() throws IOException {
	}

	public boolean chdir(String p) {
		return this.chdir(p, true);
	}

	private boolean chdir(String p, boolean refresh) {
		if (p.endsWith("..")) {
			return this.cdup();
		}

		String tmp = this.toNFS(p);

		if (!tmp.endsWith("/")) {
			tmp = tmp + "/";
		}

		if (3 > this.check(tmp)) {
			return false;
		}

		this.pwd = tmp;

		if (refresh) {
			this.fireDirectoryUpdate();
		}

		return true;
	}

	private int check(String url) {
		int x = 0;

		for (int j = 0; j < url.length(); j++) {
			if ('/' == url.charAt(j)) {
				x++;
			}
		}

		return x;
	}

	public boolean chdirNoRefresh(String p) {
		return this.chdir(p, false);
	}

	public String getLocalPath() {
		return this.path;
	}

	private String toNFS(String f) {
		String file;

		if (0 < f.lastIndexOf("nfs://")) {
			f = f.substring(f.lastIndexOf("nfs://"));
		}

		if (f.startsWith("nfs://")) {
			file = f;
		} else {
			file = this.getPWD() + f;
		}

		file = file.replace('\\', '/');

		Log.out("nfs url: " + file);

		return file;
	}

	public boolean setLocalPath(String p) {
		if (!p.startsWith("/") && !p.startsWith(":", 1)) {
			p = this.path + p;
		}

		File f = new File(p);

		if (f.exists()) {
			try {
				this.path = f.getCanonicalPath();
				this.path = this.path.replace('\\', '/');

				if (!this.path.endsWith("/")) {
					this.path = this.path + "/";
				}

			} catch (IOException ex) {
				Log.debug("Error: can not get pathname (local)!");

				return false;
			}
		} else {
			Log.debug("(local) No such path: \"" + p + "\"");

			return false;
		}

		return true;
	}

	public String[] sortLs() {
		String dir = this.getPWD();

		if (3 == this.check(this.toNFS(dir))) {
			try {
				this.files = this.getExports();
			} catch (Exception ex) {
				Log.debug("Can not list exports:" + ex);
				ex.printStackTrace();
			}
		} else {
			XFile f = new XFile(dir);
			this.files = f.list();
		}

		if (null == this.files) {
			return new String[0];
		}

		this.size = new String[this.files.length];
		this.perms = new int[this.files.length];

		int accessible = 0;

		for (int i = 0; i < this.files.length; i++) {
			XFile f2 = new XFile(dir + this.files[i]);

			if (f2.isDirectory() && !this.files[i].endsWith("/")) {
				this.files[i] = this.files[i] + "/";
			}

			this.size[i] = String.valueOf(f2.length());

			if (f2.canWrite()) {
				accessible = FtpConstants.W;
			} else if (f2.canRead()) {
				accessible = FtpConstants.R;
			} else {
				accessible = FtpConstants.DENIED;
			}

			this.perms[i] = accessible;
		}

		return this.files;
	}

	public String[] sortSize() {
		return this.size;
	}

	public int[] getPermissions() {
		return this.perms;
	}

	public int handleUpload(String f) {
		this.upload(f);

		return 0;
	}

	public int handleDownload(String f) {
		this.download(f);

		return 0;
	}

	public int upload(String f) {
		String file = this.toNFS(f);

		if (file.endsWith("/")) {
			String out = StringUtils.getDir(file);
			this.uploadDir(file, this.path + out);
			this.fireActionFinished(this);
		} else {
			String outfile = StringUtils.getFile(file);

			this.work(this.path + outfile, file);
			this.fireActionFinished(this);
		}

		return 0;
	}

	public int download(String f) {
		String file = this.toNFS(f);

		if (file.endsWith("/")) {
			String out = StringUtils.getDir(file);
			this.downloadDir(file, this.path + out);
			this.fireActionFinished(this);
		} else {
			String outfile = StringUtils.getFile(file);
			this.work(file, this.path + outfile);
			this.fireActionFinished(this);
		}

		return 0;
	}

	private void downloadDir(String dir, String out) {
		try {
			this.fileCount = 0;
			this.shortProgress = true;
			this.baseFile = StringUtils.getDir(dir);

			XFile f2 = new XFile(dir);
			String[] tmp = f2.list();

			if (null == tmp) {
				return;
			}

			File fx = new File(out);
			fx.mkdir();

			for (int i = 0; i < tmp.length; i++) {
				tmp[i] = tmp[i].replace('\\', '/');
				XFile f3 = new XFile(dir + tmp[i]);

				if (f3.isDirectory()) {
					if (!tmp[i].endsWith("/")) {
						tmp[i] = tmp[i] + "/";
					}

					this.downloadDir(dir + tmp[i], out + tmp[i]);
				} else {
					this.fileCount++;
					this.fireProgressUpdate(this.baseFile, DataConnection.GETDIR + ":" + this.fileCount, -1);
					this.work(dir + tmp[i], out + tmp[i]);
				}
			}

			this.fireProgressUpdate(this.baseFile, DataConnection.DFINISHED + ":" + this.fileCount, -1);
		} catch (Exception ex) {
			ex.printStackTrace();
			Log.debug("Transfer error: " + ex);
			this.fireProgressUpdate(this.baseFile, DataConnection.FAILED + ":" + this.fileCount, -1);
		}

		this.shortProgress = false;
	}

	private void uploadDir(String dir, String out) {
		try {
			this.isDirUpload = true;
			this.fileCount = 0;
			this.shortProgress = true;
			this.baseFile = StringUtils.getDir(dir);

			File f2 = new File(out);
			String[] tmp = f2.list();

			if (null == tmp) {
				return;
			}

			XFile fx = new XFile(dir);
			fx.mkdir();

			for (int i = 0; i < tmp.length; i++) {
				tmp[i] = tmp[i].replace('\\', '/');

				//System.out.println("1: " + dir+tmp[i] + ", " + out +tmp[i]);
				File f3 = new File(out + tmp[i]);

				if (f3.isDirectory()) {
					if (!tmp[i].endsWith("/")) {
						tmp[i] = tmp[i] + "/";
					}

					this.uploadDir(dir + tmp[i], out + tmp[i]);
				} else {
					this.fileCount++;
					this.fireProgressUpdate(this.baseFile, DataConnection.PUTDIR + ":" + this.fileCount, -1);
					this.work(out + tmp[i], dir + tmp[i]);
				}
			}

			this.fireProgressUpdate(this.baseFile, DataConnection.DFINISHED + ":" + this.fileCount, -1);
		} catch (Exception ex) {
			ex.printStackTrace();

			//System.out.println(dir + ", " + out);
			Log.debug("Transfer error: " + ex);
			this.fireProgressUpdate(this.baseFile, DataConnection.FAILED + ":" + this.fileCount, -1);
		}

		this.isDirUpload = false;
		this.shortProgress = true;
	}

	private void work(String file, String outfile) {
		BufferedOutputStream out = null;
		BufferedInputStream in = null;

		try {
			boolean outflag = false;

			if (outfile.startsWith("nfs://")) {
				outflag = true;
				out = new BufferedOutputStream(new XFileOutputStream(outfile));
			} else {
				out = new BufferedOutputStream(new FileOutputStream(outfile));
			}

			//System.out.println("out: " + outfile + ", in: " + file);
			if (file.startsWith("nfs://")) {
				in = new BufferedInputStream(new XFileInputStream(file));
			} else {
				in = new BufferedInputStream(new FileInputStream(file));
			}

			byte[] buf = new byte[buffer];
			int len = 0;
			int reallen = 0;

			//System.out.println(file+":"+getLocalPath()+outfile);
			while (true) {
				len = in.read(buf);

				//System.out.print(".");
				if (java.io.StreamTokenizer.TT_EOF == len) {
					break;
				}

				out.write(buf, 0, len);
				reallen += len;

				//System.out.println(file + ":" + StringUtils.getFile(file));
				if (outflag) {
					this.fireProgressUpdate(StringUtils.getFile(outfile), DataConnection.PUT, reallen);
				} else {
					this.fireProgressUpdate(StringUtils.getFile(file), DataConnection.GET, reallen);
				}
			}

			this.fireProgressUpdate(file, DataConnection.FINISHED, -1);
		} catch (IOException ex) {
			Log.debug("Error with file IO (" + ex + ")!");
			this.fireProgressUpdate(file, DataConnection.FAILED, -1);
		} finally {
			try {
				out.flush();
				out.close();
				in.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	private void update(String file, String type, int bytes) {
		if (null == this.listeners) {
		} else {
			for (ConnectionListener connectionListener : this.listeners) {
				connectionListener.updateProgress(file, type, bytes);
			}
		}
	}

	public void addConnectionListener(ConnectionListener l) {
		this.listeners.add(l);
	}

	public void setConnectionListeners(java.util.List<ConnectionListener> l) {
		this.listeners = l;
	}

	/**
	 * remote directory has changed
	 */
	private void fireDirectoryUpdate() {
		if (null == this.listeners) {
		} else {
			for (ConnectionListener listener : this.listeners) {
				listener.updateRemoteDirectory(this);
			}
		}
	}

	/**
	 * progress update
	 */
	private void fireProgressUpdate(String file, String type, int bytes) {
		//System.out.println(listener);
		if (null == this.listeners) {
		} else {
			for (ConnectionListener connectionListener : this.listeners) {

				if (this.shortProgress && Settings.shortProgress) {
					if (type.startsWith(DataConnection.DFINISHED)) {
						connectionListener.updateProgress(this.baseFile, DataConnection.DFINISHED + ":" + this.fileCount, bytes);
					} else if (this.isDirUpload) {
						connectionListener.updateProgress(this.baseFile, DataConnection.PUTDIR + ":" + this.fileCount, bytes);
					} else {
						connectionListener.updateProgress(this.baseFile, DataConnection.GETDIR + ":" + this.fileCount, bytes);
					}
				} else {
					connectionListener.updateProgress(file, type, bytes);
				}
			}
		}
	}

	private void fireActionFinished(NfsConnection con) {
		if (null == this.listeners) {
		} else {
			for (ConnectionListener listener : this.listeners) {
				listener.actionFinished(con);
			}
		}
	}

	public int upload(String file, InputStream i) {
		BufferedInputStream in = null;
		BufferedOutputStream out = null;

		try {
			file = this.toNFS(file);

			out = new BufferedOutputStream(new XFileOutputStream(file));
			in = new BufferedInputStream(i);

			byte[] buf = new byte[buffer];
			int len = 0;
			int reallen = 0;

			while (true) {
				len = in.read(buf);

				if (java.io.StreamTokenizer.TT_EOF == len) {
					break;
				}

				out.write(buf, 0, len);
				reallen += len;

				this.fireProgressUpdate(StringUtils.getFile(file), DataConnection.PUT, reallen);
			}

			this.fireProgressUpdate(file, DataConnection.FINISHED, -1);
		} catch (IOException ex) {
			Log.debug("Error with file IO (" + ex + ")!");
			this.fireProgressUpdate(file, DataConnection.FAILED, -1);

			return -1;
		} finally {
			try {
				out.flush();
				out.close();
				in.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		return 0;
	}

	public InputStream getDownloadInputStream(String file) {
		file = this.toNFS(file);
		Log.debug(file);

		try {
			return new BufferedInputStream(new XFileInputStream(file));
		} catch (Exception ex) {
			ex.printStackTrace();
			Log.debug(ex + " @NfsConnection::getDownloadInputStream");

			return null;
		}
	}

	public LocalDateTime[] sortDates() {
		return null;
	}

	public boolean rename(String from, String to) {
		Log.debug("Not implemented!");

		return false;
	}
}
