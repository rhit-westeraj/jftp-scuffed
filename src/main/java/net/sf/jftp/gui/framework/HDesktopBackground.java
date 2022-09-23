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
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.ImageObserver;


public class HDesktopBackground extends JPanel implements MouseListener, ImageObserver {
	private final Image img;
	private final String cmd = "default";
	public ActionListener who = null;
	private String image = null;

	public HDesktopBackground(String image, ActionListener who) {
		this.image = image;
		this.who = who;

		img = HImage.getImage(this, image);
		addMouseListener(this);
		setVisible(true);
	}

	public void paintComponent(Graphics g) {
		if (!net.sf.jftp.config.Settings.getUseBackground()) {
			return;
		}

		int x = img.getWidth(this);
		int y = img.getHeight(this);
		int w = 2000 / x;
		int h = 2000 / y;

		for (int i = 0; i < w; i++) {
			for (int j = 0; j < h; j++) {
				g.drawImage(img, i * x, j * y, this);
			}
		}
	}

	public void update(Graphics g) {
		paintComponent(g);
	}

	public void mouseClicked(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {
	}

	public void mouseReleased(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}

	public boolean imageUpdate(Image image, int infoflags, int x, int y, int width, int height) {
		//if(width > 0 && height >0) repaint();
		return true;
	}
}