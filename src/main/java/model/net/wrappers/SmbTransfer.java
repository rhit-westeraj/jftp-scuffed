package model.net.wrappers;

import model.net.ConnectionListener;
import model.net.Transfer;

import java.util.List;


class SmbTransfer implements Runnable {
	private final String url;
	private final String domain;
	private final String localPath;
	private final String file;
	private final String user;
	private final String pass;
	private final String type;
	private final List<ConnectionListener> listeners;
	private Thread runner;
	private SmbConnection con;

	public SmbTransfer(String url, String localPath, String file, String user, String pass, String domain, List<ConnectionListener> listeners, String type) {
		super();
		this.url = url;
		this.localPath = localPath;
		this.file = file;
		this.user = user;
		this.pass = pass;
		this.type = type;
		this.domain = domain;
		this.listeners = listeners;

		this.prepare();
	}

	private void prepare() {
		this.runner = new Thread(this);
		this.runner.setPriority(Thread.MIN_PRIORITY);
		this.runner.start();
	}

	public void run() {
		this.con = new SmbConnection(this.url, this.domain, this.user, this.pass, null);
		this.con.setLocalPath(this.localPath);
		this.con.setConnectionListeners(this.listeners);

		if (this.type.equals(Transfer.DOWNLOAD)) {
			this.con.download(this.file);
		} else if (this.type.equals(Transfer.UPLOAD)) {
			this.con.upload(this.file);
		}
	}

	public SmbConnection getSmbConnection() {
		return this.con;
	}
}
