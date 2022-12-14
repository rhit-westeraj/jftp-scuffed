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

import view.JFtp;
import controller.config.LoadSet;
import controller.config.Settings;
import view.gui.framework.HButton;
import view.gui.framework.HFrame;
import view.gui.framework.HImage;
import view.gui.framework.HInsetPanel;
import view.gui.framework.HPanel;
import view.gui.framework.HPasswordField;
import view.gui.framework.HTextField;
import view.gui.tasks.ExternalDisplayer;
import model.net.wrappers.NfsConnection;
import model.net.wrappers.StartConnection;
import model.system.logging.Log;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;


public class NfsHostChooser extends HFrame implements ActionListener, WindowListener {
	private static final HTextField host = new HTextField("URL:", "nfs://localhost:v2m/tmp", 20);
	private static final HTextField user = new HTextField("Username:", "<anonymous>", 15);

	//public static HTextField pass = new HTextField("Password:","none@nowhere.no");
	private static final HPasswordField pass = new HPasswordField("Password:", "nopasswd");
	private static final HButton info = new HButton("Read me!");
	private final HPanel okP = new HPanel();
	private final HButton ok = new HButton("Connect");
	private ComponentListener listener;
	private boolean useLocal;

	public NfsHostChooser(ComponentListener l, boolean local) {
		super();
		this.listener = l;
		this.useLocal = local;
		this.init();
	}

	public NfsHostChooser(ComponentListener l) {
		super();
		this.listener = l;
		this.init();
	}

	public NfsHostChooser() {
		super();
		this.init();
	}

	private void init() {
		//setSize(600, 220);
		this.setLocation(100, 150);
		this.setTitle("NFS Connection...");
		this.setBackground(this.okP.getBackground());

		JPanel p = new JPanel();
		p.add(info);

		//*** MY ADDITIONS
		try {
			File f = new File(Settings.appHomeDir);
			f.mkdir();

			File f1 = new File(Settings.login);
			f1.createNewFile();

			File f2 = new File(Settings.login_def_nfs);
			f2.createNewFile();
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		LoadSet l = new LoadSet();
		String[] login = LoadSet.loadSet(Settings.login_def_nfs);

		if ((null != login[0]) && (1 < login.length)) {
			host.setText(login[0]);
			user.setText(login[1]);
		}

        /*
                host.setText("nfs://localhost:v2m/tmp");
                user.setText("guest");

        }
        */
		if (Settings.getStorePasswords()) {
			if ((null != login[0]) && (2 < login.length) && (null != login[2])) {
				pass.setText(login[2]);
			}
		} else {
			pass.setText("");
		}

		HInsetPanel root = new HInsetPanel();
		root.setLayout(new GridLayout(4, 2, 5, 3));

		root.add(host);
		root.add(p);
		root.add(user);
		root.add(pass);

		root.add(new JLabel(""));
		root.add(this.okP);

		this.okP.add(this.ok);

		this.getContentPane().setLayout(new BorderLayout(10, 10));
		this.getContentPane().add(JFtp.CENTER, root);

		this.ok.addActionListener(this);
		info.addActionListener(this);
		this.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
		pass.text.addActionListener(this);

		this.pack();
		this.setModal(false);
		this.setVisible(false);
		this.addWindowListener(this);
	}

	public void update() {
		this.fixLocation();
		this.setVisible(true);
		this.toFront();
		host.requestFocus();
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == info) {
			java.net.URL url = ClassLoader.getSystemResource(Settings.nfsinfo);

			if (null == url) {
				url = HImage.class.getResource("/" + Settings.nfsinfo);
			}

			ExternalDisplayer d = new ExternalDisplayer(url);
		} else if ((e.getSource() == this.ok) || (e.getSource() == pass.text)) {
			// Switch windows
			//this.setVisible(false);
			this.setCursor(new Cursor(Cursor.WAIT_CURSOR));

			NfsConnection con = null;

			String htmp = host.getText().trim();
			String utmp = user.getText().trim();
			String ptmp = pass.getText();

			//*** MY ADDITIONS
			final int potmp = 0; //*** just filler for the port number

			String userName = user.text.getText();

			//***
			try {
				boolean status;
				status = StartConnection.startCon("NFS", htmp, userName, ptmp, potmp, "", this.useLocal);

                /*

                con = new NfsConnection(htmp);
                //JFtp.remoteDir.setCon(con);
                //con.addConnectionListener(((ConnectionListener)JFtp.remoteDir));

                //JFtp.statusP.jftp.addConnection(htmp, con);

                if(!userName.equals("<anonymous>")) ((NfsConnection)con).login(utmp,ptmp);

                if(useLocal)
                {
                 con.setLocalPath("/");
                      JFtp.statusP.jftp.addLocalConnection(htmp, con);
                }
                else JFtp.statusP.jftp.addConnection(htmp, con);

                con.chdir(htmp);

                //con.setLocalPath(JFtp.localDir.getCon().getPWD());
                //con.addConnectionListener((ConnectionListener) JFtp.localDir);



                */
			} catch (Exception ex) {
				Log.debug("Could not create NfsConnection!");
			}

			this.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
			this.dispose();
			JFtp.mainFrame.setVisible(true);
			JFtp.mainFrame.toFront();

			if (null != this.listener) {
				this.listener.componentResized(new ComponentEvent(this, 0));
			}
		}
	}

	public void windowClosing(WindowEvent e) {
		//System.exit(0);
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
