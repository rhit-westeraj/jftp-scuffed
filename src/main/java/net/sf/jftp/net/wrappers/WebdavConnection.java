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
package net.sf.jftp.net.wrappers;

import net.sf.jftp.net.BasicConnection;
import net.sf.jftp.net.ConnectionListener;
import net.sf.jftp.net.DataConnection;
import net.sf.jftp.net.FtpConnection;
import org.apache.commons.httpclient.HttpURL;
import org.apache.webdav.lib.WebdavFile;
import org.apache.webdav.lib.WebdavResource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StreamTokenizer;
import java.util.Date;
import java.util.Vector;


public class WebdavConnection implements BasicConnection {
	public static final int webdavBuffer = 32000;
	private final String user;
	private final String pass;
	private String path = "";
	private String pwd = "";
	private Vector listeners = new Vector();
	private String[] size = new String[0];
	private int[] perms = null;
	private String baseFile;
	private int fileCount;
	private boolean shortProgress = false;

	public WebdavConnection(String path, String user, String pass, ConnectionListener l) {
		this.user = user;
		this.pass = pass;

		listeners.add(l);
		this.chdir(path);
	}

	public int removeFileOrDir(String file) {
		net.sf.jftp.system.logging.Log.debug("Feature is not implemented yet");

		if (true) {
			return -1;
		}

		try {
			if ((file == null) || file.isEmpty()) {
				return -1;
			}

			String tmp = file;

			if (net.sf.jftp.system.StringUtils.isRelative(file)) {
				tmp = pwd + file;
			}

			WebdavFile f = new WebdavFile(this.getURL(tmp));

			if (!f.getAbsolutePath().equals(f.getCanonicalPath())) {

				if (!f.delete()) {
					return -1;
				}
			}

			if (f.exists() && f.isDirectory()) {
				this.cleanLocalDir(tmp);
			}

			//System.out.println(tmp);
			if (!f.delete()) {
				net.sf.jftp.system.logging.Log.debug("Removal failed.");

				return -1;
			}
		} catch (Exception ex) {
			net.sf.jftp.system.logging.Log.debug("Error: " + ex.toString());
			ex.printStackTrace();
		}

		return -1;
	}

