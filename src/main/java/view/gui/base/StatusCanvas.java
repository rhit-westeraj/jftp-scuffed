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

import controller.config.Settings;
import view.gui.framework.GUIDefaults;
import model.system.LocalIO;

import javax.swing.*;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;


public class StatusCanvas extends JPanel {
	private final JLabel host = new JLabel("");
	private final JLabel text = new JLabel(Settings.greeting);
	JLabel separator = new JLabel("   ");
	private String drawText = "";
	private int pos;
	private Image image;
	private Graphics offg;
	private boolean slide;
	private int interval = 3;
	private boolean fwd;

	public StatusCanvas() {
		super();
		this.setMinimumSize(new Dimension(200, 25));
		this.setPreferredSize(new Dimension(320, 25));
		this.setLayout(null);
		this.setVisible(true);
	}

	public void setInterval(int x) {
		this.interval = x;
	}

	public void forward() {
		this.fwd = true;
	}

	public void setText(String msg) {
		if (false) {
			this.text.setText(msg);
		} else {
			this.text.setText("");
			this.drawText = msg;
			this.slide = false;

			if (AppMenuBar.fadeMenu.getState()) {
				SwingUtilities.invokeLater(() -> {
					for (this.pos = 30; 0 < this.pos; this.pos -= 1) {
						this.paintImmediately(0, 0, this.getSize().width, this.getSize().height);
						LocalIO.pause(this.interval);
					}
				});
			} else {
				this.repaint();
			}
		}

		this.validate();
	}

	public void scrollText(String msg) {
		if (false) {
			this.text.setText(msg);
		} else {
			this.text.setText("");
			this.drawText = msg;
			this.slide = true;

			if (AppMenuBar.fadeMenu.getState()) {
				for (this.pos = 700; this.pos > (msg.length() * -6); this.pos -= 1) {
					if (this.fwd) {
						break;
					}
					this.repaint();
					LocalIO.pause(this.interval);

				}
			} else {
				this.repaint();
			}

			this.fwd = false;
		}

		this.validate();
	}

	public String getHost() {
		return this.host.getText();
	}

	public void setHost(String h) {
		this.host.setText(h);
		this.validate();
	}

	public void fresh() {
		this.image = null;
		this.offg = null;

		this.repaint();
	}

	public void paintComponent(Graphics g) {
		if (null == this.image) {
			this.image = this.createImage(this.getWidth(), this.getHeight());
		}

		if (null == this.offg) {
			this.offg = this.image.getGraphics();
		}

		this.offg.setColor(GUIDefaults.light);
		this.offg.setFont(GUIDefaults.status);
		this.offg.fillRect(0, 0, this.getSize().width, this.getSize().height);
		this.offg.setColor(new Color(125, 125, 175));

		if (this.slide) {
			this.offg.drawString(this.drawText, 10 + this.pos, 15);
		} else {
			this.offg.drawString(this.drawText, 10, 15 + this.pos);
		}

		this.offg.setColor(GUIDefaults.front);
		this.offg.drawRect(0, 0, this.getSize().width - 1, this.getSize().height - 1);
		this.offg.drawRect(0, 0, this.getSize().width - 2, this.getSize().height - 2);
		g.drawImage(this.image, 0, 0, this);
	}

	public void update(Graphics g) {
		this.paintComponent(g);
	}
}
