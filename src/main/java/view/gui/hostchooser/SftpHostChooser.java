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

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package view.gui.hostchooser;

import net.miginfocom.swing.MigLayout;
import view.JFtp;
import controller.config.LoadSet;
import controller.config.SaveSet;
import controller.config.Settings;
import view.gui.framework.HButton;
import view.gui.framework.HFrame;
import view.gui.framework.HInsetPanel;
import view.gui.framework.HPanel;
import view.gui.framework.HPasswordField;
import view.gui.framework.HTextField;
import model.net.wrappers.Sftp2Connection;
import model.net.wrappers.Sftp2URLConnection;
import model.system.logging.Log;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;

public class SftpHostChooser extends HFrame implements ActionListener, WindowListener, ChangeListener {
	private final HTextField host = new HTextField("Host:", "localhost");
	private final HTextField user = new HTextField("Username:", "guest");
	private final HTextField port = new HTextField("Port:", "22");
	private final HPasswordField pass = new HPasswordField("Password/Phrase:", "nopasswd");
	private final JComboBox enc = new JComboBox();
	private final JComboBox cs = new JComboBox();
	private final JComboBox keys = new JComboBox();
	private final JLabel encL = new JLabel("Preferred Encryption");
	private final JLabel csL = new JLabel("Preferred Message Authentication");
	private final JLabel keysL = new JLabel("Preferred Public Key");
	private final JLabel keyfileL = new JLabel("None, maybe look in ~/.ssh");
	private final HButton ok = new HButton("Connect");
	private final HButton keyfile = new HButton("Choose Key File");
	private ComponentListener listener;
	private boolean useLocal;
	private String keyfileName;

	public SftpHostChooser(ComponentListener l, boolean local) {
		super();
		this.listener = l;
		this.useLocal = local;
		this.init();
	}

	public SftpHostChooser(ComponentListener l) {
		super();
		this.listener = l;
		this.init();
	}

	public SftpHostChooser() {
		super();
		this.init();
	}

