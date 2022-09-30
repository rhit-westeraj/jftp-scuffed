package net.sf.jftp.net.wrappers;

import net.sf.jftp.net.Transfer;

import java.util.Vector;

public class Sftp2Transfer implements Runnable {
	private final String host;
	private final String localPath;
	private final String remotePath;
	private final String file;
	private final String user;
	private final String pass;
	private final String type;
	private final Vector listeners;
	private final String keyfile;
	private final String port;
	public Thread runner;
	private Sftp2Connection con = null;

	public Sftp2Transfer(final String localPath, final String remotePath, final String file, final String user, final String pass, final Vector listeners, final String type, final String keyfile, final String host, final String port) {
		super();
		this.localPath = localPath;
		this.remotePath = remotePath;
		this.file = file;
		this.user = user;
		this.pass = pass;
		this.type = type;
		this.listeners = listeners;
		this.keyfile = keyfile;
		this.host = host;
		this.port = port;

		this.prepare();
	}

	public void prepare() {
		this.runner = new Thread(this);
		this.runner.setPriority(Thread.MIN_PRIORITY);
		this.runner.start();
	}

	public void run() {
		this.con = new Sftp2Connection(this.host, this.port, this.keyfile);
		this.con.setConnectionListeners(this.listeners);
		this.con.login(this.user, this.pass);
		this.con.setLocalPath(this.localPath);
		this.con.chdir(this.remotePath);

		if (this.type.equals(Transfer.DOWNLOAD)) {
			this.con.download(this.file);
		} else if (this.type.equals(Transfer.UPLOAD)) {
			this.con.upload(this.file);
		}
	}

	public Sftp2Connection getSftpConnection() {
		return this.con;
	}
}
