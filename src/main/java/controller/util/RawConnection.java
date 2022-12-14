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
package controller.util;

import view.JFtp;
import view.gui.framework.HTextField;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;


public class RawConnection extends JFrame implements ActionListener, WindowListener {
	public static final HTextField host = new HTextField("Host:", "", 20);
	public static final HTextField port = new HTextField(I18nHelper.getUIString("port2"), "", 5);
	public static final JTextArea output = new JTextArea();
	public static boolean established;
	public static boolean mayDispose;
	public static JScrollPane outputPane;
	private final JMenuBar mb = new JMenuBar();
	private final JMenu file = new JMenu(I18nHelper.getUIString("prog"));
	private final JMenu about = new JMenu(I18nHelper.getUIString("about"));
	private final JMenu session = new JMenu(I18nHelper.getUIString("session"));
	private final JMenuItem close = new JMenuItem("ExIt");
	private final JMenuItem changeHost = new JMenuItem(I18nHelper.getUIString("host1"));
	private final JMenuItem info = new JMenuItem(I18nHelper.getUIString("info2"));
	private final HTextField com = new HTextField(I18nHelper.getUIString("command1"), "", 20);
	private final JButton send = new JButton(I18nHelper.getUIString("send1"));
	private final JButton clear = new JButton(I18nHelper.getUIString("clear"));
	private JRawConnection c;

	public RawConnection() {
		this("localhost", 25);
	}

	private RawConnection(String hostname, int p) {
		super();
		host.setText(hostname);

		this.setSize(550, 300);
		this.setLocation(150, 150);
		this.setTitle(I18nHelper.getUIString("direct.tcp.ip.connection"));
		this.getContentPane().setLayout(new BorderLayout(2, 2));

		javax.swing.JPanel p1 = new javax.swing.JPanel();
		p1.add(host);
		p1.add(port);
		host.text.setEditable(false);
		port.text.setEditable(false);
		port.setText(Integer.toString(p));

		this.com.text.addActionListener(this);

		javax.swing.JPanel p2 = new javax.swing.JPanel();
		p2.add(this.com);

		this.com.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				if (java.awt.event.KeyEvent.VK_ENTER == e.getKeyCode()) {
					RawConnection.this.transmit();
				}
			}
		});

		p2.add(this.send);
		this.send.addActionListener(this);
		p2.add(this.clear);
		this.clear.addActionListener(this);

		output.setEditable(false);

		outputPane = new JScrollPane(output);
		outputPane.setMinimumSize(new Dimension(400, 300));

		this.getContentPane().add("North", p1);
		this.getContentPane().add(JFtp.CENTER, outputPane);
		this.getContentPane().add(JFtp.SOUTH, p2);

		this.com.setText("");

		this.file.add(this.close);
		this.close.addActionListener(this);
		this.session.add(this.changeHost);
		this.changeHost.addActionListener(this);
		this.about.add(this.info);
		this.info.addActionListener(this);

		this.session.add(this.close);
		this.mb.add(this.session);

		this.setJMenuBar(this.mb);

		this.addWindowListener(this);
		this.setVisible(true);

		JHostChooser jhc = new JHostChooser();
	}

	private void transmit() {
		if (established) {
			if (this.c.isThere()) {
				this.c.send(this.com.getText());
			} else {
				this.debugWrite(I18nHelper.getLogString("no.connection"));
			}
		} else {
			this.c = new JRawConnection(host.getText(), Integer.parseInt(port.getText()), true);

			if (this.c.isThere()) {
				this.c.send(this.com.getText());
				established = true;
			} else {
				this.debugWrite(I18nHelper.getLogString("no.connection"));
			}
		}

		this.com.setText("");
	}

	public void actionPerformed(ActionEvent e) {
		if ((e.getSource() == this.send) || (e.getSource() == this.com.text)) {
			this.transmit();
		}

		if (e.getSource() == this.clear) {
			output.setText("");
		}

		if (e.getSource() == this.close) {
			this.dispose();
		}

		if (e.getSource() == this.changeHost) {
			JHostChooser jhc = new JHostChooser();
		}
	}

	private void debugWrite(String str) {
		output.append(str + "\n");
	}

	public void windowClosing(WindowEvent e) {
		if (mayDispose) {
			this.dispose();
		}
	}

	public void windowIconified(WindowEvent e) {
	}

	public void windowDeiconified(WindowEvent e) {
	}

	public void windowClosed(WindowEvent e) {
	}

	public void windowActivated(WindowEvent e) {
	}

	public void windowDeactivated(WindowEvent e) {
	}

	public void windowOpened(WindowEvent e) {
	}
}
