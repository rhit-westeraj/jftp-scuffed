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
package net.sf.jftp.gui.tasks;

import net.sf.jftp.gui.framework.HButton;
import net.sf.jftp.gui.framework.HPanel;
import net.sf.jftp.gui.framework.HTextField;
import net.sf.jftp.net.wrappers.HttpTransfer;
import net.sf.jftp.util.I18nHelper;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;


public class HttpDownloader extends HPanel implements ActionListener {
	private final HTextField text;
	private final HButton ok = new HButton(I18nHelper.getUIString("start"));

	public HttpDownloader() {
		super();
		//setSize(480,100);
		//setTitle("Download a file via an URL");
		//setLocation(150,150);
		//getContentPane().
		this.setLayout(new FlowLayout());

		this.text = new HTextField(I18nHelper.getUIString("url"), "http://", 25);

		//getContentPane().
		this.add(this.text);

		//getContentPane().
		this.add(this.ok);
		this.ok.addActionListener(this);
		this.text.text.addActionListener(this);

		this.setVisible(true);
	}

	public void actionPerformed(ActionEvent e) {
		if ((e.getSource() == this.ok) || (e.getSource() == this.text.text)) {
			Vector listeners = new Vector();
			listeners.add(net.sf.jftp.JFtp.localDir);

			HttpTransfer t = new HttpTransfer(this.text.getText().trim(), net.sf.jftp.JFtp.localDir.getPath(), listeners, net.sf.jftp.JFtp.getConnectionHandler());

			net.sf.jftp.JFtp.statusP.jftp.removeFromDesktop(this.hashCode());

			//hide();
		}
	}
}
