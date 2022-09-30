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
package net.sf.jftp.gui.framework;

import javax.swing.*;
import java.awt.*;


public class HComboBox extends JPanel //implements ActionListener
{
	private final JLabel label;
	public final JComboBox comboBox;

	public HComboBox(final String l) {
		super();
		this.setLayout(new BorderLayout(5, 5));

		this.label = new JLabel(l + "  ");
		this.add("West", this.label);

		//comboBox = new JComboBox(t, 12);
		this.comboBox = new JComboBox();
		this.add("East", this.comboBox);

		this.setVisible(true);
	}

	public HComboBox(final String l, final String[] t) {
		super();
		this.setLayout(new BorderLayout(5, 5));

		this.label = new JLabel(l + "  ");
		this.add("West", this.label);

		//comboBox = new JComboBox(t, 12);
		this.comboBox = new JComboBox(t);
		this.add("East", this.comboBox);

		this.setVisible(true);
	}

	public String getLabel() {
		return this.label.getText();
	}

	public void setLabel(final String l) {
		this.label.setText(l + "  ");
	}

	public Object getSelectedItem() {
		return this.comboBox.getSelectedItem();
	}

	public void addItem(final Object obj) {
		this.comboBox.addItem(obj);
	}

	public void addActionListener(final java.awt.event.ActionListener actListen) {
		this.comboBox.addActionListener(actListen);
	}

	public void setEditable(final boolean yesno) {
		this.comboBox.setEditable(yesno);
	}
}
