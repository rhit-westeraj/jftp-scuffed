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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;


public class Displayer extends JInternalFrame implements ActionListener {
	public static final boolean showCloseButton = false;

	private final JTextArea info = new JTextArea(25, 50) {
		public Insets getInsets() {
			final Insets std = super.getInsets();

			return new Insets(std.top + 5, std.left + 5, std.bottom + 5, std.right + 5);
		}
	};

	private final JButton close = new JButton("Close");

	public Displayer(final java.net.URL file, final Font font) {
		super(file.getFile(), true, true, true, true);
		this.setLocation(50, 50);
		this.setSize(600, 540);
		this.getContentPane().setLayout(new BorderLayout());

		this.load(file);
		if (font != null) {
			this.info.setFont(font);
		} else {
			this.info.setFont(new Font("monospaced", Font.PLAIN, 11));
		}
		this.info.setEditable(false);

		final JScrollPane jsp = new JScrollPane(this.info);
		this.getContentPane().add("Center", jsp);

		final net.sf.jftp.gui.framework.HPanel closeP = new net.sf.jftp.gui.framework.HPanel();
		closeP.setLayout(new FlowLayout(FlowLayout.CENTER));
		closeP.add(this.close);

		this.close.addActionListener(this);

		if (net.sf.jftp.gui.tasks.Displayer.showCloseButton) {
			this.getContentPane().add("South", closeP);
		}

		this.info.setCaretPosition(0);

		this.setVisible(true);
	}

	public void actionPerformed(final ActionEvent e) {
		if (e.getSource() == this.close) {
			this.dispose();
		}
	}

	private void load(final java.net.URL file) {
		String data = "";
		final StringBuilder now = new StringBuilder();

		try {
			final DataInput in = new DataInputStream(new BufferedInputStream(file.openStream()));

			while ((data = in.readLine()) != null) {
				now.append(data).append("\n");
			}
		} catch (final IOException e) {
			net.sf.jftp.system.logging.Log.debug(e + " @Displayer.load()");
		}

		this.info.setText(now.toString());
	}

	public Insets getInsets() {
		final Insets std = super.getInsets();

		return new Insets(std.top + 5, std.left + 5, std.bottom + 5, std.right + 5);
	}
}
