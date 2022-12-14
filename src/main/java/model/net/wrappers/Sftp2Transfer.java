package model.net.wrappers;

import model.net.ConnectionListener;
import model.net.Transfer;

import java.util.List;

class Sftp2Transfer implements Runnable {
	private final String host;
	private final String localPath;
	private final String remotePath;
	private final String file;
	private final String user;
	private final String pass;
	private final String type;
	private final List<ConnectionListener> listeners;
	private final String keyfile;
	private final String port;
	private Thread runner;
	private Sftp2Connection con;

	public Sftp2Transfer(String localPath, String remotePath, String file, String user, String pass, java.util.List<ConnectionListener> listeners, String type, String keyfile, String host, String port) {
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

	private void prepare() {
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
