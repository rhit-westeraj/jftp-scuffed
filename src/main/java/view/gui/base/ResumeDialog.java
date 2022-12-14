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
package view.gui.base;

import view.JFtp;
import controller.config.Settings;
import view.gui.base.dir.DirEntry;
import view.gui.framework.HFrame;
import view.gui.framework.HPanel;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;


class ResumeDialog extends HFrame implements ActionListener {
	private final JButton resume = new JButton("Resume");
	private final JButton skip = new JButton("Skip");
	private final JButton over = new JButton("Overwrite");
	private DirEntry dirEntry;

	public ResumeDialog(DirEntry dirEntry) {
		super();
		this.dirEntry = dirEntry;

		this.setLocation(150, 150);
		this.setTitle("Question");

		this.resume.setEnabled(false);

		JTextArea text = new JTextArea();
		text.append("A file named " + dirEntry.file + " already exists.                       \n\n");

		File f = new File(JFtp.localDir.getPath() + dirEntry.file);
		long diff = 0;

		diff = dirEntry.getRawSize() - f.length();

		if (0 == diff) {
			text.append("It has exactly the same size as the remote file.\n\n");
		} else if (0 > diff) {
			text.append("It is bigger than the remote file.\n\n");
		} else {
			text.append("It is smaller than the remote file.\n\n");
			this.resume.setEnabled(true);
		}

		this.getContentPane().setLayout(new BorderLayout(5, 5));
		this.getContentPane().add(JFtp.CENTER, text);

		HPanel p = new HPanel();
		p.add(this.resume);
		p.add(this.skip);
		p.add(this.over);

		this.getContentPane().add(JFtp.SOUTH, p);

		this.resume.addActionListener(this);
		this.skip.addActionListener(this);
		this.over.addActionListener(this);

		this.pack();
		this.fixLocation();
		this.setVisible(true);
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == this.resume) {
			this.dispose();
			this.transfer();
		} else if (e.getSource() == this.skip) {
			this.dispose();
		} else if (e.getSource() == this.over) {
			this.dispose();

			File f = new File(JFtp.localDir.getPath() + this.dirEntry.file);
			f.delete();

			this.transfer();
		}
	}

	private void transfer() {
		if ((Settings.smallSize > this.dirEntry.getRawSize()) && !this.dirEntry.isDirectory()) {
			JFtp.remoteDir.getCon().download(this.dirEntry.file);
		} else {
			JFtp.remoteDir.getCon().handleDownload(this.dirEntry.file);
		}
	}
}