	private void cleanLocalDir(String dir) {
		try {
			dir = dir.replace('\\', '/');

			if (!dir.endsWith("/")) {
				dir = dir + "/";
			}
			WebdavFile f2 = new WebdavFile(this.getURL(dir));
			String[] tmp = f2.list();

			if (tmp == null) {
				return;
			}

			for (String s : tmp) {
				org.apache.webdav.lib.WebdavFile f3 = new org.apache.webdav.lib.WebdavFile(this.getURL(dir + s));

				if (!f3.getAbsolutePath().equals(f3.getCanonicalPath())) {
					f3.delete();

				}

				if (f3.isDirectory()) {
					this.cleanLocalDir(dir + s);
					f3.delete();
				} else {
					//System.out.println(dir+tmp[i]);
					f3.delete();
				}
			}
		} catch (Exception ex) {
			net.sf.jftp.system.logging.Log.debug("Error: " + ex);
			ex.printStackTrace();
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
		return pwd;
	}

	public boolean cdup() {
		return this.chdir(pwd.substring(0, pwd.lastIndexOf("/") + 1));
	}

	public boolean mkdir(String dirName) {
		net.sf.jftp.system.logging.Log.debug("Feature is not implemented yet");

		if (true) {
			return false;
		}

		try {
			if (net.sf.jftp.system.StringUtils.isRelative(dirName)) {
				dirName = pwd + dirName;
			}

			WebdavFile f = new WebdavFile(this.getURL(dirName));

			boolean x = f.mkdir();
			this.fireDirectoryUpdate();

			return x;
		} catch (Exception ex) {
			net.sf.jftp.system.logging.Log.debug("Error: " + ex.toString());
			ex.printStackTrace();

			return false;
		}
	}

	public void list() throws IOException {
	}

	public boolean chdir(String p) {
		try {
			String p2 = this.processPath(p);

			if (p2 == null) {
				return false;
			}

			//WebdavFile f = new WebdavFile(getURL(p2));
			WebdavResource f = this.getResource(p2);

			if (!f.exists() || !f.isCollection()) //!f.isDirectory() || !f.canRead())
			{
				net.sf.jftp.system.logging.Log.debug("Access denied.");

				return false;
			}

			pwd = p2;

			net.sf.jftp.system.logging.Log.out("PWD: " + pwd);

			this.fireDirectoryUpdate();

			return true;
		} catch (Exception ex) {
			net.sf.jftp.system.logging.Log.debug("Error: " + ex);
			ex.printStackTrace();

			return false;
		}
	}

	public boolean chdirNoRefresh(String p) {
		String p2 = this.processPath(p);

		if (p2 == null) {
			return false;
		}

		//System.out.println(p2);
		pwd = p2;

		return true;
	}

	public String getLocalPath() {
		//System.out.println("local: " + path);
		return path;
	}

	public String processPath(String p) {
		try {
			if (!p.startsWith("http://")) {
				p = pwd + p;
			}

			if (!p.endsWith("/")) {
				p = p + "/";
			}

			while (p.endsWith("/../")) {
				p = p.substring(0, p.lastIndexOf("/../") - 1);
				p = p.substring(0, p.lastIndexOf("/"));
			}

			while (p.endsWith("/./")) {
				p = p.substring(0, p.lastIndexOf("/./") - 1);
				p = p.substring(0, p.lastIndexOf("/"));
			}

			net.sf.jftp.system.logging.Log.out("\n\n\nprocessPath URL: " + p);

			WebdavResource f = this.getResource(p);
			String p2 = p;

			if (f.exists() && f.isCollection()) {
				try {
					if (!p2.endsWith("/")) {
						p2 = p2 + "/";
					}

					return p2;
				} catch (Exception ex) {
					net.sf.jftp.system.logging.Log.debug("Error: can not get pathname (processPath)!");

					return null;
				}
			} else {
				net.sf.jftp.system.logging.Log.debug("(processpPath) No such path: \"" + p + "\"");

				return null;
			}
		} catch (Exception ex) {
			net.sf.jftp.system.logging.Log.debug("Error: " + ex);
			ex.printStackTrace();

			return null;
		}
	}

	public boolean setLocalPath(String p) {
		try {
			p = p.replace('\\', '/');

			//System.out.print("local 1:" + p);
			if (net.sf.jftp.system.StringUtils.isRelative(p)) {
				p = path + p;
			}

			p = p.replace('\\', '/');

			//System.out.println(", local 2:" + p);
			File f = new File(p);

			if (f.exists()) {
				try {
					path = f.getCanonicalPath();
					path = path.replace('\\', '/');

					if (!path.endsWith("/")) {
						path = path + "/";
					}

					//System.out.println("localPath: "+path);
				} catch (Exception ex) {
					net.sf.jftp.system.logging.Log.debug("Error: can not get pathname (local)!");

					return false;
				}
			} else {
				net.sf.jftp.system.logging.Log.debug("(local) No such path: \"" + p + "\"");

				return false;
			}

			return true;
		} catch (Exception ex) {
			net.sf.jftp.system.logging.Log.debug("Error: " + ex);
			ex.printStackTrace();

			return false;
		}
	}

	public String[] sortLs() {
		try {
			net.sf.jftp.system.logging.Log.out("sortLs PWD: " + pwd);

			WebdavResource fp = this.getResource(pwd);
			WebdavResource[] f = fp.listWebdavResources();
			String[] files = new String[f.length];
			size = new String[f.length];
			perms = new int[f.length];

			int accessible = 0;

			for (int i = 0; i < f.length; i++) {
				files[i] = f[i].getName();
				net.sf.jftp.system.logging.Log.out("sortLs files[" + i + "]: " + files[i]);

				//size[i] = "" + (int) f[i].length();
				size[i] = "" + (int) f[i].getGetContentLength();
				perms[i] = FtpConnection.R;

				if (f[i].isCollection() && !files[i].endsWith("/")) {
					files[i] = files[i] + "/";
				}
			}

			return files;
		} catch (Exception ex) {
			net.sf.jftp.system.logging.Log.debug("Error: " + ex);
			ex.printStackTrace();

			return new String[0];
		}
	}

	public String[] sortSize() {
		return size;
	}

	public int[] getPermissions() {
		return perms;
	}

	public int handleDownload(String file) {
		this.transfer(file);

		return 0;
	}

	public int handleUpload(String file) {
		this.transfer(file, true);

		return 0;
	}

	public int download(String file) {
		this.transfer(file);

		return 0;
	}

	public int upload(String file) {
		this.transfer(file, true);

		return 0;
	}

	private void transferDir(String dir, String out) {
		try {
			fileCount = 0;
			shortProgress = true;
			baseFile = net.sf.jftp.system.StringUtils.getDir(dir);

			WebdavFile f2 = new WebdavFile(this.getURL(dir));
			String[] tmp = f2.list();

			if (tmp == null) {
				return;
			}

			WebdavFile fx = new WebdavFile(this.getURL(out));

			if (!fx.mkdir()) {
				net.sf.jftp.system.logging.Log.debug("Can not create directory: " + out + " - already exist or permission denied?");
			}

			for (int i = 0; i < tmp.length; i++) {
				tmp[i] = tmp[i].replace('\\', '/');
				WebdavFile f3 = new WebdavFile(this.getURL(dir + tmp[i]));

				if (f3.isDirectory()) {
					if (!tmp[i].endsWith("/")) {
						tmp[i] = tmp[i] + "/";
					}

					this.transferDir(dir + tmp[i], out + tmp[i]);
				} else {
					this.fireProgressUpdate(baseFile, DataConnection.GETDIR + ":" + fileCount, -1);
					this.work(dir + tmp[i], out + tmp[i]);
				}
			}

			this.fireProgressUpdate(baseFile, DataConnection.DFINISHED + ":" + fileCount, -1);
			shortProgress = false;
		} catch (Exception ex) {
			net.sf.jftp.system.logging.Log.debug("Error: " + ex);
			ex.printStackTrace();
		}
	}

	private HttpURL getURL(String u) {
		try {
			HttpURL url = new HttpURL(u);

			url.setUserinfo(user, pass);

			return url;
		} catch (Exception ex) {
			ex.printStackTrace();
			net.sf.jftp.system.logging.Log.debug("ERROR: " + ex);

			return null;
		}
	}

	private WebdavResource getResource(String res) throws IOException {
		return new WebdavResource(this.getURL(res));
	}

	private void transfer(String file) {
		this.transfer(file, false);
	}

	private void transfer(String file, boolean up) {
		String out = net.sf.jftp.system.StringUtils.getDir(file);

		if (net.sf.jftp.system.StringUtils.isRelative(file)) {
			file = pwd + file;
		}

		file = file.replace('\\', '/');
		out = out.replace('\\', '/');

		String outfile = net.sf.jftp.system.StringUtils.getFile(file);

		if (file.endsWith("/")) {
			if (up) {
				net.sf.jftp.system.logging.Log.debug("Directory upload not implemented yet.");

				return;
			}

			this.transferDir(file, path + out);

		} else {
			if (up) {
				this.work(path + outfile, file);
			} else {
				this.work(file, path + outfile);
			}
		}
	}

	private void work(String file, String outfile) {
		net.sf.jftp.system.logging.Log.out("transfer started\nfile: " + file + "\noutfile: " + outfile);

		BufferedInputStream in = null;
		BufferedOutputStream out = null;

		try {
			if (outfile.startsWith("http://")) {
				String resPath = outfile.substring(0, outfile.lastIndexOf("/") + 1);
				String name = outfile.substring(outfile.lastIndexOf("/") + 1);

				net.sf.jftp.system.logging.Log.debug("Uploading " + file + " to " + resPath + " as " + name);
WebdavResource res = this.getResource(resPath);

				if (res.putMethod(new File(file))) {
					this.fireProgressUpdate(file, DataConnection.FINISHED, -1);
				} else {
					net.sf.jftp.system.logging.Log.debug("Upload failed.");
					this.fireProgressUpdate(file, DataConnection.FAILED, -1);
				}

				return;
			}

			net.sf.jftp.system.logging.Log.debug("Downloading " + file + " to " + outfile);

			out = new BufferedOutputStream(new FileOutputStream(outfile));
			in = new BufferedInputStream(this.getResource(file).getMethodData());
byte[] buf = new byte[webdavBuffer];
			int len = 0;
			int reallen = 0;
while (true) {
				len = in.read(buf);
if (len == StreamTokenizer.TT_EOF) {
					break;
				}

				out.write(buf, 0, len);

				reallen += len;
	this.fireProgressUpdate(net.sf.jftp.system.StringUtils.getFile(file), DataConnection.GET, reallen);
			}

			this.fireProgressUpdate(file, DataConnection.FINISHED, -1);
		} catch (IOException ex) {
			net.sf.jftp.system.logging.Log.debug("Error with file IO (" + ex + ")!");
			ex.printStackTrace();
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

	public int upload(String file, InputStream in) {

		net.sf.jftp.system.logging.Log.debug("Upload using InputStream is not implemented yet!");

		return -1;
	}
	public InputStream getDownloadInputStream(String file) {
		if (net.sf.jftp.system.StringUtils.isRelative(file)) {
			file = pwd + file;
		}

		file = file.replace('\\', '/');

		try {
			return this.getResource(file).getMethodData();
		} catch (Exception ex) {
			ex.printStackTrace();
			net.sf.jftp.system.logging.Log.debug(ex + " @WebdavConnection::getDownloadInputStream");

			return null;
		}
	}

	public void addConnectionListener(ConnectionListener l) {
		listeners.add(l);
	}

	public void setConnectionListeners(Vector l) {
		listeners = l;
	}
	public void fireDirectoryUpdate() {
		if (listeners == null) {
		} else {
			for (int i = 0; i < listeners.size(); i++) {
				((ConnectionListener) listeners.elementAt(i)).updateRemoteDirectory(this);
			}
		}
	}

	public boolean login(String user, String pass) {
		return true;
	}

	public void fireProgressUpdate(String file, String type, int bytes) {
		if (listeners == null) {
		} else {
			for (int i = 0; i < listeners.size(); i++) {
				ConnectionListener listener = (ConnectionListener) listeners.elementAt(i);

				if (shortProgress && net.sf.jftp.config.Settings.shortProgress) {
					if (type.startsWith(DataConnection.DFINISHED)) {
						listener.updateProgress(baseFile, DataConnection.DFINISHED + ":" + fileCount, bytes);
					}

					listener.updateProgress(baseFile, DataConnection.GETDIR + ":" + fileCount, bytes);
				} else {
					listener.updateProgress(file, type, bytes);
				}
			}
		}
	}

	public Date[] sortDates() {
		return null;
	}

	public boolean rename(String from, String to) {
		net.sf.jftp.system.logging.Log.debug("Not implemented!");

		return false;
	}
}
