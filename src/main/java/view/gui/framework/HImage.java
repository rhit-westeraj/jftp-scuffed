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
package view.gui.framework;

import model.system.logging.Log;
import controller.util.I18nHelper;

import javax.swing.*;
import java.awt.Component;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;


public enum HImage {
	;

	public static synchronized Image getImage(Component c, String name) {
		Image img = null;

		try {
			java.net.URL url = ClassLoader.getSystemResource(name);

			if (null == url) {
				url = HImage.class.getResource("/" + name);
			}

			//System.out.println(name + ":" + url);
			// this is used in case the image not found, and we are packaged as a jar.
			if (null == url) {
				url = HImage.class.getResource("/" + name.replace('\\', '/'));
			}

			img = (null != url) ? Toolkit.getDefaultToolkit().getImage(url) : null;

			//Image img = Toolkit.getDefaultToolkit().getImage(name);
			MediaTracker mt = new MediaTracker(c);
			mt.addImage(img, 1);
			mt.waitForAll();
		} catch (Exception ex) {
			Log.debug(I18nHelper.getLogString("error.fetching.image"));
			ex.printStackTrace();

			return img;
		}

		return img;
	}

	public static synchronized ImageIcon getImageIcon(String name, String desc) {
		java.net.URL url = ClassLoader.getSystemResource(name);

		if (null == url) {
			url = HImage.class.getResource("/" + name);
		}
		return (null != url) ? new javax.swing.ImageIcon(url) : null;
	}
}