	private void init() {
		this.setLocation(100, 150);
		this.setTitle("Sftp Connection...");
		this.setBackground(new HPanel().getBackground());

		try {
			File f = new File(Settings.appHomeDir);
			f.mkdir();

			File f1 = new File(Settings.login);
			f1.createNewFile();

			File f2 = new File(Settings.login_def_sftp);
			f2.createNewFile();
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		String[] login = LoadSet.loadSet(Settings.login_def_sftp);

		if ((null != login[0]) && (1 < login.length)) {
			this.host.setText(login[0]);
			this.user.setText(login[1]);

			if (3 < login.length) {
				this.port.setText(login[3]);
			}
		}

		if (Settings.getStorePasswords()) {
			if ((null != login) && (2 < login.length) && (null != login[2])) {
				this.pass.setText(login[2]);
			}
		} else {
			this.pass.setText("");
		}


		this.enc.addItem("3des-cbc");
		this.enc.addItem("blowfish-cbc");

		this.cs.addItem("hmac-sha1");
		this.cs.addItem("hmac-sha1-96");
		this.cs.addItem("hmac-md5");
		this.cs.addItem("hmac-md5-96");

		this.keys.addItem("ssh-rsa");
		this.keys.addItem("ssh-dss");

		HInsetPanel root = new HInsetPanel();
		root.setLayout(new MigLayout());
		root.add(this.host);
		root.add(this.port, "wrap");
		root.add(this.user);
		root.add(this.pass, "wrap");

		root.add(new JLabel(" "), "wrap");

		root.add(this.encL);
		root.add(this.enc, "wrap");
		root.add(this.csL);
		root.add(this.cs, "wrap");
		root.add(this.keysL);
		root.add(this.keys);

		root.add(new JLabel(" "), "wrap");

		root.add(this.keyfileL);
		root.add(this.keyfile, "wrap");
		this.keyfileL.setForeground(Color.DARK_GRAY);
		this.keyfileL.setFont(new Font("serif", Font.ITALIC, 11));

		root.add(new JLabel(" "), "wrap");

		root.add(new JLabel(" "));
		root.add(this.ok, "align right");

		this.getContentPane().setLayout(new BorderLayout(10, 10));
		this.getContentPane().add(JFtp.CENTER, root);

		this.ok.addActionListener(this);
		this.keyfile.addActionListener(this);
		this.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
		this.pass.text.addActionListener(this);

		this.pack();
		this.setModal(false);
		this.setVisible(false);
		this.addWindowListener(this);
	}

	public void stateChanged(ChangeEvent e) {
		this.enc.setEnabled(false);
		this.cs.setEnabled(false);
		this.keys.setEnabled(false);
	}

	public void update() {
		this.fixLocation();
		this.setVisible(true);
		this.toFront();
		this.host.requestFocus();
	}

	public void update(String url) {
		try {
			url = url.replace("sftp://", "ftp://"); //sftp is not a valid protocol here, but we still get the parser to work

			Sftp2URLConnection uc = new Sftp2URLConnection(new java.net.URL(url));
			Sftp2Connection con = uc.getSftp2Connection();
			JFtp.statusP.jftp.addConnection(url, con);

			uc.connect();

			if (uc.loginSucceeded()) {
				this.dispose();

				if (null != this.listener) {
					this.listener.componentResized(new ComponentEvent(this, 0));
				}

				JFtp.mainFrame.setVisible(true);
				JFtp.mainFrame.toFront();
			} else {
				this.setTitle("Wrong password!");
				this.host.setText(uc.getHost());
				this.port.setText(Integer.toString(uc.getPort()));
				this.user.setText(uc.getUser());
				this.pass.setText(uc.getPass());
				this.setVisible(true);
				this.toFront();
				this.host.requestFocus();
			}
		} catch (IOException ex) {
			Log.debug("Error!");
			ex.printStackTrace();
		}
	}

	public void actionPerformed(ActionEvent e) {
		if ((e.getSource() == this.ok) || (e.getSource() == this.pass.text)) {
			this.setCursor(new Cursor(Cursor.WAIT_CURSOR));

			String htmp = this.host.getText().trim();
			String utmp = this.user.getText().trim();
			String ptmp = this.pass.getText();

			int potmp = 22;

			try {
				potmp = Integer.parseInt(this.port.getText());
			} catch (Exception ex) {
				Log.debug("Error: Not a number!");
			}

			try {
				boolean status;

				SaveSet s = new SaveSet(Settings.login_def_sftp, htmp, utmp, ptmp, String.valueOf(potmp), "null", "null");

				Sftp2Connection con2 = new Sftp2Connection(htmp, String.valueOf(potmp), this.keyfileName);

				if (con2.login(utmp, ptmp)) {
					if (this.useLocal) {
						JFtp.statusP.jftp.addLocalConnection(htmp, con2);
					} else {
						JFtp.statusP.jftp.addConnection(htmp, con2);
					}

					if (con2.chdir(con2.getPWD()) || con2.chdir("/")) {
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				Log.debug("Could not create SftpConnection, does this distribution come with j2ssh?");
			}


			this.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
			this.dispose();
			JFtp.mainFrame.setVisible(true);
			JFtp.mainFrame.toFront();

			if (null != this.listener) {
				this.listener.componentResized(new ComponentEvent(this, 0));
			}
		} else if (e.getSource() == this.keyfile) {
			JFileChooser chooser = new JFileChooser();
			int returnVal = chooser.showOpenDialog(this);

			if (javax.swing.JFileChooser.APPROVE_OPTION == returnVal) {
				this.keyfileName = chooser.getSelectedFile().getPath();

				if (null != this.keyfileName) {
					this.keyfileL.setText("(File present)");
				}
			} else {
				this.keyfileName = null;

				if (null != this.keyfileName) {
					this.keyfileL.setText("(No File)");
				}
			}
		}
	}

	public void windowClosing(WindowEvent e) {
		this.dispose();
	}

	public void windowClosed(WindowEvent e) {
	}

	public void windowActivated(WindowEvent e) {
	}

	public void windowDeactivated(WindowEvent e) {
	}

	public void windowIconified(WindowEvent e) {
	}

	public void windowDeiconified(WindowEvent e) {
	}

	public void windowOpened(WindowEvent e) {
	}

	public void pause(int time) {
		try {
			Thread.sleep(time);
		} catch (Exception ex) {
		}
	}
}
